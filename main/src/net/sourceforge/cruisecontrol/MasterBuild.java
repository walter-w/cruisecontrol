/********************************************************************************
 * CruiseControl, a Continuous Integration Toolkit
 * Copyright (c) 2001, ThoughtWorks, Inc.
 * 651 W Washington Ave. Suite 500
 * Chicago, IL 60661 USA
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions
 * are met:
 * 
 *     + Redistributions of source code must retain the above copyright 
 *       notice, this list of conditions and the following disclaimer. 
 *       
 *     + Redistributions in binary form must reproduce the above 
 *       copyright notice, this list of conditions and the following 
 *       disclaimer in the documentation and/or other materials provided 
 *       with the distribution. 
 *       
 *     + Neither the name of ThoughtWorks, Inc., CruiseControl, nor the 
 *       names of its contributors may be used to endorse or promote 
 *       products derived from this software without specific prior 
 *       written permission. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR 
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR 
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR 
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING 
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS 
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ********************************************************************************/
package net.sourceforge.cruisecontrol;

import java.io.*;
import java.text.*;
import java.util.*;
import javax.mail.*;
import org.apache.tools.ant.*;
import org.w3c.dom.*;

/**
 * Class that will run the "Master Build" -- a
 * loop over the build process so that builds can
 * be automatically run.  Extends XmlLogger so
 * this is the only listener that needs to be declared.
 *
 * @author <a href="mailto:alden@thoughtworks.com">alden almagro</a>
 * @author <a href="mailto:pj@thoughtworks.com">Paul Julius</a>
 * @author Robert Watkins
 * @author <a href="mailto:jcyip@thoughtworks.com">Jason Yip</a>
 * @author <a href="mailto:johnny.cass@epiuse.com">Johnny Cass</a>
 * @author <a href="mailto:davidl@iis.com">David Le Strat</a>
 */
public class MasterBuild extends XmlLogger implements BuildListener {

    private static final String BUILDINFO_FILENAME = "buildcycleinfo";
    private static final String DEFAULT_MAP = "emailmap.properties";
    private final String DEFAULT_PROPERTIES_FILENAME = "cruisecontrol.properties";
    private static final String XML_LOGGER_FILE = "log.xml";

    // Needs to be static since new instance used each build
    //label/modificationset/build participants
    private static String  _label;
    private static String  _labelIncrementerClassName;
    // Needs to be static since new instance used each build
    private static String  _lastGoodBuildTime;
    private static String  _lastBuildAttemptTime;
    
    private static boolean _lastBuildSuccessful = true;
    private static boolean _buildNotNecessary;
    
    private static boolean _spamWhileBroken = true;
    
    private static String  _logDir;
    private static String  _logFile;
    private static String  _userList;

    //build iteration info
    private static long _buildInterval;
    private static boolean _isIntervalAbsolute;
    private int _cleanBuildEvery;

    //build properties
    private Properties _properties;
    private String _propsFileName;

    //xml merge stuff
    private static Vector _auxLogFiles = new Vector();
    private static Vector _auxLogProperties = new Vector();
    private static String _today;

    //email stuff
    private String _defaultEmailSuffix;
    private String _mailhost;
    private String _returnAddress;
    private Set _buildmaster;
    private Set _notifyOnFailure;
    private boolean _reportSuccess;
    private static String _projectName;
    private boolean _useEmailMap;
    private String _emailmapFilename;

    //ant specific stuff
    private String _antFile;
    private String _antTarget;
    private String _cleanAntTarget;
    
    //(PENDING) Extract class to handle logging
    // _debug and _verbose are static because a new instance is used to do logging
    private static boolean _debug;
    private static boolean _verbose;
    
    private boolean _mapSourceControlUsersToEmail;
    
    //build servlet info
    private String _servletURL;
    private static File _currentBuildStatusFile;

    /**
     * Entry point.  Verifies that all command line arguments are correctly 
     * specified.
     */
    public static void main(String[] args) {
        MasterBuild mb = new MasterBuild();
        mb.log("***** Starting automated build process *****\n");

        mb.readBuildInfo();
        mb.overwriteWithUserArguments(args);
        mb.setDefaultPropsFileIfNecessary();

        if (mb.buildInfoSpecified()) {
            mb.execute();
        } else {
            mb.usage();
        }
    }

    // (PENDING) Extract class
    ////////////////////////////
    // BEGIN Handling arguments
    ////////////////////////////
    /**
     * Deserialize the label and timestamp of the last good build.
     */
    public void readBuildInfo() {
        File infoFile = new File(BUILDINFO_FILENAME);
        log("Reading build information from : " + infoFile.getAbsolutePath());
        if (!infoFile.exists() || !infoFile.canRead()) {
            log("Cannot read build information.");
            return;
        }

        try {
            ObjectInputStream s = new ObjectInputStream(new FileInputStream(infoFile));
            //(PENDING) just pass back a BuildInfo instead
            BuildInfo info = (BuildInfo) s.readObject();

            _lastGoodBuildTime = info.lastGoodBuild;
            _lastBuildAttemptTime = info.lastBuild;
            _label = info.label;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void overwriteWithUserArguments(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            try {
                if (args[i].equals("-lastbuild")) {
                    _lastBuildAttemptTime = processLastBuildArg(args[i + 1]);
                    _lastGoodBuildTime = _lastBuildAttemptTime;
                } else if (args[i].equals("-label")) {
                    //(PENDING) check format of label
                    _label = args[i + 1];
                } else if (args[i].equals("-properties")) {
                    _propsFileName = args[i + 1];
                }
            } catch (RuntimeException re) {
                re.printStackTrace();
                usage();
            }
        }
    }

    public void setDefaultPropsFileIfNecessary() {
        if (_propsFileName == null) {
            if (new File(DEFAULT_PROPERTIES_FILENAME).exists()) {
                _propsFileName = DEFAULT_PROPERTIES_FILENAME;
            }
        }
    }
    
    public boolean buildInfoSpecified() {
        return 
         (_lastBuildAttemptTime != null) && (_label != null) && (_propsFileName != null);
    }

    private String processLastBuildArg(String lastBuild) {
        if (!isCompleteTime(lastBuild)) {
            throw new IllegalArgumentException(
             "Bad format for last build: " + lastBuild);
        }
        return lastBuild;
    }    

    private boolean isCompleteTime(String time) {
        int expectedLength = 14;
        if (time.length() < expectedLength) {
            return false;
        }
        
        return true;
    }
    //////////////////////////
    // END handling arguments
    //////////////////////////
    /**
     * Serialize the label and timestamp of the last good build
     */
    private void writeBuildInfo() {
        try {
            BuildInfo info = 
             new BuildInfo(_lastBuildAttemptTime, _lastGoodBuildTime, _label);
            ObjectOutputStream s = new ObjectOutputStream(new FileOutputStream(BUILDINFO_FILENAME));
            s.writeObject(info);
            s.flush();
            s.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Load properties file, see masterbuild.properties for descriptions of 
     * properties.
     */
    private void loadProperties() throws Exception {
        File propFile = new File(_propsFileName);
        
        if (!propFile.exists()) {
            throw new FileNotFoundException("Properties file \"" + propFile 
             + "\" not found");
        }
        
        Properties props = new Properties();
        props.load(new FileInputStream(propFile));
        
        StringTokenizer st = new StringTokenizer(props.getProperty("auxlogfiles"), ",");
        _auxLogProperties = new Vector();
        while (st.hasMoreTokens()) {
            String nextFile = st.nextToken().trim();
            _auxLogProperties.add(nextFile);
        }
        
        _buildInterval = Integer.parseInt(props.getProperty("buildinterval"))*1000;
        
        _debug = getBooleanProperty(props, "debug");
        _verbose = getBooleanProperty(props, "verbose");
        _mapSourceControlUsersToEmail = getBooleanProperty(props, 
         "mapSourceControlUsersToEmail");
        
        _defaultEmailSuffix = props.getProperty("defaultEmailSuffix");
        _mailhost = props.getProperty("mailhost");
        _servletURL = props.getProperty("servletURL");
        _returnAddress = props.getProperty("returnAddress");
        _buildmaster = getSetFromString(props.getProperty("buildmaster"));
        _notifyOnFailure = getSetFromString(props.getProperty("notifyOnFailure"));
        _reportSuccess = getBooleanProperty(props, "reportSuccess");
        _spamWhileBroken = getBooleanProperty(props, "spamWhileBroken");
        
        _logDir = props.getProperty("logDir"); 
        new File(_logDir).mkdirs();
        
        String buildStatusFileName = _logDir + File.separator 
         + props.getProperty("currentBuildStatusFile");
        log("Creating " + buildStatusFileName);
        _currentBuildStatusFile = new File(buildStatusFileName);
        _currentBuildStatusFile.createNewFile();
        
        _antFile = props.getProperty("antfile");
        _antTarget = props.getProperty("target");
        _cleanAntTarget = props.getProperty("cleantarget");
        _cleanBuildEvery = Integer.parseInt(props.getProperty("cleanBuildEvery"));
        _labelIncrementerClassName = props.getProperty("labelIncrementerClass");
        if (_labelIncrementerClassName == null) {
            _labelIncrementerClassName = DefaultLabelIncrementer.class.getName();
        }
        
        _emailmapFilename = props.getProperty("emailmap");
        _useEmailMap = usingEmailMap(_emailmapFilename);
        
        if (_debug || _verbose)
            props.list(System.out);
    }

    private boolean getBooleanProperty(Properties props, String key) {
        try {
            return props.getProperty(key).equals("true");
        } catch (NullPointerException npe) {
            log("Missing " + key + " property.  Using 'false'.");
            return false;
        }
    }    
    
    /**
     * This method infers from the value of the email
     * map filename, whether or not the email map is being
     * used. For example, if the filename is blank
     * or null, then the map is not being used.
     * 
     * @param emailMapFileName
     *               Name provided by the user.
     * @return true if the email map should be consulted, otherwise false.
     */
    private boolean usingEmailMap(String emailMapFileName) {
        //If the user specified name is null or blank or doesn't exist, then 
        //  the email map is not being used.
        if (emailMapFileName == null || emailMapFileName.trim().length() == 0) {
            return false;
        }
        //Otherwise, check to see if the filename provided exists and is readable.
        File userEmailMap = new File(emailMapFileName);
        return userEmailMap.exists() && userEmailMap.canRead();
    }

    /**
     * Loop infinitely, firing off the build as necessary.  Reloads the 
     * properties file every time so that the build process need not be stopped,
     * only the property file needs to be edited if changes are necessary.  Will
     * execute an alternate ant task every n builds, so that we can possibly 
     * execute a full clean build, etc.
     */
    public void execute() {
        try {
            int buildcounter = 0;
            while (true) {
                Date startTime = new Date();
                startLog();
                loadProperties();

                //Set the security manager to one which will prevent 
                // the Ant Main class from killing the VM.
                SecurityManager oldSecMgr = System.getSecurityManager();
                System.setSecurityManager(new NoExitSecurityManager());
                try {
                    Main.main(getCommandLine(buildcounter));
                } catch (ExitException ee) {
                    //Ignoring the exit exception from Main.
                } finally {
                    //Reset the SecurityManager to the old one.
                    System.setSecurityManager(oldSecMgr);
                }

                //(PENDING) do this in buildFinished?
                if (_buildNotNecessary) {
                    if (!_lastBuildSuccessful && _spamWhileBroken) {
                        sendBuildEmail(_projectName + "Build still failing...");
                    }
                } else {
                    if (_lastBuildSuccessful) {
                        buildcounter++;
                        if(_reportSuccess) {
                            sendBuildEmail(_projectName + " Build " + _label 
                             + " Successful");
                        } else {
                            log("Skipping email notifications for successful builds");
                        }
                        incrementLabel();
                    } else {
                        sendBuildEmail(_projectName + "Build Failed");
                    }
                    writeBuildInfo();
                }
                long timeToSleep = getSleepTime(startTime);
                endLog(timeToSleep);
                Thread.sleep(timeToSleep);
            }

        } catch (InterruptedException e) {
            log("Exception trying to sleep");
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendBuildEmail(String message) {
        Set emails = getEmails(_userList);
        emailReport(emails, message);
    }
    
    private long getSleepTime(Date startTime) {
        if (_isIntervalAbsolute) {
            // We need to sleep up until startTime + buildInterval.
            // Therefore, we need startTime + buildInterval - now.
            Date now = new Date();
            long sleepTime = startTime.getTime() + _buildInterval - now.getTime();
            sleepTime = (sleepTime < 0 ? 0 : sleepTime);
            return sleepTime;
        } else {
            return _buildInterval;
        }
    }
    
    private String[] getCommandLine(int buildCounter) {
        Vector v = new Vector();
        v.add("-DlastGoodBuildTime=" + _lastGoodBuildTime);
        v.add("-DlastBuildAttemptTime=" + _lastBuildAttemptTime);
        v.add("-Dlabel=" + _label);
        v.add("-listener");
        v.add(this.getClass().getName());

        if (_debug)
            v.add("-debug");
        if (_verbose)
            v.add("-verbose");

        v.add("-buildfile");
        v.add(_antFile);

        if (((buildCounter % _cleanBuildEvery) == 0) && _cleanAntTarget != "") {
            log("Using clean target");
            v.add(_cleanAntTarget);
        } else if (_antTarget != "") {
            log("Using normal target");
            v.add(_antTarget);
        }

        return(String[])v.toArray(new String[v.size()]);
    }

    /**
     * Merge any auxiliary xml-based files into the main log xml,
     * e.g. xml from junit, modificationset, bugs, etc.
     * only copies text from aux files to the main ant log,
     * and thus assumes that your aux xml has valid syntax.
     * The auxiliary xml files are declared in the masterbuild properties
     * file.  If the auxiliary xml File is a directory, all files with extension
     * xml will be appended.
     **/
    private void mergeAuxXmlFiles(Project antProject) {
        _logFile = getFinalLogFileName(antProject);
        XMLLogMerger merger = 
         new XMLLogMerger(_logFile, antProject.getProperty("XmlLogger.file"), 
         _auxLogFiles, _label, _today);
        try {
            merger.merge();
        } catch (IOException ioe) {
            System.err.println("Failure merging XML files: " + ioe.getMessage());
        }
    }
    
    /**
     * Returns the filename that should be used as the
     * composite log file. This method uses information
     * from previous steps in the build process, like
     * whether or not the build was successful, to determine
     * what the filename will be.
     *
     * @param proj   Project to get property values from.
     * @return Name of the composite log file.
     */
    private String getFinalLogFileName(Project proj) {
        String dateStamp = proj.getProperty("DSTAMP");
        String timeStamp = proj.getProperty("TSTAMP");
        if (dateStamp == null || timeStamp == null) {
            throw new RuntimeException("Datestamp and timestamp are not set."
                                       + " The ANT tstamp task must be called"
                                       + " before MasterBuild will work.");
        }

        String logFileName = "log" + dateStamp + timeStamp;
        if (_lastBuildSuccessful) {
            logFileName += "L" + _label;
        }
        logFileName += ".xml";
        logFileName = _logDir + File.separator + logFileName;

        return logFileName;
    }

    /**
     * This method delegates to the dynamically loaded LabelIncrementer. The actual
     * implementing class can be declared in the masterbuild.properties file, or
     * the class DefaultLabelIncrementer will be used.
     *
     * @see loadProperties
     */
    private void incrementLabel() {
        //REDTAG - Paul - How explicit should we make the error messages? Is ClassCastException
        //  enough?
        try {
            Class incrementerClass = Class.forName(_labelIncrementerClassName);
            LabelIncrementer incr = (LabelIncrementer)incrementerClass.newInstance();
            _label = incr.incrementLabel(_label);
        } catch (Exception e) {
            log("Error incrementing label.");
            e.printStackTrace();
        }

    }

    //(PENDING) Extract e-mail stuff into another class
    private Set getEmails(String list) {
        //The buildmaster is always included in the email names.
        Set emails = new HashSet(_buildmaster);

        //If the build failed then the failure notification emails are included.
        if (!_lastBuildSuccessful) {
            emails.addAll(_notifyOnFailure);
        }
        
        if (_mapSourceControlUsersToEmail) {
            log("Adding source control users to e-mail list: " + list);
            emails.addAll(getSetFromString(list));
        }

        return translateAliases(emails);
    }

    private void emailReport(Set emails, String subject) {
        StringBuffer logMessage = new StringBuffer("Sending mail to:");
        for(Iterator iter = emails.iterator(); iter.hasNext();) {
            logMessage.append(" " + iter.next());
        }
        log(logMessage.toString());

        String message = "View results here -> " + _servletURL + "?"
         + _logFile.substring(_logFile.lastIndexOf(File.separator) + 1, 
         _logFile.lastIndexOf("."));

        try {
            Mailer mailer = new Mailer(_mailhost, emails, _returnAddress);
            mailer.sendMessage(subject, message);
        } catch (javax.mail.MessagingException me) {
            System.out.println("Unable to send email.");
            me.printStackTrace();
        }
    }

    /**
     * Forms a set of unique words/names from the comma
     * delimited list provided. Maybe empty, never null.
     * 
     * @param commaDelim String containing a comma delimited list of words,
     *                   e.g. "paul,Paul, Tim, Alden,,Frank".
     * @return Set of words; maybe empty, never null.
     */
    private Set getSetFromString(String commaDelim) {
        Set elements = new TreeSet();
        if (commaDelim == null) {
            return elements;
        }

        StringTokenizer st = new StringTokenizer(commaDelim, ",");
        while (st.hasMoreTokens()) {
            String mapped = st.nextToken().trim();
            elements.add(mapped);
        }

        return elements;
    }

    private Set translateAliases(Set possibleAliases) {
        Set returnAddresses = new HashSet();
        boolean aliasPossible = false;
        for (Iterator iter = possibleAliases.iterator(); iter.hasNext();) {
            String nextName = (String) iter.next();
            if (nextName.indexOf("@") > -1) {
                //The address is already fully qualified.
                returnAddresses.add(nextName);
            } else if (_useEmailMap) {
                File emailmapFile = new File(_emailmapFilename);
                Properties emailmap = new Properties();
                try {
                    emailmap.load(new FileInputStream(emailmapFile));
                } catch (Exception e) {
                    log("error reading email map file: " + _emailmapFilename);
                    e.printStackTrace();
                }

                String mappedNames = emailmap.getProperty(nextName);
                if (mappedNames == null) {
                    if (_defaultEmailSuffix != null) {
                        nextName += _defaultEmailSuffix;
                    }
                    returnAddresses.add(nextName);
                } else {
                    returnAddresses.addAll(getSetFromString(mappedNames));
                    aliasPossible = true;
                }
            } else {
                if (_defaultEmailSuffix != null) {
                    nextName += _defaultEmailSuffix;
                }
                returnAddresses.add(nextName);
            }
        }
        
        if (aliasPossible) {
            returnAddresses = translateAliases(returnAddresses);
        }
        
        return returnAddresses;
    }

    /**
     *	convenience method for logging
     */
    public void log(String s) {
        log(s, System.out);
    }

    /**
     *	divert logging to any printstream
     */
    public void log(String s, PrintStream out) {
        out.println("[masterbuild] " + s);
    }

    /**
     *	Print header for each build attempt.
     */
    private void startLog() {
        log("***** Starting Build Cycle");
        log("***** Label: " + _label);
        log("***** Last Good Build: " + _lastGoodBuildTime);
        log("\n");
    }

    /**
     *	Print footer for each build attempt.
     */
    private void endLog(long sleepTime) {
        log("\n");
        log("***** Ending Build Cycle, sleeping " + (sleepTime/1000.0) + " seconds until next build.\n\n\n");
        log("***** Label: " + _label);
        log("***** Last Good Build: " + _lastGoodBuildTime);
        log("\n");
    }

    /**
     *	Print usage instructions if command line arguments are not correctly specified.
     */
    public void usage() {
        System.out.println("Usage:");
        System.out.println("");
        System.out.println("Starts a continuous integration loop");
        System.out.println("");
        System.out.println("java MasterBuild [options]");
        System.out.println("where options are:");
        System.out.println("   -lastbuild timestamp   where timestamp is in yyyyMMddHHmmss format.  note HH is the 24 hour clock.");
        System.out.println("   -label label           where label is in x.y format, y being an integer.  x can be any string.");
        System.out.println("   -properties file       where file is the masterbuild properties file, and is available in the classpath");
        System.out.println("");
        System.exit(0);
    }

    /**
     * Writes a file with a snippet of html regarding
     * whether the build is currently running or
     * when it will start again.  The build servlet
     * will then look for this file to report the
     * current build status ("build started at x" or
     * "next build at x").
     *
     * @param isRunning true if the build is currently
     *                  running, otherwise false.
     */
    private void logCurrentBuildStatus(boolean isRunning) {
        String currentlyRunning = "<br>&nbsp;<br><b>Current Build Started At:</b><br>";
        String notRunning = "<br>&nbsp;<br><b>Next Build Starts At:</b><br>";
        SimpleDateFormat numericDateFormatter 
         = new SimpleDateFormat("MM/dd/yyyy HH:mm");
        Date buildTime = new Date();
        if (!isRunning) {
            buildTime = new Date(buildTime.getTime() + _buildInterval);
        }

        try {        
            FileWriter currentBuildWriter = new FileWriter(_currentBuildStatusFile);
            currentBuildWriter.write((isRunning ? currentlyRunning : notRunning) 
             + numericDateFormatter.format(buildTime) + "<br>");
            currentBuildWriter.close();
            currentBuildWriter = null;
        } catch (IOException ioe) {
            log("Problem writing current build status");
            ioe.printStackTrace();
        }
    }

    /**
     * Overrides method in XmlLogger.  Gets us the timestamp that we performed 
     * a "get" on our source control repository and whether or not the build was 
     * successful.  Calls the method on XmlLogger afterward.
     */
    public void buildFinished(BuildEvent buildevent) {
        logCurrentBuildStatus(false);

        Project proj = buildevent.getProject();
        _projectName = proj.getName();
        _buildNotNecessary = 
         (proj.getProperty(ModificationSet.BUILDUNNECESSARY) != null);
        if (_buildNotNecessary) {
            return;
        }

        _today = proj.getProperty("TODAY");

        _userList = proj.getProperty(ModificationSet.USERS);

        _lastBuildSuccessful = false;

        _lastBuildAttemptTime = proj.getProperty(ModificationSet.SNAPSHOTTIMESTAMP);
        if (buildevent.getException() == null) {
            _lastBuildSuccessful = true;
            _lastGoodBuildTime = _lastBuildAttemptTime;
        }

        //get the exact filenames from the ant properties that tell us what aux xml files we have...
        _auxLogFiles = new Vector();
        for (Enumeration e = _auxLogProperties.elements(); e.hasMoreElements();) {
            String propertyName = (String)e.nextElement();
            String fileName = proj.getProperty(propertyName);
            if (fileName == null) {
                log("Auxillary Log File Property '" + propertyName + "' not set.");
            } else {
                _auxLogFiles.add(fileName);
            }            

        }

        //If the XmlLogger.file property doesn't exist, we will set it here to a default
        //  value. This will short circuit XmlLogger from setting the default value.
        String prop = proj.getProperty("XmlLogger.file");
        if (prop == null || prop.trim().length() == 0) {
            proj.setProperty("XmlLogger.file", XML_LOGGER_FILE);
        }

        super.buildFinished(buildevent);
        mergeAuxXmlFiles(proj);
    }

    /**
     *	Overrides method in XmlLogger.  writes snippet of html to disk
     *	specifying the start time of the running build, so that the build servlet can pick this up.
     */
    public void buildStarted(BuildEvent buildevent) {
        if (!canWriteXMLLoggerFile()) {
            throw new BuildException("No write access to " + XML_LOGGER_FILE);
        }

        logCurrentBuildStatus(true);
        super.buildStarted(buildevent);
    }

    boolean canWriteXMLLoggerFile() {
        File logFile = new File(XML_LOGGER_FILE);
        if (!logFile.exists() || logFile.canWrite()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Wraps the XmlLogger's method with a logging level check
     */
    public void messageLogged(BuildEvent event) {
        int logLevel = event.getPriority();
        if (false == _debug && Project.MSG_DEBUG == logLevel) {
            return;
        }
        
        if (false == _verbose && Project.MSG_VERBOSE == logLevel) {
            return;
        }
        
        super.messageLogged(event);
    }    
    
}

/**
 * Inner class to hold the build information elements
 * which will be serialized and deseralized by the
 * MasterBuild process.
 */
class BuildInfo implements Serializable {
    String lastBuild;
    String lastGoodBuild;
    String label;

    BuildInfo(String lastBuildAttempt, String lastGoodBuild, String label) {
        lastBuild = lastBuildAttempt;
        this.lastGoodBuild = lastGoodBuild;
        this.label = label;
    }
}

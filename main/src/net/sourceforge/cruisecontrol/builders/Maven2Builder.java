package net.sourceforge.cruisecontrol.builders;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import net.sourceforge.cruisecontrol.Builder;
import net.sourceforge.cruisecontrol.CruiseControlException;
import net.sourceforge.cruisecontrol.util.DateUtil;
import net.sourceforge.cruisecontrol.util.ValidationHelper;
import net.sourceforge.cruisecontrol.util.Util;

import org.apache.log4j.Logger;
import org.jdom.Element;

/**
 * Maven2 builder class based on the Maven builder class from 
 * <a href="mailto:fvancea@maxiq.com">Florin Vancea</a>. 
 * <br />
 * Attempts to mimic the behavior of Ant builds, at least as far as CC is
 * concerned. Basically it's a (heavily) edited version of AntBuilder. No style
 * at all, but serves its purpose. :)
 * 
 * @author Steria Benelux Sa/Nv - Provided without any warranty
 */
public class Maven2Builder extends Builder {

    private static final Logger LOG = Logger.getLogger(Maven2Builder.class);
    static final String MVN_BIN_DIR = "bin" + File.separator;

    private String mvnHome;
    private String mvnScript;
    private String pomFile;
    private String goal;
    private String settingsFile;
    private String activateProfiles;
    private long timeout = ScriptRunner.NO_TIMEOUT;
    private String flags;

    /**
     * Set an Alternate path for the user settings file.
     * @param settingsFile Alternate path for the user settings file.
     */
    public void setSettingsFile(String settingsFile) {

        this.settingsFile = settingsFile;
    }

    /**
     * Set the comma-delimited list of profiles to activate.
     * @param activateProfiles comma-delimited list of profiles to activate.
     */
    public void setActivateProfiles(String activateProfiles) {

        this.activateProfiles = activateProfiles;
    }
    
    /**
     * Set mvnHome. This will be used to find the mvn script which is mvnHome/bin/
     * @param mvnHome the mvn home
     * @throws CruiseControlException When the mvn script could not be found this exception is thrown.
     */
    public void setMvnHome(String mvnHome) throws CruiseControlException {

        if (!mvnHome.endsWith(File.separator)) {
            mvnHome = mvnHome + File.separator;
        }
        this.mvnHome = mvnHome;

        LOG.debug("MvnHome = " + this.mvnHome + " Mvn should be in " + this.mvnHome + MVN_BIN_DIR);
    }

    /**
     * Full path to Maven script, which overrides the default ".../bin/mvn".
     * @param mvnScipt
     */ 
    public void setMvnScript(final String mvnScipt) {
        this.mvnScript = mvnScipt;
    }
    
    /**
     * Set the pom file. This is also used to find the working directory.
     * @param pomFile
     */
    public void setPomFile(String pomFile) {

        this.pomFile = pomFile;

        LOG.debug("pom file : " + this.pomFile);
    }
    
    public void setGoal(String goal) {

        this.goal = goal;
    }

    public void setTimeout(long timeout) {

        this.timeout = timeout;
    }

    /**
     * Check at the starting of CC if required attributes are set
     */
    public void validate() throws CruiseControlException {

        super.validate();

        ValidationHelper.assertFalse(mvnScript != null && mvnHome != null,
                    "'mvnhome' and 'mvnscript' cannot both be set");

        if (mvnHome != null) {
            ValidationHelper.assertIsSet(mvnHome, "mvnhome", getClass());
            final File mvnHomeDir = new File(mvnHome);
            ValidationHelper.assertTrue(mvnHomeDir.exists() && mvnHomeDir.isDirectory(),
                    "'mvnhome' must exist and be a directory. Expected to find "
                            + mvnHomeDir.getAbsolutePath()
                            + "; Check the mvnhome attribute of the maven2 plugin");

            mvnScript = findMaven2Script(Util.isWindows());
        }
        ValidationHelper.assertTrue(mvnScript != null, "'mvnhome' or 'mvnscript' must be set.");

        ValidationHelper.assertIsSet(pomFile, "pomfile", getClass());
        ValidationHelper.assertIsSet(goal, "goal", this.getClass());
        if (getGoalSets().isEmpty()) {
            ValidationHelper.assertIsSet(null, "goal", this.getClass());
        }

        if (settingsFile != null) {
            ValidationHelper.assertTrue(new File(settingsFile).exists(), 
                    "The settings file could not be found : " + settingsFile);
        }
    }

    /**
     * build and return the results via xml.  debug status can be determined
     * from log4j category once we get all the logging in place.
     */
    public Element build(Map buildProperties) throws CruiseControlException {

        //This check is done here because the pom can be downloaded after CC is started 
        // and before this plugin is run
        final File filePomFile = new File(pomFile);
        ValidationHelper.assertTrue(filePomFile.exists(),
                "the pom file could not be found : " + filePomFile.getAbsolutePath()
                        + "; Check the 'pomfile' attribute: " + pomFile);

        File workingDir = filePomFile.getParentFile();
        LOG.debug("Working dir is : " + workingDir.toString());

        long startTime = System.currentTimeMillis();

        Element buildLogElement = new Element("build");

        List goalSets = getGoalSets();
        for (int i = 0; i < goalSets.size(); i++) {

            String goals = (String) goalSets.get(i);

            Maven2Script script = new Maven2Script(buildLogElement, mvnScript, pomFile, goals,
                    settingsFile, activateProfiles, flags);
            script.setBuildProperties(buildProperties);

            ScriptRunner scriptRunner = new ScriptRunner();
            boolean scriptCompleted = scriptRunner.runScript(workingDir, script, timeout);
            script.flushCurrentElement();

            if (!scriptCompleted) {
                LOG.warn("Build timeout timer of " + timeout + " seconds has expired");
                buildLogElement = new Element("build");
                buildLogElement.setAttribute("error", "build timeout");
            } else if (script.getExitCode() != 0) {
                // The maven.bat actually never returns error,
                // due to internal cleanup called after the execution itself...                
                synchronized (buildLogElement) {
                    buildLogElement.setAttribute("error", "Return code is " + script.getExitCode());
                }
            }

            if (buildLogElement.getAttribute("error") != null) {
                break;
            }

        }

        long endTime = System.currentTimeMillis();

        buildLogElement.setAttribute("time", DateUtil.getDurationAsString((endTime - startTime)));
        return buildLogElement;
    }

    public Element buildWithTarget(Map properties, String target) throws CruiseControlException {
        String origGoal = goal;
        try {
            goal = target;
            return build(properties);
        } finally {
            goal = origGoal;
        }
    }
    
    /**
     * Produces sets of goals, ready to be run each in a distinct call to Maven.
     * Separation of sets in "goal" attribute is made with '|'.
     *
     * @return a List containing String elements
     */
    List getGoalSets() {

        List list = new ArrayList();
        if (goal != null) {
            StringTokenizer stok = new StringTokenizer(goal, "|");
            while (stok.hasMoreTokens()) {
                String subSet = stok.nextToken().trim();
                if (subSet == null || subSet.length() == 0) {
                    continue;
                }
                list.add(subSet);
            }
        }
        return list;
    }

    /**
     * Set flags. E.g.: '-U -o'
     * @param flags set the flags
     */
    public void setFlags(String flags) {
    
        this.flags = flags;
    }


    /**
     * If the mvnhome attribute is set, then this method returns the correct shell script
     * to use for a specific environment.
     */
    protected String findMaven2Script(boolean isWindows) throws CruiseControlException {
        if (mvnHome == null) {
            throw new CruiseControlException("mvnhome attribute not set.");
        }

        if (isWindows) {
            return mvnHome + MVN_BIN_DIR  + "mvn.bat";
        } else {
            return mvnHome + MVN_BIN_DIR + "mvn";
        }
    }

}

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

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.XmlLogger;

/**
 * Executes a CruiseProject.
 *
 * @author  <a href="mailto:robertdw@bigpond.net.au">Robert Watkins</a>
 * @author  <a href="mailto:johnny.cass@epiuse.com">Johnny Cass</a>
 *
 * @version Revision: 1.1.1
 */
public class BuildRunner {

    /* ========================================================================
     * Static class members.
     */

    /* ========================================================================
     * Instance members.
     */
    
    private CruiseProject _project;
    private String _target;
    
    private String _lastGoodBuildTime;
    private String _lastBuildAttemptTime;
    private String _label;
    private java.io.File _buildFile;

    private java.io.PrintStream _out = System.out;
    private java.io.PrintStream _err = System.err;
    
    private CruiseLogger _logger;
    
    private Throwable _error = null;

    /* ========================================================================
     * Constructors
     */
    
    /** Creates new BuildRunner */
    public BuildRunner(String buildFileName, String target, String lastGoodBuildTime, String lastBuildAttemptTime, String label, CruiseLogger logger) {
        this(new java.io.File(buildFileName), target, lastGoodBuildTime, lastBuildAttemptTime, label, logger);
    }

    public BuildRunner(java.io.File buildFile, String target, String lastGoodBuildTime, String lastBuildAttemptTime, String label, CruiseLogger logger) {
        _logger = logger;
        try {
            _buildFile = buildFile.getCanonicalFile();
        }
        catch (java.io.IOException e) {
            throw new RuntimeException("Could not get the canonical form of " + buildFile.toString());
        }
        loadProject();
        _target = target;
        _lastGoodBuildTime = lastGoodBuildTime;
        _lastBuildAttemptTime = lastBuildAttemptTime;
        _label = label;
    }

    /* ========================================================================
     * Public Methods.
     */

    public boolean runBuild() {
        CruiseProject project = getProject();

        project.fireBuildStarted();
        _project.init();
        configureProject();
        
        try {
            project.setUserProperty("ant.version", "1.4alpha");
            project.setUserProperty("lastGoodBuildTime", _lastGoodBuildTime);
            project.setUserProperty("lastBuildAttemptTime", _lastBuildAttemptTime);
            project.setUserProperty("label", _label);
            project.setUserProperty("ant.file" , _buildFile.getAbsolutePath() );

            project.executeTarget(_target);
            return true;
        }
        catch (Throwable theError) {
            _error = theError;
            return false;
        }
        finally {
            project.fireBuildFinished(_error);
        }
    }
    
    public CruiseProject getProject() {
        return _project;
    }
    
    public void reset() {
        getProject().reset();
    }
    
    public Throwable getError() {
       return _error;
    }

    /* ========================================================================
     * Public Methods.
     */

    void loadProject() {
        _project = new CruiseProject();
        setDefaultLogger();
        _project.addBuildListener(_logger);
    }
    
    void configureProject() throws BuildException {
        // first use the ProjectHelper to create the project object
        // from the given build file.
        try {
            Class.forName("javax.xml.parsers.SAXParserFactory");
            ProjectHelper.configureProject(getProject(), _buildFile);
        } catch (NoClassDefFoundError ncdfe) {
            throw new BuildException("No JAXP compliant XML parser found. See http://java.sun.com/xml for the\nreference implementation.", ncdfe);
        } catch (ClassNotFoundException cnfe) {
            throw new BuildException("No JAXP compliant XML parser found. See http://java.sun.com/xml for the\nreference implementation.", cnfe);
        } catch (NullPointerException npe) {
            throw new BuildException("No JAXP compliant XML parser found. See http://java.sun.com/xml for the\nreference implementation.", npe);
        }
    }

    void setDefaultLogger() {
        org.apache.tools.ant.DefaultLogger defaultLogger = new org.apache.tools.ant.DefaultLogger();
        defaultLogger.setMessageOutputLevel(_logger.getMessageLevel());
        defaultLogger.setOutputPrintStream(_out);
        defaultLogger.setErrorPrintStream(_err);
        getProject().addBuildListener(defaultLogger);
    }
}

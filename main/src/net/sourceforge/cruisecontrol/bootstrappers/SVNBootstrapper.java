/********************************************************************************
 * CruiseControl, a Continuous Integration Toolkit
 * Copyright (c) 2001, ThoughtWorks, Inc.
 * 200 E. Randolph, 25th Floor
 * Chicago, IL 60601 USA
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
package net.sourceforge.cruisecontrol.bootstrappers;

import net.sourceforge.cruisecontrol.Bootstrapper;
import net.sourceforge.cruisecontrol.CruiseControlException;
import net.sourceforge.cruisecontrol.util.Commandline;
import net.sourceforge.cruisecontrol.util.ValidationHelper;

import org.apache.log4j.Logger;

import java.io.File;

/**
 * The SVNBootstrapper will handle updating a single file from Subversion before the build begins.
 *
 * @see <a href="http://subversion.tigris.org/">subversion.tigris.org</a>
 * @author <a href="etienne.studer@canoo.com">Etienne Studer</a>
 */
public class SVNBootstrapper implements Bootstrapper {

    /** serialVersionUID */
    private static final long serialVersionUID = -4444449761990187324L;

    private static final Logger LOG = Logger.getLogger(SVNBootstrapper.class);

    /** Configuration parameters */
    private String fileName;
    private String localWorkingCopy;
    private String userName;
    private String password;
    private String configDir;

    /**
     * @param configDir the configuration directory for the subversion client.
     */
    public void setConfigDir(String configDir) {
        this.configDir = configDir;
    }

    /**
     * @param fileName
     *            the file to update from the Subversion repository.
     */
    public void setFile(String fileName) {
        this.fileName = fileName;
    }

    /**
     * Sets the local working copy to use when making calls to Subversion.
     *
     * @param localWorkingCopy
     *            String indicating the relative or absolute path to the local working copy of the Subversion repository
     *            on which to execute the update command.
     */
    public void setLocalWorkingCopy(String localWorkingCopy) {
        this.localWorkingCopy = localWorkingCopy;
    }

    /**
     * @param userName
     *            the username for authentication.
     */
    public void setUsername(String userName) {
        this.userName = userName;
    }

    /**
     * @param password
     *            the password for authentication.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * This method validates that at least the filename or the local working copy location has been specified.
     *
     * @throws CruiseControlException
     *             Thrown when the repository location and the local working copy location are both null
     */
    public void validate() throws CruiseControlException {
        ValidationHelper.assertTrue(fileName != null || localWorkingCopy != null,
                "At least 'filename' or 'localWorkingCopy' is a "
                        + "required attribute on the Subversion bootstrapper task");

        if (localWorkingCopy != null) {
            File workingDir = new File(localWorkingCopy);
            ValidationHelper.assertTrue(workingDir.exists() && workingDir.isDirectory(),
                    "'localWorkingCopy' must be an existing directory. Was" + workingDir.getAbsolutePath());
        }
    }

    /**
     * Update the specified file from the subversion repository.
     *
     * @throws CruiseControlException
     */
    public void bootstrap() throws CruiseControlException {
        buildUpdateCommand().executeAndWait(LOG);
    }

    /**
     * Generates the command line for the svn update command.
     *
     * For example:
     *
     * 'svn update --non-interactive filename'
     *
     * @return the command line for the svn update command
     * @throws CruiseControlException
     *             if the working directory is not valid.
     */
    Commandline buildUpdateCommand() throws CruiseControlException {
        Commandline command = new Commandline();
        command.setExecutable("svn");

        if (localWorkingCopy != null) {
            command.setWorkingDirectory(localWorkingCopy);
        }

        command.createArgument("update");
        command.createArgument("--non-interactive");
        if (configDir != null) {
            command.createArguments("--config-dir", configDir);
        }
        if (userName != null || password != null) {
            command.createArgument("--no-auth-cache");
            if (userName != null) {
                command.createArguments("--username", userName);
            }
            if (password != null) {
                command.createArguments("--password", password);
            }
        }
        if (fileName != null) {
            command.createArgument(fileName);
        }

        LOG.debug("SVNBootstrapper: Executing command = " + command);

        return command;
    }
}

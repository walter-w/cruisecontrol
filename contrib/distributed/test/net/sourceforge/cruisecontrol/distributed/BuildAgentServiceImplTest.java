package net.sourceforge.cruisecontrol.distributed;

import junit.framework.TestCase;

import java.util.Properties;
import java.util.Arrays;
import java.util.Date;
import java.io.File;
import java.rmi.RemoteException;

import org.jdom.Element;
import org.apache.log4j.Logger;
import net.sourceforge.cruisecontrol.util.Util;
import net.sourceforge.cruisecontrol.distributed.util.PropertiesHelper;
import net.sourceforge.cruisecontrol.CruiseControlException;
import net.sourceforge.cruisecontrol.builders.DistributedMasterBuilderTest;

/**
 * Created by IntelliJ IDEA.
 * User: drollo
 * Date: May 25, 2005
 * Time: 1:54:38 PM
 * To change this template use File | Settings | File Templates.
 */
public class BuildAgentServiceImplTest extends TestCase {

    private static final Logger LOG = Logger.getLogger(BuildAgentServiceImplTest.class);

    public static final File TEST_CONFIG_FILE = new File("test/testdist.config.xml");
    public static final String TEST_AGENT_PROPERTIES_FILE = "testdist.agent.properties";
    public static final String TEST_USER_DEFINED_PROPERTIES_FILE = "testdist.user-defined.properties";

    private static final File DIR_LOGS = new File(PropertiesHelper.RESULT_TYPE_LOGS);
    private static final File DIR_OUTPUT = new File(PropertiesHelper.RESULT_TYPE_OUTPUT);

    protected void setUp() throws Exception {
        DIR_LOGS.delete();
        DIR_OUTPUT.delete();
    }

    protected void tearDown() throws Exception {
        deleteDirConfirm(DIR_LOGS);
        deleteDirConfirm(DIR_OUTPUT);
    }

    private static void deleteDirConfirm(final File dirToDelete) {
        if (dirToDelete.exists()) {
            assertTrue("Error cleaning up test directory: " + dirToDelete.getAbsolutePath()
                    +  "\nDir Contents:\n" + Arrays.asList(dirToDelete.listFiles()),
                    dirToDelete.delete());
        }
    }


    public void testAsString() throws Exception {
        final BuildAgentServiceImpl agentImpl = new BuildAgentServiceImpl();
        agentImpl.setAgentPropertiesFilename(TEST_AGENT_PROPERTIES_FILE);

        String agentAsString = agentImpl.asString();
        assertTrue("Wrong value: " + agentAsString,
                agentAsString.startsWith("Machine Name: "));
        assertTrue("Wrong value: " + agentAsString,
                agentAsString.endsWith("Busy: false;\tSince: null;\tModule: null\n\t"
                + "Pending Restart: false;\tPending Restart Since: null\n\t"
                + "Pending Kill: false;\tPending Kill Since: null"));

        final String testModuleName = "testModuleName";
        final Properties projectProps = new Properties();
        projectProps.put(PropertiesHelper.DISTRIBUTED_MODULE, testModuleName);

        try {
            agentImpl.doBuild(null, projectProps); // gets far enough to set Module name...
            fail("should fail w/ NPE");
        } catch (NullPointerException e) {
            assertEquals(null, e.getMessage());
        }

        agentAsString = agentImpl.asString();
        assertTrue("Wrong value: " + agentAsString,
                agentAsString.startsWith("Machine Name: "));
        assertTrue("Wrong value: " + agentAsString,
                agentAsString.indexOf("Busy: true;\tSince: ") > -1);
        assertTrue("Wrong value: " + agentAsString,
                agentAsString.endsWith(";\tModule: " + testModuleName
                + "\n\tPending Restart: false;\tPending Restart Since: null\n\t"
                + "Pending Kill: false;\tPending Kill Since: null"));

        agentImpl.kill(true);
        agentAsString = agentImpl.asString();
        assertTrue("Wrong value: " + agentAsString,
                agentAsString.indexOf("Busy: true;\tSince: ") > -1);
        assertTrue("Wrong value: " + agentAsString,
                agentAsString.indexOf(";\tModule: " + testModuleName
                + "\n\tPending Restart: false;\tPending Restart Since: null\n\t"
                + "Pending Kill: true;\tPending Kill Since: ") > -1);
        assertNotNull(agentImpl.getPendingKillSince());

        agentImpl.setBusy(false); // fake build finish
        agentAsString = agentImpl.asString();
        assertTrue("Wrong value: " + agentAsString,
                agentAsString.indexOf("Busy: false;\tSince: null;\tModule: null\n\t"
                + "Pending Restart: false;\tPending Restart Since: null\n\t"
                + "Pending Kill: true;\tPending Kill Since: ") > -1);
        assertNotNull(agentImpl.getPendingKillSince());
    }

    public void testGetBuildingModule() throws Exception {
        final BuildAgentServiceImpl agentImpl = new BuildAgentServiceImpl();
        agentImpl.setAgentPropertiesFilename(TEST_AGENT_PROPERTIES_FILE);

        assertNull(agentImpl.getModule());

        final String testModuleName = "testModuleName";
        final Properties projectProps = new Properties();
        projectProps.put(PropertiesHelper.DISTRIBUTED_MODULE, testModuleName);

        try {
            agentImpl.doBuild(null, projectProps); // gets far enough to set Module name...
            fail("should fail w/ NPE");
        } catch (NullPointerException e) {
            assertEquals(null, e.getMessage());
        }

        assertEquals(testModuleName, agentImpl.getModule());

        agentImpl.setBusy(false); // fake build finish
        assertNull(agentImpl.getModule());
    }

    public void testKillNoWait() throws Exception {
        final BuildAgentServiceImpl agentImpl = new BuildAgentServiceImpl();
        agentImpl.setAgentPropertiesFilename(TEST_AGENT_PROPERTIES_FILE);

        assertFalse(agentImpl.isBusy());
        assertFalse(agentImpl.isPendingKill());
        assertNull(agentImpl.getPendingKillSince());
        assertFalse(agentImpl.isPendingRestart());
        assertNull(agentImpl.getPendingRestartSince());
        // make agent think it's building now
        agentImpl.claim();
        assertTrue(agentImpl.isBusy());
        assertFalse(agentImpl.isPendingKill());
        assertFalse(agentImpl.isPendingRestart());
        agentImpl.kill(false);
        assertTrue(agentImpl.isBusy());
        assertTrue(agentImpl.isPendingKill());
        assertNotNull(agentImpl.getPendingKillSince());
        assertFalse(agentImpl.isPendingRestart());
        assertNull(agentImpl.getPendingRestartSince());

        // fake finish agent build - not really needed here
        agentImpl.setBusy(false);
    }

    public void testRestartNoWait() throws Exception {
        final BuildAgentServiceImpl agentImpl = new BuildAgentServiceImpl();
        agentImpl.setAgentPropertiesFilename(TEST_AGENT_PROPERTIES_FILE);

        assertFalse(agentImpl.isBusy());
        assertFalse(agentImpl.isPendingKill());
        assertNull(agentImpl.getPendingKillSince());
        assertFalse(agentImpl.isPendingRestart());
        assertNull(agentImpl.getPendingRestartSince());
        // make agent think it's building now
        agentImpl.claim();
        assertTrue(agentImpl.isBusy());
        assertFalse(agentImpl.isPendingKill());
        assertFalse(agentImpl.isPendingRestart());
        // fake finish agent build
        try {
            agentImpl.restart(false);
            fail("Restart should fail outside of webstart");
        } catch (RuntimeException e) {
            assertEquals("Couldn't find webstart Basic Service. Is Agent running outside of webstart?",
                    e.getMessage());
        }
        assertTrue(agentImpl.isBusy());
        assertFalse(agentImpl.isPendingKill());
        assertNull(agentImpl.getPendingKillSince());
        assertTrue(agentImpl.isPendingRestart());
        assertNotNull(agentImpl.getPendingRestartSince());
    }

    public void testKillWithWait() throws Exception {
        final BuildAgentServiceImpl agentImpl = new BuildAgentServiceImpl();
        agentImpl.setAgentPropertiesFilename(TEST_AGENT_PROPERTIES_FILE);

        assertFalse(agentImpl.isBusy());
        assertFalse(agentImpl.isPendingKill());
        assertNull(agentImpl.getPendingKillSince());
        assertFalse(agentImpl.isPendingRestart());
        assertNull(agentImpl.getPendingRestartSince());
        // make agent think it's building now
        agentImpl.claim();
        assertTrue(agentImpl.isBusy());
        assertFalse(agentImpl.isPendingKill());
        assertFalse(agentImpl.isPendingRestart());
        agentImpl.kill(true);
        assertTrue(agentImpl.isBusy());
        assertTrue(agentImpl.isPendingKill());
        assertNotNull(agentImpl.getPendingKillSince());
        assertFalse(agentImpl.isPendingRestart());

        // fake finish agent build
        agentImpl.setBusy(false);
    }

    public void testRestartWithWait() throws Exception {
        final BuildAgentServiceImpl agentImpl = new BuildAgentServiceImpl();
        agentImpl.setAgentPropertiesFilename(TEST_AGENT_PROPERTIES_FILE);

        assertFalse(agentImpl.isBusy());
        assertFalse(agentImpl.isPendingKill());
        assertNull(agentImpl.getPendingKillSince());
        assertFalse(agentImpl.isPendingRestart());
        assertNull(agentImpl.getPendingRestartSince());
        // make agent think it's building now
        agentImpl.claim();
        assertTrue(agentImpl.isBusy());
        assertFalse(agentImpl.isPendingKill());
        assertNull(agentImpl.getPendingRestartSince());
        assertFalse(agentImpl.isPendingRestart());
        assertNull(agentImpl.getPendingRestartSince());
        agentImpl.restart(true);
        assertTrue(agentImpl.isBusy());
        assertFalse(agentImpl.isPendingKill());
        assertTrue(agentImpl.isPendingRestart());
        assertNotNull(agentImpl.getPendingRestartSince());

        // fake finish agent build
        try {
            agentImpl.setBusy(false);
            fail("Restart should fail outside of webstart");
        } catch (RuntimeException e) {
            assertEquals("Couldn't find webstart Basic Service. Is Agent running outside of webstart?",
                    e.getMessage());
        }
    }

    public void testRetrieveResultsAsZipBuildSuccess() throws Exception {
        final BuildAgentServiceImpl agentImpl = createTestAgent(false);
        try {
            assertTrue(agentImpl.resultsExist(PropertiesHelper.RESULT_TYPE_LOGS));
            assertTrue(agentImpl.resultsExist(PropertiesHelper.RESULT_TYPE_OUTPUT));
            assertTrue("Agent should be busy until build results are retrived and cleared.",
                    agentImpl.isBusy());
        } finally {
            // cleanup left over files
            agentImpl.clearOutputFiles();
        }
        assertFalse(agentImpl.isBusy());
    }

    public void testRetrieveResultsAsZipBuildFail() throws Exception {
        final BuildAgentServiceImpl agentImpl = createTestAgent(true);
        try {
            assertTrue(agentImpl.resultsExist(PropertiesHelper.RESULT_TYPE_LOGS));
            assertFalse(agentImpl.resultsExist(PropertiesHelper.RESULT_TYPE_OUTPUT));
            assertTrue("Agent should be busy until build results are retrived and cleared.",
                    agentImpl.isBusy());
        } finally {
            // cleanup left over files
            agentImpl.clearOutputFiles();
        }
        assertFalse(agentImpl.isBusy());
    }

    private static BuildAgentServiceImpl createTestAgent(boolean isBuildFailure)
            throws CruiseControlException, RemoteException {

        final BuildAgentServiceImpl agentImpl = new BuildAgentServiceImpl();
        agentImpl.setAgentPropertiesFilename(TEST_AGENT_PROPERTIES_FILE);

        // @todo Fix issues where AntBuilder fails if java is not on os path
        callTestDoBuild(isBuildFailure, agentImpl);

        return agentImpl;
    }

    /** Call doBuild() on the given agent using test settings */
    public static Element callTestDoBuild(boolean isBuildFailure,
                                       final BuildAgentService agent) throws CruiseControlException, RemoteException {
        final Element rootElement = Util.loadConfigFile(TEST_CONFIG_FILE);

        final Element project = (Element) rootElement.getChildren("project").get(0);
        assertEquals("testproject", project.getAttributeValue("name"));

        final Element schedule = (Element) project.getChildren("schedule").get(0);
        final int distributedElementIndex;
        if (isBuildFailure) {
            distributedElementIndex = 0;
        } else {
            distributedElementIndex = 1;
        }
        final Element distributed = ((Element) schedule.getChildren("distributed").get(distributedElementIndex));
        DistributedMasterBuilderTest.addMissingPluginDefaults(distributed);

        final String expectedModuleName;
        if (isBuildFailure) {
            expectedModuleName = "testmodule-fail";
        } else {
            expectedModuleName = "testmodule-success";
        }
        final String moduleName = distributed.getAttributeValue("module");
        assertEquals(expectedModuleName, moduleName);
        final Properties projectProps = new Properties();
        projectProps.put(PropertiesHelper.DISTRIBUTED_MODULE, moduleName);

        final Element antBuilderElement = (Element) distributed.getChildren().get(0);
        DistributedMasterBuilderTest.addMissingPluginDefaults(antBuilderElement);

        // @todo Find way to make this work when JAVA_HOME/bin is NOT on the path
        // @todo Fix to use expanded env.* settings from config file
        if (getANT_HOME() != null) {
            antBuilderElement.setAttribute("anthome", getANT_HOME());
        } else {
            LOG.warn("Unit Test couldn't find ANT_HOME env var. Might work if java/bin is in the path. Here goes...");
        }

        final Element buildResult = agent.doBuild(antBuilderElement, projectProps);
        return buildResult;
    }

    public static String getANT_HOME() {
        return DistributedMasterBuilderTest.OS_ENV.getVariable("ANT_HOME");
    }

    public void testClaim() {
        final BuildAgentServiceImpl agentImpl = new BuildAgentServiceImpl();
        agentImpl.setAgentPropertiesFilename(TEST_AGENT_PROPERTIES_FILE);

        assertFalse(agentImpl.isBusy());
        assertNull(agentImpl.getDateClaimed());
        agentImpl.claim();
        assertTrue(agentImpl.isBusy());
        assertNotNull(agentImpl.getDateClaimed());
        final Date firstClaimDate = agentImpl.getDateClaimed();

        try {
            agentImpl.claim();
            fail("Should throw exception if attempt is made to claim a busy agent");
        } catch (IllegalStateException e) {
            assertTrue("Unexpected error message w/ invalid call to claim(): " + e.getMessage(),
                    e.getMessage().startsWith("Cannot claim agent on "));
            assertTrue("Unexpected error message w/ invalid call to claim(): " + e.getMessage(),
                    e.getMessage().indexOf("that is busy building module: null") > 0);
        }
        assertEquals(firstClaimDate, agentImpl.getDateClaimed());

        // @todo should agent expose a release() method to clear busy flag?
        try {
            agentImpl.doBuild(null, null);
            fail("Should have failed to build");
        } catch (NullPointerException e) {
            assertEquals("Unexpected build error: " + e.getMessage(), null, e.getMessage());
        } catch (RemoteException e) {
            assertTrue("Unexpected build error: " + e.getMessage(),
                    e.getMessage().startsWith("Failed to complete build on agent; nested exception is: \n"
                    + "\tnet.sourceforge.cruisecontrol.CruiseControlException: ant logfile "));
        }
        assertEquals(firstClaimDate, agentImpl.getDateClaimed());

        try {
            agentImpl.clearOutputFiles();
            fail("Expected agent cleanup to fail");
        } catch (NullPointerException e) {
            assertEquals("Unexpected build error: " + e.getMessage(), null, e.getMessage());
        }
        assertTrue("Expected agent busy flag to be true after cleanup failed.", agentImpl.isBusy());
        assertEquals(firstClaimDate, agentImpl.getDateClaimed());

        agentImpl.setBusy(false);
        assertNull(agentImpl.getDateClaimed());
    }
}

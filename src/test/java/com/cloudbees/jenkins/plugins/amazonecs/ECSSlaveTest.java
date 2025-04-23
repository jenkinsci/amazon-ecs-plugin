package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.services.ecs.model.ClientException;
import com.amazonaws.services.ecs.model.Task;
import hudson.model.TaskListener;
import hudson.slaves.JNLPLauncher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WithJenkins
class ECSSlaveTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void terminateRunningTask() throws Exception {
        ECSService ecsService = mock(ECSService.class);
        ECSCloud cloud = mock(ECSCloud.class);
        Mockito.when(cloud.getEcsService()).thenReturn(ecsService);

        TaskListener listener = mock(TaskListener.class);
        ECSTaskTemplate template = getTaskTemplate();

        ECSSlave sut = new ECSSlave(cloud, "myagent", template, new JNLPLauncher());
        sut.setTaskArn("mytaskarn");
        sut.setClusterArn("myclusterarn");
        sut.setNodeName("mynode");

        sut._terminate(listener);

        // Delete the task
        verify(ecsService, times(1)).stopTask("mytaskarn", "myclusterarn");
    }

    @Test
    void terminate_ThrowsException_ignoreException() throws Exception {
        ECSService ecsService = mock(ECSService.class);
        Mockito.doThrow(new ClientException("failed"))
                .when(ecsService).stopTask("mytaskarn", "myclusterarn");

        ECSCloud cloud = mock(ECSCloud.class);
        Mockito.when(cloud.getEcsService()).thenReturn(ecsService);

        TaskListener listener = mock(TaskListener.class);
        ECSTaskTemplate template = getTaskTemplate();

        ECSSlave sut = new ECSSlave(cloud, "myagent", template, new JNLPLauncher());
        sut.setTaskArn("mytaskarn");
        sut.setClusterArn("myclusterarn");
        sut.setNodeName("mynode");

        sut._terminate(listener);

        // Delete the task
        verify(ecsService, times(1)).stopTask("mytaskarn", "myclusterarn");
    }

    private void test_node_is_survivable_with_last_status_and_desired_status(String lastStatus, String desiredStatus, boolean expectedSurvivable) throws Exception {
        ECSService ecsService = mock(ECSService.class);
        ECSCloud cloud = mock(ECSCloud.class);
        Mockito.when(cloud.getEcsService()).thenReturn(ecsService);
        ECSTaskTemplate template = getTaskTemplate();

        ECSSlave sut = new ECSSlave(cloud, "myagent", template, new JNLPLauncher());
        sut.setClusterArn("myclusterarn");
        sut.setTaskArn("mytaskarn");

        Task task = mock(Task.class);
        Mockito.when(task.getLastStatus()).thenReturn(lastStatus);
        Mockito.when(task.getDesiredStatus()).thenReturn(desiredStatus);
        Mockito.when(ecsService.describeTask(sut.getTaskArn(), sut.getClusterArn())).thenReturn(task);

        if (expectedSurvivable) {
            assertTrue(sut.isSurvivable());
        } else {
            assertFalse(sut.isSurvivable());
        }
    }

    @Test
    void node_is_survivable_if_task_is_provisioning_and_desired_status_is_running() throws Exception {
        test_node_is_survivable_with_last_status_and_desired_status("PROVISIONING", "RUNNING", true);
    }

    @Test
    void node_is_survivable_if_task_is_running_and_desired_status_is_running() throws Exception {
        test_node_is_survivable_with_last_status_and_desired_status("RUNNING", "RUNNING", true);
    }

    @Test
    void node_is_not_survivable_if_task_is_stopped_and_desired_status_is_running() throws Exception {
        test_node_is_survivable_with_last_status_and_desired_status("STOPPED", "RUNNING", false);
    }

    @Test
    void node_is_not_survivable_if_task_is_running_and_desired_status_is_stopped() throws Exception {
        test_node_is_survivable_with_last_status_and_desired_status("RUNNING", "STOPPED", false);
    }

    @Test
    void node_is_not_survivable_if_task_is_stopped_and_desired_status_is_stopped() throws Exception {
        test_node_is_survivable_with_last_status_and_desired_status("STOPPED", "STOPPED", false);
    }

    @Test
    void node_is_not_survivable_if_task_cannot_be_found() throws Exception {
        ECSService ecsService = mock(ECSService.class);
        ECSCloud cloud = mock(ECSCloud.class);
        Mockito.when(cloud.getEcsService()).thenReturn(ecsService);
        ECSTaskTemplate template = getTaskTemplate();

        ECSSlave sut = new ECSSlave(cloud, "myagent", template, new JNLPLauncher());
        sut.setClusterArn("myclusterarn");
        sut.setTaskArn("mytaskarn");

        Mockito.when(ecsService.describeTask(sut.getTaskArn(), sut.getClusterArn())).thenReturn(null);

        assertFalse(sut.isSurvivable());
    }

    @Test
    void agent_has_1_executor_as_default() throws Exception {
        ECSService ecsService = mock(ECSService.class);
        ECSCloud cloud = mock(ECSCloud.class);
        Mockito.when(cloud.getEcsService()).thenReturn(ecsService);
        ECSTaskTemplate template = getTaskTemplate();

        ECSSlave sut = new ECSSlave(cloud, "myagent", template, new JNLPLauncher());

        assertEquals(1, sut.getNumExecutors());
    }

    @Test
    void agent_has_4_executors_when_configured() throws Exception {
        ECSService ecsService = mock(ECSService.class);
        ECSCloud cloud = mock(ECSCloud.class);
        Mockito.when(cloud.getEcsService()).thenReturn(ecsService);
        ECSTaskTemplate template = getTaskTemplate();

        when(cloud.getNumExecutors()).thenReturn(4);

        ECSSlave sut = new ECSSlave(cloud, "myagent", template, new JNLPLauncher());

        assertEquals(4, sut.getNumExecutors());
    }

    private ECSTaskTemplate getTaskTemplate() {
        return new ECSTaskTemplate(
                "templateName",
                "label",
                "agentContainerName",
                "taskDefinitionOverride",
                null,
                "image",
                "repositoryCredentials",
                "launchType",
                "operatingSystemFamily",
                "cpuArchitecture",
                false,
                null,
                "networkMode",
                "remoteFSRoot",
                false,
                null,
                0,
                0,
                0,
                null,
                null,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                false);
    }

}

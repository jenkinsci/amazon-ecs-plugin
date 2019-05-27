package com.cloudbees.jenkins.plugins.amazonecs;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import com.amazonaws.services.ecs.model.ClientException;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import hudson.model.TaskListener;
import hudson.slaves.JNLPLauncher;


public class ECSSlaveTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void terminateRunningTask() throws Exception {

        ECSService ecsService = mock(ECSService.class);
        ECSCloud cloud = mock(ECSCloud.class);
        Mockito.when(cloud.getEcsService()).thenReturn(ecsService);

        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        TaskListener listener = mock(TaskListener.class);
        Mockito.when(listener.getLogger()).thenReturn(new PrintStream(bo));
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
    public void terminate_ThrowsException_ignoreException() throws Exception {

        ECSService ecsService = mock(ECSService.class);
        Mockito.doThrow(new ClientException("failed"))
                .when(ecsService).stopTask("mytaskarn", "myclusterarn");

        ECSCloud cloud = mock(ECSCloud.class);
        Mockito.when(cloud.getEcsService()).thenReturn(ecsService);

        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        TaskListener listener = mock(TaskListener.class);
        Mockito.when(listener.getLogger()).thenReturn(new PrintStream(bo));
        ECSTaskTemplate template = getTaskTemplate();

        ECSSlave sut = new ECSSlave(cloud, "myagent", template, new JNLPLauncher());
        sut.setTaskArn("mytaskarn");
        sut.setClusterArn("myclusterarn");
        sut.setNodeName("mynode");

        sut._terminate(listener);

        // Delete the task
        verify(ecsService, times(1)).stopTask("mytaskarn", "myclusterarn");
    }

    private ECSTaskTemplate getTaskTemplate() {
        return new ECSTaskTemplate(
            "templateName",
            "label",
            "taskDefinitionOverride",
            "image",
            "repositoryCredentials", 
            "launchType",
            "networkMode",
            "remoteFSRoot",
            0,
            0,
            0,
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
            null);
    }
}

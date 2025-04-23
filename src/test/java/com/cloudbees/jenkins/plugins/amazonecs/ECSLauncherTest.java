package com.cloudbees.jenkins.plugins.amazonecs;


import com.amazonaws.services.ecs.model.Task;
import com.amazonaws.waiters.WaiterUnrecoverableException;
import hudson.model.TaskListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


@WithJenkins
class ECSLauncherTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void generic_ecs_exception_is_not_retried() throws Exception {
        ECSService ecsService = mock(ECSService.class);
        ECSCloud cloud = mock(ECSCloud.class);
        ECSComputer computer = mock(ECSComputer.class);
        TaskListener listener = mock(TaskListener.class);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        Mockito.when(computer.getNode()).thenReturn(mock(ECSSlave.class));
        Mockito.when(cloud.getEcsService()).thenReturn(ecsService);
        Mockito.when(listener.getLogger()).thenReturn(new PrintStream(bo));

        ECSLauncher launcher = Mockito.spy(new ECSLauncher(cloud, "tunnel", ""));

        doThrow(new WaiterUnrecoverableException("Generic ecs exception")).when(launcher).launchECSTask(any(ECSComputer.class), any(TaskListener.class), anyLong());

        assertThrows(WaiterUnrecoverableException.class, () ->
                launcher.launch(computer, listener), "Generic ECS exception");

        verify(launcher, times(1)).launchECSTask(any(ECSComputer.class), any(TaskListener.class), anyLong());
    }

    @Test
    void eni_timeout_exception_is_retried() throws Exception {
        ECSService ecsService = mock(ECSService.class);
        ECSCloud cloud = mock(ECSCloud.class);
        ECSComputer computer = mock(ECSComputer.class);
        TaskListener listener = mock(TaskListener.class);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        Mockito.when(computer.getNode()).thenReturn(mock(ECSSlave.class));
        Mockito.when(cloud.getEcsService()).thenReturn(ecsService);
        Mockito.when(listener.getLogger()).thenReturn(new PrintStream(bo));

        ECSLauncher launcher = Mockito.spy(new ECSLauncher(cloud, "tunnel", ""));

        doThrow(ECSLauncher.RetryableLaunchFailure.class).doReturn(mock(Task.class)).when(launcher).launchECSTask(any(ECSComputer.class), any(TaskListener.class), anyLong());
        doNothing().when(launcher).waitForAgent(any(ECSSlave.class), any(TaskListener.class), anyLong());

        launcher.launch(computer, listener);

        verify(launcher, times(2)).launchECSTask(any(ECSComputer.class), any(TaskListener.class), anyLong());
    }
}

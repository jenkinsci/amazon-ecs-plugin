package com.cloudbees.jenkins.plugins.amazonecs;

import hudson.slaves.OfflineCause;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;

public class ECSComputerListenerTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void unsurvivable_node_is_terminated_when_computer_is_offline() throws Exception {
        ECSComputer computer = mock(ECSComputer.class);
        OfflineCause cause = mock(OfflineCause.class);
        ECSSlave node = mock(ECSSlave.class);
        Mockito.when(node.isSurvivable()).thenReturn(false);
        Mockito.when(computer.getNode()).thenReturn(node);

        ECSComputerListener listener = ECSComputerListener.getInstance();
        listener.onOffline(computer, cause);

        verify(node, times(1)).terminate();
    }

    @Test
    public void survivable_node_is_not_terminated_when_computer_is_offline() throws Exception {
        ECSComputer computer = mock(ECSComputer.class);
        OfflineCause cause = mock(OfflineCause.class);
        ECSSlave node = mock(ECSSlave.class);
        Mockito.when(node.isSurvivable()).thenReturn(true);
        Mockito.when(computer.getNode()).thenReturn(node);

        ECSComputerListener listener = ECSComputerListener.getInstance();
        listener.onOffline(computer, cause);

        verify(node, never()).terminate();
    }
}

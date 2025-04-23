package com.cloudbees.jenkins.plugins.amazonecs;

import hudson.slaves.OfflineCause;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mockito;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@WithJenkins
class ECSComputerListenerTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void unsurvivable_node_is_terminated_when_computer_is_offline() throws Exception {
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
    void survivable_node_is_not_terminated_when_computer_is_offline() throws Exception {
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

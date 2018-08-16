package com.cloudbees.jenkins.plugins.amazonecs;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.ecs.model.TaskDefinition;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;
import hudson.model.Node;


public class ProvisioningCallbackTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void callbackCall_existingTemplate_runsEcsTask() throws Exception {

        ECSTaskTemplate template = new ECSTaskTemplate(
            "templateName", "template-label",
            null, "image", "EC2", "remoteFSRoot",
            0, 0, 0, null, null, false, false,
            "containerUser", null, null, null, null, null);

        List<ECSTaskTemplate> templates = new ArrayList<ECSTaskTemplate>();
        ECSCloud cloud = new ECSCloud("testcloud", templates, "", "cluster", "regionName", "jenkinsUrl", 0);
        TaskDefinition taskDefinition = new TaskDefinition().withFamily("myfamily");

        ECSService ecs = Mockito.mock(ECSService.class);
        Mockito.when(ecs.registerTemplate(cloud, template)).thenReturn(taskDefinition);
        Mockito.when(ecs.runEcsTask(any(ECSSlave.class), template, anyString(), anyCollection(), taskDefinition)).thenReturn("arn::aws::region::task");

        ProvisioningCallback callback = new ProvisioningCallback(cloud, ecs, template);
        Node node = callback.call();

        assertEquals("template-label", node.getLabelString());
        assertEquals("arn::aws::region::task", ((ECSSlave)node).getTaskArn());
        assertTrue(node.getNodeName().startsWith("testcloud-"));
    }

    @Test
    public void callbackCall_existingTaskDefinitionOverride_runsEcsTask() throws Exception {

        ECSTaskTemplate template = new ECSTaskTemplate(
            "templateName", "template-label",
            "arn::aws::region::taskdefinition", "image", "EC2", "remoteFSRoot",
            0, 0, 0, null, null, false, false,
            "containerUser", null, null, null, null, null);

        List<ECSTaskTemplate> templates = new ArrayList<ECSTaskTemplate>();
        ECSCloud cloud = new ECSCloud("testcloud", templates, "", "cluster", "regionName", "jenkinsUrl", 0);

        ECSService ecs = mock(ECSService.class);
        TaskDefinition taskDefinition = new TaskDefinition().withFamily("myfamily");
        when(ecs.findTaskDefinition("arn::aws::region::taskdefinition")).thenReturn(taskDefinition);

        ProvisioningCallback callback = new ProvisioningCallback(cloud, ecs, template);

        Node node = callback.call();

        assertThat(node, is(instanceOf(ECSSlave.class)));
        assertEquals("template-label", node.getLabelString());
        assertTrue(node.getNodeName().startsWith("testcloud-"));

        // should run task
        verify(ecs).runEcsTask(any(ECSSlave.class), template, anyString(), anyCollection(), any(TaskDefinition.class));
    }

    @Test(expected=RuntimeException.class)
    public void callbackCall_taskDefinitionDoesntExist_fails() throws Exception {

        ECSTaskTemplate template = new ECSTaskTemplate(
            "templateName", "template-label",
            "arn::aws::region::taskdefinition", "image", "EC2", "remoteFSRoot",
            0, 0, 0, null, null, false, false,
            "containerUser", null, null, null, null, null);

        List<ECSTaskTemplate> templates = new ArrayList<ECSTaskTemplate>();
        ECSCloud cloud = new ECSCloud("testcloud", templates, "", "cluster", "regionName", "jenkinsUrl", 0);

        ECSService ecs = mock(ECSService.class);
        when(ecs.findTaskDefinition("arn::aws::region::taskdefinition")).thenReturn(null);

        ProvisioningCallback callback = new ProvisioningCallback(cloud, ecs, template);
        callback.call();
    }
}
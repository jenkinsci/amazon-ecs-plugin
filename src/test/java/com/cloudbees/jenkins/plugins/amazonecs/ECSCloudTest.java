package com.cloudbees.jenkins.plugins.amazonecs;


import com.amazonaws.services.ecs.model.TaskDefinition;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner.PlannedNode;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;


public class ECSCloudTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void provision_oneagent() throws Exception {

        List<ECSTaskTemplate> templates = new ArrayList<>();
        templates.add(getTaskTemplate("my-template","label"));

        ECSCloud sut = new ECSCloud("mycloud", "", "mycluster");
        sut.setTemplates(templates);
        sut.setRegionName("eu-west-1");
        sut.setJenkinsUrl("http://jenkins.local");
        sut.setSlaveTimeoutInSeconds(5);
        sut.setRetentionTimeout(5);
        Collection<PlannedNode> plannedNodes = sut.provision(new LabelAtom("label"), 1);

        Assert.assertEquals(1, plannedNodes.size());
    }

    @Test
    public void canProvision_unknownLabel_returnsFalse() throws Exception {

        List<ECSTaskTemplate> templates = new ArrayList<>();

        ECSCloud sut = new ECSCloud("mycloud", "", "mycluster");
        sut.setTemplates(templates);
        sut.setRegionName("eu-west-1");
        sut.setJenkinsUrl("http://jenkins.local");
        sut.setSlaveTimeoutInSeconds(5);
        sut.setRetentionTimeout(5);

        boolean canProvision = sut.canProvision(new LabelAtom("unknownLabel"));

        Assert.assertFalse(canProvision);
    }

    @Test
    public void testFindParentTemplateWhenNoneSupplied() throws Exception {
        ECSService ecsService = mock(ECSService.class);
        ECSCloud cloud = new ECSCloud("mycloud","mycluster",ecsService);

        cloud.addTemplate(getTaskTemplate());

        ECSTaskTemplate expected = getTaskTemplate("template-name", "template-default");
        cloud.addTemplate(expected);
        ECSTaskTemplate actual = cloud.findParentTemplate(null);
        assertEquals(expected,actual);

        expected = getTaskTemplate("template-default", "");
        cloud.setTemplates(Collections.singletonList(expected));
        cloud.addTemplate(getTaskTemplate());
        actual = cloud.findParentTemplate(null);
        assertEquals(expected,actual);

    }

    @Test
    public void addDynamicTemplateRegistersTemplate() throws Exception {
        ECSService ecsService = mock(ECSService.class);

        ECSCloud cloud = new ECSCloud("mycloud", "mycluster", ecsService);
        ECSTaskTemplate tt = getTaskTemplate();
        TaskDefinition expectedTask = new TaskDefinition();
        expectedTask.setTaskDefinitionArn(UUID.randomUUID().toString());
        String expected = expectedTask.getTaskDefinitionArn();

        when(ecsService.registerTemplate(cloud.getDisplayName(), tt)).thenReturn(expectedTask);

        String actual = cloud.addDynamicTemplate(tt).getDynamicTaskDefinition();
        assertEquals(expected,actual);
    }

    @Test
    public void provisionByLabelInheritFromUsingListOfLabels() throws Exception {
        ECSCloud            cloud    = new ECSCloud("mycloud", "", "mycluster");
        ECSTaskTemplate expected = getTaskTemplate("somename","label1 label2 label3");

        List<ECSTaskTemplate> currentTemplates = cloud.getTemplates();
        List<ECSTaskTemplate> newTemplates = new LinkedList<>(currentTemplates);
        newTemplates.add(expected);
        cloud.setTemplates(newTemplates);
        assertTrue(cloud.canProvision("label2"));
    }

    @Test
    public void removeJunkTemplateProducesNoError() throws Exception {
        ECSService ecsService = mock(ECSService.class);
        when(ecsService.findTaskDefinition(anyString())).thenReturn(null);
        ECSCloud cloud = new ECSCloud("mycloud", "mycluster",ecsService);
        cloud.setRegionName("us-east-1");
        cloud.removeDynamicTemplate(getTaskTemplate(Math.random() + "", "label1, label2, label3"));
    }

    @Test
    public void isAllowedOverride_empty_returnsFalse() throws Exception {

        ECSCloud sut = new ECSCloud("mycloud", "", "mycluster");

        Assert.assertFalse(sut.isAllowedOverride("label"));
    }

    @Test
    public void isAllowedOverride_label_returnsTrue() throws Exception {

        ECSCloud sut = new ECSCloud("mycloud", "", "mycluster");
        sut.setAllowedOverrides("label");

        Assert.assertTrue(sut.isAllowedOverride("label"));
    }

    private ECSTaskTemplate getTaskTemplate() {
        return getTaskTemplate(UUID.randomUUID().toString(),UUID.randomUUID().toString());
    }
    private ECSTaskTemplate getTaskTemplate(String templateName, String label) {
        return new ECSTaskTemplate(
                templateName,
                label,
                "",
                null,
                "image",
                "repositoryCredentials",
                "launchType",
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
                0);
    }
}

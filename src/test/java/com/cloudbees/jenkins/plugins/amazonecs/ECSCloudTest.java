package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.services.ecs.model.TaskDefinition;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner.PlannedNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WithJenkins
class ECSCloudTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void provision_oneagent() {
        List<ECSTaskTemplate> templates = new ArrayList<>();
        templates.add(getTaskTemplate("my-template", "label"));

        ECSCloud sut = new ECSCloud("mycloud", "", "", "mycluster");
        sut.setTemplates(templates);
        sut.setRegionName("eu-west-1");
        sut.setNumExecutors(1);
        sut.setJenkinsUrl("http://jenkins.local");
        sut.setSlaveTimeoutInSeconds(5);
        sut.setRetentionTimeout(5);
        Collection<PlannedNode> plannedNodes = sut.provision(new LabelAtom("label"), 1);

        assertEquals(1, plannedNodes.size());
    }

    @Test
    void canProvision_unknownLabel_returnsFalse() {
        List<ECSTaskTemplate> templates = new ArrayList<>();

        ECSCloud sut = new ECSCloud("mycloud", "", "", "mycluster");
        sut.setTemplates(templates);
        sut.setRegionName("eu-west-1");
        sut.setNumExecutors(1);
        sut.setJenkinsUrl("http://jenkins.local");
        sut.setSlaveTimeoutInSeconds(5);
        sut.setRetentionTimeout(5);

        boolean canProvision = sut.canProvision(new LabelAtom("unknownLabel"));

        assertFalse(canProvision);
    }

    @Test
    void testFindParentTemplateWhenNoneSupplied() {
        ECSService ecsService = mock(ECSService.class);
        ECSCloud cloud = new ECSCloud("mycloud", "mycluster", ecsService);

        cloud.addTemplate(getTaskTemplate());

        ECSTaskTemplate expected = getTaskTemplate("template-name", "template-default");
        cloud.addTemplate(expected);
        ECSTaskTemplate actual = cloud.findParentTemplate(null);
        assertEquals(expected, actual);

        expected = getTaskTemplate("template-default", "");
        cloud.setTemplates(Collections.singletonList(expected));
        cloud.addTemplate(getTaskTemplate());
        actual = cloud.findParentTemplate(null);
        assertEquals(expected, actual);

    }

    @Test
    void addDynamicTemplateRegistersTemplate() {
        ECSService ecsService = mock(ECSService.class);

        ECSCloud cloud = new ECSCloud("mycloud", "mycluster", ecsService);
        ECSTaskTemplate tt = getTaskTemplate();
        TaskDefinition expectedTask = new TaskDefinition();
        expectedTask.setTaskDefinitionArn(UUID.randomUUID().toString());
        String expected = expectedTask.getTaskDefinitionArn();

        when(ecsService.registerTemplate(cloud.getDisplayName(), tt)).thenReturn(expectedTask);

        String actual = cloud.addDynamicTemplate(tt).getDynamicTaskDefinition();
        assertEquals(expected, actual);
    }

    @Test
    void provisionByLabelInheritFromUsingListOfLabels() {
        ECSCloud cloud = new ECSCloud("mycloud", "", "", "mycluster");
        ECSTaskTemplate expected = getTaskTemplate("somename", "label1 label2 label3");

        List<ECSTaskTemplate> currentTemplates = cloud.getTemplates();
        List<ECSTaskTemplate> newTemplates = new LinkedList<>(currentTemplates);
        newTemplates.add(expected);
        cloud.setTemplates(newTemplates);
        assertTrue(cloud.canProvision("label2"));
    }

    @Test
    void removeJunkTemplateProducesNoError() {
        ECSService ecsService = mock(ECSService.class);
        ECSCloud cloud = new ECSCloud("mycloud", "mycluster", ecsService);
        cloud.setRegionName("us-east-1");
        cloud.removeDynamicTemplate(getTaskTemplate(Math.random() + "", "label1, label2, label3"));
    }

    @Test
    void isAllowedOverride_empty_returnsFalse() {
        ECSCloud sut = new ECSCloud("mycloud", "", "", "mycluster");

        assertFalse(sut.isAllowedOverride("label"));
    }

    @Test
    void isAllowedOverride_label_returnsTrue() {
        ECSCloud sut = new ECSCloud("mycloud", "", "", "mycluster");
        sut.setAllowedOverrides("label");

        assertTrue(sut.isAllowedOverride("label"));
    }

    @Test
    void getProvisioningCapacity_returnsZeroWhenMaxAgentsReached() {
        int onlineExecutors = 4;
        int connectingExecutors = 1;
        int excessWorkload = 5;

        List<ECSTaskTemplate> templates = new ArrayList<>();
        templates.add(getTaskTemplate("my-template", "label"));

        ECSCloud sut = new ECSCloud("mycloud", "", "", "mycluster");
        sut.setMaxAgents(5);
        sut.setTemplates(templates);
        sut.setRegionName("eu-west-1");
        sut.setNumExecutors(1);
        sut.setJenkinsUrl("http://jenkins.local");
        sut.setSlaveTimeoutInSeconds(5);
        sut.setRetentionTimeout(5);

        int provisioningCapacity = sut.getProvisioningCapacity(excessWorkload, onlineExecutors, connectingExecutors);
        assertEquals(0, provisioningCapacity);
    }

    @Test
    void getProvisioningCapacity_returnsRemainingMaxAgentsWhenWorkloadExceedsAvailability() {
        int onlineExecutors = 7;
        int connectingExecutors = 4;
        int excessWorkload = 8;

        List<ECSTaskTemplate> templates = new ArrayList<>();
        templates.add(getTaskTemplate("my-template", "label"));

        ECSCloud sut = new ECSCloud("mycloud", "", "", "mycluster");
        sut.setMaxAgents(14);
        sut.setTemplates(templates);
        sut.setRegionName("eu-west-1");
        sut.setNumExecutors(1);
        sut.setJenkinsUrl("http://jenkins.local");
        sut.setSlaveTimeoutInSeconds(5);
        sut.setRetentionTimeout(5);

        int provisioningCapacity = sut.getProvisioningCapacity(excessWorkload, onlineExecutors, connectingExecutors);
        assertEquals(3, provisioningCapacity);
    }

    @Test
    void getProvisioningCapacity_returnsExcessWorkloadWithoutMaxAgents() {
        int onlineExecutors = 6;
        int connectingExecutors = 2;
        int excessWorkload = 10;

        List<ECSTaskTemplate> templates = new ArrayList<>();
        templates.add(getTaskTemplate("my-template", "label"));

        ECSCloud sut = new ECSCloud("mycloud", "", "", "mycluster");
        sut.setMaxAgents(0);
        sut.setTemplates(templates);
        sut.setRegionName("eu-west-1");
        sut.setJenkinsUrl("http://jenkins.local");
        sut.setSlaveTimeoutInSeconds(5);
        sut.setRetentionTimeout(5);

        int provisioningCapacity = sut.getProvisioningCapacity(excessWorkload, onlineExecutors, connectingExecutors);
        assertEquals(10, provisioningCapacity);
    }

    @Test
    void getProvisioningCapacity_returnsExcessWorkloadWhenWorkloadDoesNotExceedAvailability() {
        int onlineExecutors = 3;
        int connectingExecutors = 2;
        int excessWorkload = 4;

        List<ECSTaskTemplate> templates = new ArrayList<>();
        templates.add(getTaskTemplate("my-template", "label"));

        ECSCloud sut = new ECSCloud("mycloud", "", "", "mycluster");
        sut.setMaxAgents(10);
        sut.setTemplates(templates);
        sut.setRegionName("eu-west-1");
        sut.setJenkinsUrl("http://jenkins.local");
        sut.setSlaveTimeoutInSeconds(5);
        sut.setRetentionTimeout(5);

        int provisioningCapacity = sut.getProvisioningCapacity(excessWorkload, onlineExecutors, connectingExecutors);
        assertEquals(4, provisioningCapacity);
    }

    @Test
    void getProvisioningCapacity_returnsZeroWhenOverflowEncountered() {
        int onlineExecutors = Integer.MAX_VALUE;
        int connectingExecutors = 1;
        int excessWorkload = 1;

        List<ECSTaskTemplate> templates = new ArrayList<>();
        templates.add(getTaskTemplate("my-template", "label"));

        ECSCloud sut = new ECSCloud("mycloud", "", "", "mycluster");
        sut.setMaxAgents(10);
        sut.setTemplates(templates);
        sut.setRegionName("eu-west-1");
        sut.setJenkinsUrl("http://jenkins.local");
        sut.setSlaveTimeoutInSeconds(5);
        sut.setRetentionTimeout(5);

        int provisioningCapacity = sut.getProvisioningCapacity(excessWorkload, onlineExecutors, connectingExecutors);
        assertEquals(0, provisioningCapacity);
    }

    @Test
    void getProvisioningCapacity_returnsZeroWhenCurrentAgentsGreaterThanMaxAgents() {
        int onlineExecutors = 5;
        int connectingExecutors = 5;
        int excessWorkload = 1;

        List<ECSTaskTemplate> templates = new ArrayList<>();
        templates.add(getTaskTemplate("my-template", "label"));

        ECSCloud sut = new ECSCloud("mycloud", "", "", "mycluster");
        sut.setMaxAgents(1);
        sut.setTemplates(templates);
        sut.setRegionName("eu-west-1");
        sut.setJenkinsUrl("http://jenkins.local");
        sut.setSlaveTimeoutInSeconds(5);
        sut.setRetentionTimeout(5);

        int provisioningCapacity = sut.getProvisioningCapacity(excessWorkload, onlineExecutors, connectingExecutors);
        assertEquals(0, provisioningCapacity);
    }

    private ECSTaskTemplate getTaskTemplate() {
        return getTaskTemplate(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    private ECSTaskTemplate getTaskTemplate(String templateName, String label) {
        return new ECSTaskTemplate(
                templateName,
                label,
                "",
                "",
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

package com.cloudbees.jenkins.plugins.amazonecs;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.amazonaws.services.ecs.model.ClientException;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import hudson.model.Label;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.NodeProvisioner.PlannedNode;


public class ECSCloudTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void provision_oneagent() throws Exception {

        List<ECSTaskTemplate> templates = new ArrayList<ECSTaskTemplate>();
        templates.add(getTaskTemplate());

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

        List<ECSTaskTemplate> templates = new ArrayList<ECSTaskTemplate>();

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

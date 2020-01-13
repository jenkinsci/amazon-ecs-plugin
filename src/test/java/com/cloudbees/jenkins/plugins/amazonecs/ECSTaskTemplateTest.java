package com.cloudbees.jenkins.plugins.amazonecs;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import org.apache.commons.lang.builder.EqualsBuilder;

public class ECSTaskTemplateTest {

    @Test
    public void shouldMerge() throws Exception {

        ECSTaskTemplate child = new ECSTaskTemplate(
            "child-name", "child-label",
            null, "child-image", "child-repository-credentials", "EC2", "child-network-mode", "child-remoteFSRoot",
            false, 0, 0, 0, null, null, false, false,
            "child-containerUser", null, null, null, null, null, null, null, null, "parent", 0);

        ECSTaskTemplate parent = new ECSTaskTemplate(
            "parent-name", "parent-label",
            null, "parent-image", "parent-repository-credentials", "FARGATE", "parent-network-mode", "parent-remoteFSRoot",
            false, 0, 0, 0, null, null, false, false,
            "parent-containerUser", null, null, null, null, null, null, null, null, null, 0);

        ECSTaskTemplate expected = new ECSTaskTemplate(
            "child-name", "child-label",
            null, "child-image", "child-repository-credentials", "EC2", "child-network-mode", "child-remoteFSRoot",
            false, 0, 0, 0, null, null, false, false,
            "child-containerUser", null, null, null, null, null, null, null, null, null, 0);


        ECSTaskTemplate result = child.merge(parent);
        assertTrue(EqualsBuilder.reflectionEquals(expected, result));
    }

    @Test
    public void shouldReturnSettingsFromParent() throws Exception {

        ECSTaskTemplate child = new ECSTaskTemplate(
            "child-name", "child-label",
            null, null, "child-repository-credentials", "EC2", "child-network-mode", "child-remoteFSRoot", // image is set to null
            false, 0, 0, 0, null, null, false, false,
            "child-containerUser", null, null, null, null, null, null, null, null, "parent", 0);

        ECSTaskTemplate parent = new ECSTaskTemplate(
            "parent-name", "parent-label",
            null, "parent-image", "parent-repository-credentials", "FARGATE", "parent-network-mode", "parent-remoteFSRoot",
            false, 0, 0, 0, null, null, false, false,
            "parent-containerUser", null, null, null, null, null, null, null, null, null, 0);

        ECSTaskTemplate expected = new ECSTaskTemplate(
            "child-name", "child-label",
            null, "parent-image", "child-repository-credentials", "EC2", "child-network-mode", "child-remoteFSRoot",
            false, 0, 0, 0, null, null, false, false,
            "child-containerUser", null, null, null, null, null, null, null, null, null, 0);

        ECSTaskTemplate result = child.merge(parent);

        assertTrue(EqualsBuilder.reflectionEquals(expected, result));
    }

    @Test
    public void shouldReturnChildIfNoParent() throws Exception {

        ECSTaskTemplate child = new ECSTaskTemplate(
            "child-name", "child-label",
            null, "child-image", "child-repository-credentials", "EC2", "child-network-mode", "child-remoteFSRoot",
            false, 0, 0, 0, null, null, false, false,
            "child-containerUser", null, null, null, null, null, null, null, null, null, 0); // inheritFrom is null

        ECSTaskTemplate expected = new ECSTaskTemplate(
            "child-name", "child-label",
            null, "child-image", "child-repository-credentials", "EC2", "child-network-mode", "child-remoteFSRoot",
            false, 0, 0, 0, null, null, false, false,
            "child-containerUser", null, null, null, null, null, null, null, null, null, 0);

        ECSTaskTemplate result = child.merge(null);

        assertTrue(EqualsBuilder.reflectionEquals(expected, result));
    }
}

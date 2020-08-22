package com.cloudbees.jenkins.plugins.amazonecs;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import org.apache.commons.lang.builder.EqualsBuilder;

public class ECSTaskTemplateTest {

    ECSTaskTemplate getParent() {
        return new ECSTaskTemplate(
                "parent-name", "parent-label",
                null, null, "parent-image", "parent-repository-credentials", "FARGATE", false, null, "parent-network-mode", "parent-remoteFSRoot",
                false, null, 0, 0, 0, null, null, false, false,
                "parent-containerUser", null, null, null, null, null, null, null, null, null, 0);
    }

    ECSTaskTemplate getChild(String parent) {
        return new ECSTaskTemplate(
                "child-name", "child-label",
                null, null, "child-image", "child-repository-credentials", "EC2", false, null, "child-network-mode", "child-remoteFSRoot",
                false, null, 0, 0, 0, null, null, false, false,
                "child-containerUser", null, null, null, null, null, null, null, null, parent, 0);
    }

    @Test
    public void shouldMerge() throws Exception {

        ECSTaskTemplate parent = getParent();
        ECSTaskTemplate child = getChild("parent");

        ECSTaskTemplate expected = new ECSTaskTemplate(
            "child-name", "child-label",
            null, null, "child-image", "child-repository-credentials", "EC2", false, null, "child-network-mode", "child-remoteFSRoot",
            false, null, 0, 0, 0, null, null, false, false,
            "child-containerUser", null, null, null, null, null, null, null, null, null, 0);


        ECSTaskTemplate result = child.merge(parent);
        assertTrue(EqualsBuilder.reflectionEquals(expected, result));
    }

    @Test
    public void shouldReturnSettingsFromParent() throws Exception {

        ECSTaskTemplate parent = getParent();
        ECSTaskTemplate child = getChild("parent");

        ECSTaskTemplate expected = new ECSTaskTemplate(
            "child-name", "child-label",
            null, null, "child-image", "child-repository-credentials", "EC2", false, null, "child-network-mode", "child-remoteFSRoot",
            false, null, 0, 0, 0, null, null, false, false,
            "child-containerUser", null, null, null, null, null, null, null, null, null, 0);

        ECSTaskTemplate result = child.merge(parent);

        assertEquals(expected,result);
    }

    @Test
    public void shouldReturnChildIfNoParent() throws Exception {

        ECSTaskTemplate child = getChild(null);

        ECSTaskTemplate expected = new ECSTaskTemplate(
            "child-name", "child-label",
            null, null, "child-image", "child-repository-credentials", "EC2", false, null, "child-network-mode", "child-remoteFSRoot",
            false, null, 0, 0, 0, null, null, false, false,
            "child-containerUser", null, null, null, null, null, null, null, null, null, 0);

        ECSTaskTemplate result = child.merge(null);

        assertEquals(expected,result);
    }

    @Test
    public void shouldOverrideEntrypoint() {
        String entrypoint = "/bin/bash";

        ECSTaskTemplate parent = getParent();
        ECSTaskTemplate child = getChild("parent");

        ECSTaskTemplate expected = new ECSTaskTemplate(
                "child-name", "child-label",
                null, null, "child-image", "child-repository-credentials", "EC2", false, null, "child-network-mode", "child-remoteFSRoot",
                false, null, 0, 0, 0, null, null, false, false,
                "child-containerUser", null, null, null, null, null, null, null, null, null, 0);

        //Child entrypoint should equal to parent by default
        parent.setEntrypoint(entrypoint);
        expected.setEntrypoint(entrypoint);
        assertTrue(EqualsBuilder.reflectionEquals(expected, child.merge(parent)));

        //Forced empty entrypoint on child should take precedence
        parent.setEntrypoint(entrypoint);
        child.setEntrypoint("/bin/false");
        expected.setEntrypoint("/bin/false");
        assertTrue(EqualsBuilder.reflectionEquals(expected, child.merge(parent)));
    }
}

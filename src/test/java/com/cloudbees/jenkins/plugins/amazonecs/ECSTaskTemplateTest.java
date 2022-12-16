package com.cloudbees.jenkins.plugins.amazonecs;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import org.apache.commons.lang.builder.EqualsBuilder;

import java.util.ArrayList;

public class ECSTaskTemplateTest {

    ECSTaskTemplate getParent() {
        return new ECSTaskTemplate(
                "parent-name", "parent-label",
                null, null, null, "parent-image", "parent-repository-credentials", "FARGATE", "LINUX", "X86_64",false, null, "parent-network-mode", "parent-remoteFSRoot",
                false, null, 0, 0, 0, null, null, null, false, false,
                "parent-containerUser", "parent-kernelCapabilities", null, new ArrayList<>(), null, null, null, null, null, null, null, null, null, 0, false);
    }

    ECSTaskTemplate getChild(String parent) {
        return new ECSTaskTemplate(
                "child-name", "child-label",
                null, null, null, "child-image", "child-repository-credentials", "EC2", "LINUX", "X86_64",false, null, "child-network-mode", "child-remoteFSRoot",
                false, null, 0, 0, 0, null, null, null, false, false,
                "child-containerUser", "child-kernelCapabilities", null, new ArrayList<>(), null, null, null, null, null, null, null, null, parent, 0, false);
    }

    @Test
    public void shouldMerge() throws Exception {

        ECSTaskTemplate parent = getParent();
        ECSTaskTemplate child = getChild("parent");

        ECSTaskTemplate expected = new ECSTaskTemplate(
            "child-name", "child-label",
            null, null, null, "child-image", "child-repository-credentials", "EC2", "LINUX", "X86_64",false, null, "child-network-mode", "child-remoteFSRoot",
            false, null, 0, 0, 0, null, null, null, false, false,
            "child-containerUser", "child-kernelCapabilities", null, new ArrayList<>(), null, null, null, null, null, null, null, null, null, 0, false);


        ECSTaskTemplate result = child.merge(parent);
        assertTrue(EqualsBuilder.reflectionEquals(expected, result));
    }

    @Test
    public void shouldReturnSettingsFromParent() throws Exception {

        ECSTaskTemplate parent = getParent();
        ECSTaskTemplate child = getChild("parent");

        ECSTaskTemplate expected = new ECSTaskTemplate(
            "child-name", "child-label",
            null, null, null, "child-image", "child-repository-credentials", "EC2", "LINUX", "X86_64",false, null, "child-network-mode", "child-remoteFSRoot",
            false, null, 0, 0, 0, null, null, null, false, false,
            "child-containerUser", "child-kernelCapabilities", null, new ArrayList<>(), null, null, null, null, null, null, null, null, null, 0, false);

        ECSTaskTemplate result = child.merge(parent);

        assertEquals(expected,result);
    }

    @Test
    public void shouldReturnChildIfNoParent() throws Exception {

        ECSTaskTemplate child = getChild(null);

        ECSTaskTemplate expected = new ECSTaskTemplate(
            "child-name", "child-label",
            null, null, null, "child-image", "child-repository-credentials", "EC2", "LINUX", "X86_64",false, null, "child-network-mode", "child-remoteFSRoot",
            false, null, 0, 0, 0, null, null, null, false, false,
            "child-containerUser", "child-kernelCapabilities", null, new ArrayList<>(), null, null, null, null, null, null, null, null, null, 0, false);

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
                null, null, null, "child-image", "child-repository-credentials", "EC2", "LINUX", "X86_64",false, null, "child-network-mode", "child-remoteFSRoot",
                false, null, 0, 0, 0, null, null, null, false, false,
                "child-containerUser", "child-kernelCapabilities", null, new ArrayList<>(), null, null, null, null, null, null, null, null, null, 0, false);

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

/*
 * The MIT License
 *
 *  Copyright (c) 2015, CloudBees, Inc.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 */

package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.KeyValuePair;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import jenkins.model.JenkinsLocationConfiguration;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Collection;
import java.util.Set;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ECSTaskTemplate extends AbstractDescribableImpl<ECSTaskTemplate> {

    private final String label;
    private final String image;
    private final String remoteFSRoot;
    private final int memory;
    private final int cpu;

    private String entrypoint;
    private String jvmArgs;

    @DataBoundConstructor
    public ECSTaskTemplate(String label, String image, String remoteFSRoot, int memory, int cpu) {
        this.label = label;
        this.image = image;
        this.remoteFSRoot = remoteFSRoot;
        this.memory = memory;
        this.cpu = cpu;
    }

    @DataBoundSetter
    public void setEntrypoint(String entrypoint) {
        this.entrypoint = entrypoint;
    }

    @DataBoundSetter
    public void setJvmArgs(String jvmArgs) {
        this.jvmArgs = jvmArgs;
    }

    public String getLabel() {
        return label;
    }

    public String getImage() {
        return image;
    }

    public String getRemoteFSRoot() {
        return remoteFSRoot;
    }

    public int getMemory() {
        return memory;
    }

    public int getCpu() {
        return cpu;
    }

    public String getEntrypoint() {
        return entrypoint;
    }

    public String getJvmArgs() {
        return jvmArgs;
    }

    public Set<LabelAtom> getLabelSet() {
        return Label.parse(label);
    }

    public String getDisplayName() {
        return "ECS Slave " + label;
    }

    public RegisterTaskDefinitionRequest asRegisterTaskDefinitionRequest(Collection<String> command) {
        final String[] o = entrypoint != null ? entrypoint.split(" ") : new String[0];
        return new RegisterTaskDefinitionRequest()
            .withFamily("jenkins-slave")
            .withContainerDefinitions(new ContainerDefinition()
                    .withName("jenkins-slave")
                    .withImage(image)
                    .withMemory(memory)
                    .withCpu(cpu)
                    .withEntryPoint(o)
                    .withCommand(command)
                    .withEnvironment(new KeyValuePair()
                            .withName("JAVA_OPTS").withValue(jvmArgs))
                    .withEssential(true));
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ECSTaskTemplate> {

        @Override
        public String getDisplayName() {

            return Messages.Template();
        }
    }
}

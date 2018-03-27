package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.services.ecs.model.ContainerDefinition;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Set;

public class ECSTaskDefinition extends AbstractDescribableImpl<ECSTaskDefinition> implements ECSTask {

    /**
     * Task definition
     */
    @Nonnull
    private final String taskDefinitionName;

    @Nonnull
    private final String jenkinsContainerName;
    /**
     * White-space separated list of {@link hudson.model.Node} labels.
     *
     * @see Label
     */
    @CheckForNull
    private final String label;

    @DataBoundConstructor
    public ECSTaskDefinition(@Nonnull String taskDefinitionName,
                             @Nonnull String jenkinsContainerName, @Nullable String label, int memory,
                             int memoryReservation,
                             int cpu, @Nullable String remoteFSRoot) {
        this.taskDefinitionName = taskDefinitionName;
        this.jenkinsContainerName = jenkinsContainerName;
        this.label = label;
        this.memory = memory;
        this.memoryReservation = memoryReservation;
        this.cpu = cpu;
        this.remoteFSRoot = remoteFSRoot;
    }

    /**
     * The number of MiB of memory reserved for the Docker container. If your
     * container attempts to exceed the memory allocated here, the container
     * is killed by ECS.
     *
     * @see ContainerDefinition#withMemory(Integer)
     */
    private final int memory;
    /**
     * The soft limit (in MiB) of memory to reserve for the container. When
     * system memory is under contention, Docker attempts to keep the container
     * memory to this soft limit; however, your container can consume more
     * memory when it needs to, up to either the hard limit specified with the
     * memory parameter (if applicable), or all of the available memory on the
     * container instance, whichever comes first.
     *
     * @see ContainerDefinition#withMemoryReservation(Integer)
     */
    private final int memoryReservation;

    /* a hint to ECSService regarding whether it can ask AWS to make a new container or not */
    public int getMemoryConstraint() {
        if (this.memoryReservation > 0) {
            return this.memoryReservation;
        }
        return this.memory;
    }

    /**
     * The number of <code>cpu</code> units reserved for the container. A
     * container instance has 1,024 <code>cpu</code> units for every CPU
     * core. This parameter specifies the minimum amount of CPU to reserve
     * for a container, and containers share unallocated CPU units with other
     * containers on the instance with the same ratio as their allocated
     * amount.
     *
     * @see ContainerDefinition#withCpu(Integer)
     */
    private final int cpu;

    /**
     * Slave remote FS
     */
    @Nullable
    private final String remoteFSRoot;


    public int getMemory() {
        return memory;
    }

    public int getMemoryReservation() {
        return memoryReservation;
    }

    public int getCpu() {
        return cpu;
    }


    public String getLabel() {
        return label;
    }

    public Set<LabelAtom> getLabelSet() {
        return Label.parse(label);
    }

    public String getDisplayName() {
        return "ECS Slave " + label;
    }

    public String getRemoteFSRoot() {
        return remoteFSRoot;
    }

    @Nonnull
    public String getTaskDefinitionName() {
        return taskDefinitionName;
    }

    @Nonnull
    public String getJenkinsContainerName() {
        return jenkinsContainerName;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ECSTaskDefinition> {

        private static String TASK_DEFINITION_NAME_PATTERN = "[a-z|A-Z|0-9|_|-]{1,127}";

        @Override
        public String getDisplayName() {
            return Messages.Template();
        }

        public FormValidation doCheckTaskDefinitionName(@QueryParameter String value) throws IOException, ServletException {
            if (value.length() > 0 && value.length() <= 127 && value.matches(TASK_DEFINITION_NAME_PATTERN)) {
                return FormValidation.ok();
            }
            return FormValidation.error("Up to 127 letters (uppercase and lowercase), numbers, hyphens, and underscores are allowed");
        }
    }
}

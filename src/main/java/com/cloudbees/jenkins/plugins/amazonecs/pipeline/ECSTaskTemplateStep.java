package com.cloudbees.jenkins.plugins.amazonecs.pipeline;

import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import java.io.Serializable;
import java.util.Set;
import com.google.common.collect.ImmutableSet;
import hudson.model.Run;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ECSTaskTemplateStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(ECSTaskTemplateStep.class.getName());

    private final String DEFAULT_CLOUD = "cloud-default";
    private final String label;
    private final String name;
    private String cloud = DEFAULT_CLOUD;
    private String taskDefinitionOverride;
    private String repositoryCredentials;
    private String image;
    private String launchType;
    private String networkMode;
    private String remoteFSRoot;
    private int memory;
    private int memoryReservation;
    private int cpu;
    private String subnets;
    private String securityGroups;
    private boolean assignPublicIp;
    private boolean privileged;
    private String containerUser;
    private String taskrole;
    private String inheritFrom;

    @DataBoundConstructor
    public ECSTaskTemplateStep(String label, String name) {

        this.label = label;
        this.name = name == null ? "jenkins-agent" : name;
    }

    public String getLabel() {
        return label;
    }

    public String getName() {
        return name;
    }

    @DataBoundSetter
    public void setCloud(String cloud) {
        this.cloud = cloud;
    }

    public String getCloud() {
        return cloud;
    }

    @DataBoundSetter
    public void setTaskDefinitionOverride(String taskDefinitionOverride) {
        this.taskDefinitionOverride = taskDefinitionOverride;
    }

    public String getTaskDefinitionOverride() {
        return taskDefinitionOverride;
    }

    @DataBoundSetter
    public void setRepositoryCredentials(String repositoryCredentials) {
        this.repositoryCredentials = repositoryCredentials;
    }

    public String getRepositoryCredentials() {
        return repositoryCredentials;
    }

    @DataBoundSetter
    public void setImage(String image) {
        this.image = image;
    }

    public String getImage() {
        return image;
    }

    @DataBoundSetter
    public void setLaunchType(String launchType) {
        this.launchType = launchType;
    }

    public String getLaunchType() {
        return launchType;
    }

    @DataBoundSetter
    public void setNetworkMode(String networkMode) {
        this.networkMode = networkMode;
    }

    public String getNetworkMode() {
        return networkMode;
    }

    @DataBoundSetter
    public void setRemoteFSRoot(String remoteFSRoot) {
        this.remoteFSRoot = remoteFSRoot;
    }

    public String getRemoteFSRoot() {
        return remoteFSRoot;
    }

    @DataBoundSetter
    public void setMemory(int memory) {
        this.memory = memory;
    }

    public int getMemory() {
        return memory;
    }

    @DataBoundSetter
    public void setMemoryReservation(int memoryReservation) {
        this.memoryReservation = memoryReservation;
    }

    public int getMemoryReservation() {
        return memoryReservation;
    }

    @DataBoundSetter
    public void setCpu(int cpu) {
        this.cpu = cpu;
    }

    public int getCpu() {
        return cpu;
    }

    @DataBoundSetter
    public void setSubnets(String subnets) {
        this.subnets = subnets;
    }

    public String getSubnets() {
        return subnets;
    }

    @DataBoundSetter
    public void setSecurityGroups(String securityGroups) {
        this.securityGroups = securityGroups;
    }

    public String getSecurityGroups() {
        return securityGroups;
    }

    @DataBoundSetter
    public void setAssignPublicIp(boolean assignPublicIp) {
        this.assignPublicIp = assignPublicIp;
    }

    public boolean getAssignPublicIp() {
        return assignPublicIp;
    }

    @DataBoundSetter
    public void setPrivileged(boolean privileged) {
        this.privileged = privileged;
    }

    public boolean getPrivileged() {
        return privileged;
    }

    @DataBoundSetter
    public void setContainerUser(String containerUser) {
        this.containerUser = containerUser;
    }

    public String getContainerUser() {
        return containerUser;
    }

    @DataBoundSetter
    public void setTaskrole(String taskrole) {
        this.taskrole = taskrole;
    }

    public String getTaskrole() {
        return taskrole;
    }

    @DataBoundSetter
    public void setInheritFrom(String inheritFrom) {
        this.inheritFrom = inheritFrom;
    }

    public String getInheritFrom() {
        return inheritFrom;
    }

    @Override
    public StepExecution start(StepContext stepContext) throws Exception {
        LOGGER.log(Level.FINE, "In ECSTaskTemplateStep start. label: {0}", label);
        LOGGER.log(Level.FINE, "In ECSTaskTemplateStep start. cloud: {0}", cloud);
        return new ECSTaskTemplateStepExecution(this, stepContext);
    }

    @Extension(optional = true)
    public static final class DescriptorImpl extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "taskTemplate";
        }
        @Override
        public String getDisplayName() {
            return "Cloud instances provisioning for declarative pipeline";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, TaskListener.class);
        }
    }

    @Override
    public String toString() {
        return "Step options: " +
                "ecs{" + '\n' +
                "cloud='" + cloud + '\'' + '\n' +
                "image='" + image + '\'' + '\n' +
                "taskDefinitionOverride='" + taskDefinitionOverride + '\'' + '\n' +
                "launchType='" + launchType + '\'' + '\n' +
                "networkMode='" + networkMode + '\'' + '\n' +
                "remoteFSRoot='" + remoteFSRoot + '\'' + '\n' +
                "memory='" + memory + '\'' + '\n' +
                "memoryReservation='" + memoryReservation + '\'' + '\n' +
                "cpu='" + cpu + '\'' + '\n' +
                "subnets='" + subnets + '\'' + '\n' +
                "securityGroups='" + securityGroups + '\'' + '\n' +
                "assignPublicIp='" + assignPublicIp + '\'' + '\n' +
                "privileged='" + privileged + '\'' + '\n' +
                "containerUser='" + containerUser + '\'' + '\n' +
                "taskrole='" + taskrole + '\'' + '\n' +
                "inheritFrom='" + inheritFrom + '\'' + '\n' +
                '}';
    }
}

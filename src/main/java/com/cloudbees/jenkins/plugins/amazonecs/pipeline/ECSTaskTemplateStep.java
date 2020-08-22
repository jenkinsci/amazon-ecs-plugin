package com.cloudbees.jenkins.plugins.amazonecs.pipeline;

import com.cloudbees.jenkins.plugins.amazonecs.SerializableSupplier;
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.amazonaws.services.ecs.model.LaunchType;
import com.amazonaws.services.ecs.model.NetworkMode;
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.EnvironmentEntry;
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.ExtraHostEntry;
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.LogDriverOption;
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.MountPointEntry;
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.PortMappingEntry;
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.PlacementStrategyEntry;
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.CapacityProviderStrategyEntry;
import com.google.common.collect.ImmutableSet;
import hudson.model.Run;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.servlet.ServletException;

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
    private boolean defaultCapacityProvider;
    private String networkMode;
    private String remoteFSRoot;
    private boolean uniqueRemoteFSRoot;
    private String platformVersion;
    private int memory;
    private int memoryReservation;
    private int cpu;
    private int sharedMemorySize;
    private String subnets;
    private String securityGroups;
    private boolean assignPublicIp;
    private boolean privileged;
    private String containerUser;
    private String executionRole;
    private String taskrole;
    private String inheritFrom;
    private String logDriver;
    private List<LogDriverOption> logDriverOptions;
    private List<EnvironmentEntry> environments;
    private List<ExtraHostEntry> extraHosts;
    private List<MountPointEntry> mountPoints;
    private List<PortMappingEntry> portMappings;
    private List<PlacementStrategyEntry> placementStrategies;
    private List<CapacityProviderStrategyEntry> capacityProviderStrategies;

    private List<String> overrides;

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
    public void setUniqueRemoteFSRoot(boolean uniqueRemoteFSRoot) {
        this.uniqueRemoteFSRoot = uniqueRemoteFSRoot;
    }

    public boolean getUniqueRemoteFSRoot() {
        return uniqueRemoteFSRoot;
    }

    @DataBoundSetter
    public void setPlatformVersion(String platformVersion) {
        this.platformVersion = platformVersion;
    }

    public String getPlatformVersion() {
        return platformVersion  ;
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
    public void setSharedMemorySize(int sharedMemorySize) {
        this.sharedMemorySize = sharedMemorySize;
    }

    public int getSharedMemorySize() {
        return sharedMemorySize;
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
    public void setDefaultCapacityProvider(boolean defaultCapacityProvider) {
        this.defaultCapacityProvider = defaultCapacityProvider;
    }

    public boolean getDefaultCapacityProvider() {
        return defaultCapacityProvider;
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
    public void setExecutionRole(String executionRole) {
        this.executionRole = executionRole;
    }

    public String getExecutionRole() {
        return executionRole;
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

    public String getLogDriver() {
        return logDriver;
    }

    @DataBoundSetter
    public void setLogDriver(String logDriver) {
        this.logDriver = logDriver;
    }

    public List<LogDriverOption> getLogDriverOptions() {
        return logDriverOptions;
    }

    @DataBoundSetter
    public void setLogDriverOptions(List<LogDriverOption> logDriverOptions) {
        this.logDriverOptions = logDriverOptions;
    }

    public List<EnvironmentEntry> getEnvironments() {
        return environments;
    }

    @DataBoundSetter
    public void setEnvironments(List<EnvironmentEntry> environments) {
        this.environments = environments;
    }

    public List<ExtraHostEntry> getExtraHosts() {
        return extraHosts;
    }

    @DataBoundSetter
    public void setExtraHosts(List<ExtraHostEntry> extraHosts) {
        this.extraHosts = extraHosts;
    }

    public List<MountPointEntry> getMountPoints() {
        return mountPoints;
    }

    @DataBoundSetter
    public void setMountPoints(List<MountPointEntry> mountPoints) {
        this.mountPoints = mountPoints;
    }

    public List<PortMappingEntry> getPortMappings() {
        return portMappings;
    }

    @DataBoundSetter
    public void setPortMappings(List<PortMappingEntry> portMappings) {
        this.portMappings = portMappings;
    }

    public List<PlacementStrategyEntry> getPlacementStrategies() {
        return placementStrategies;
    }

    @DataBoundSetter
    public void setPlacementStrategies(List<PlacementStrategyEntry> placementStrategies) {
        this.placementStrategies = placementStrategies;
    }

    public List<CapacityProviderStrategyEntry> getCapacityProviderStrategies() {
        return capacityProviderStrategies;
    }

    @DataBoundSetter
    public void setCapacityProviderStrategy(List<CapacityProviderStrategyEntry> capacityProviderStrategies) {
        this.capacityProviderStrategies = capacityProviderStrategies;
    }
    

    @DataBoundSetter
    public void setOverrides(List<String> overrides) {
        this.overrides = overrides;
    }

    public List<String> getOverrides() {
        return overrides;
    }

    @Override
    public StepExecution start(StepContext stepContext) throws Exception {
        LOGGER.log(Level.FINE, "In ECSTaskTemplateStep start. label: {0}", label);
        LOGGER.log(Level.FINE, "In ECSTaskTemplateStep start. cloud: {0}", cloud);
        return new ECSTaskTemplateStepExecution(this, stepContext, (SerializableSupplier<Jenkins.CloudList>)() -> Jenkins.get().clouds);
    }

    @Extension(optional = true)
    public static final class DescriptorImpl extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "ecsTaskTemplate";
        }
        @Override
        public String getDisplayName() {
            return "Define a task template to use in the AWS ECS plugin";
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

        public ListBoxModel doFillLaunchTypeItems() {
            final ListBoxModel options = new ListBoxModel();
            for (LaunchType launchType : LaunchType.values()) {
                options.add(launchType.toString());
            }
            return options;
        }

        public ListBoxModel doFillNetworkModeItems() {
            final ListBoxModel options = new ListBoxModel();
            for (NetworkMode networkMode : NetworkMode.values()) {
                options.add(networkMode.toString());
            }
            return options;
        }

        public ListBoxModel doFillProtocolItems() {
            final ListBoxModel options = new ListBoxModel();
            options.add("TCP", "tcp");
            options.add("UDP", "udp");
            return options;
        }

        public FormValidation doCheckSubnetsLaunchType(@QueryParameter("subnets") String subnets,
                @QueryParameter("launchType") String launchType) throws IOException, ServletException {
            if (launchType.contentEquals(LaunchType.FARGATE.toString())) {
                return FormValidation.error("Subnets need to be set, when using FARGATE");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckSubnetsNetworkMode(@QueryParameter("subnets") String subnets,
                @QueryParameter("networkMode") String networkMode) throws IOException, ServletException {
            if (networkMode.equals(NetworkMode.Awsvpc.toString()) && subnets.isEmpty()) {
                return FormValidation.error("Subnets need to be set when using awsvpc network mode");
            }
            return FormValidation.ok();
        }

        /* we validate both memory and memoryReservation fields to the same rules */
        public FormValidation doCheckMemory(@QueryParameter("memory") int memory,
                @QueryParameter("memoryReservation") int memoryReservation) throws IOException, ServletException {
            return validateMemorySettings(memory, memoryReservation);
        }

        public FormValidation doCheckMemoryReservation(@QueryParameter("memory") int memory,
                @QueryParameter("memoryReservation") int memoryReservation) throws IOException, ServletException {
            return validateMemorySettings(memory, memoryReservation);
        }

        private FormValidation validateMemorySettings(int memory, int memoryReservation) {
            if (memory < 0 || memoryReservation < 0) {
                return FormValidation.error("memory and/or memoryReservation must be 0 or a positive integer");
            }
            if (memory == 0 && memoryReservation == 0) {
                return FormValidation.error("at least one of memory or memoryReservation are required to be > 0");
            }
            if (memory > 0 && memoryReservation > 0) {
                if (memory <= memoryReservation) {
                    return FormValidation.error("memory must be greater than memoryReservation if both are specified");
                }
            }
            return FormValidation.ok();
        }
    }

    @Override
    public String toString() {
        return Arrays.stream(this.getClass().getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers()) ).map(f -> {
            try {
                return String.format("%s: %s",f.getName(),f.get(this));
            } catch (IllegalAccessException e) {
                throw new RuntimeException();
            }
        }).collect(Collectors.joining("\n"));
    }
}

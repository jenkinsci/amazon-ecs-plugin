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

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.AwsVpcConfiguration;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.EFSAuthorizationConfig;
import com.amazonaws.services.ecs.model.EFSAuthorizationConfigIAM;
import com.amazonaws.services.ecs.model.EFSTransitEncryption;
import com.amazonaws.services.ecs.model.EFSVolumeConfiguration;
import com.amazonaws.services.ecs.model.HostEntry;
import com.amazonaws.services.ecs.model.HostVolumeProperties;
import com.amazonaws.services.ecs.model.KeyValuePair;
import com.amazonaws.services.ecs.model.LaunchType;
import com.amazonaws.services.ecs.model.OSFamily;
import com.amazonaws.services.ecs.model.CPUArchitecture;
import com.amazonaws.services.ecs.model.CapacityProviderStrategyItem;
import com.amazonaws.services.ecs.model.LinuxParameters;
import com.amazonaws.services.ecs.model.MountPoint;
import com.amazonaws.services.ecs.model.NetworkMode;
import com.amazonaws.services.ecs.model.PlacementStrategy;
import com.amazonaws.services.ecs.model.PlacementStrategyType;
import com.amazonaws.services.ecs.model.PortMapping;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RepositoryCredentials;
import com.amazonaws.services.ecs.model.Volume;
import com.amazonaws.services.ecs.model.DescribeClustersRequest;
import com.amazonaws.services.ecs.model.DescribeClustersResult;
import com.amazonaws.services.ecs.model.Cluster;
import com.amazonaws.services.ecs.model.Ulimit;
import com.amazonaws.services.ecs.model.UlimitName;

import static com.google.common.base.Strings.isNullOrEmpty;
import com.amazonaws.services.elasticfilesystem.model.AccessPointDescription;
import com.amazonaws.services.elasticfilesystem.model.FileSystemDescription;
import com.cloudbees.jenkins.plugins.amazonecs.aws.EFSService;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ECSTaskTemplate extends AbstractDescribableImpl<ECSTaskTemplate> implements Serializable {
    private static final long serialVersionUID = -426721853953018205L;
    /**
     * Template Name
     */
    private final String templateName;
    /**
     * White-space separated list of {@link hudson.model.Node} labels.
     *
     * @see Label
     */
    @CheckForNull
    private final String label;

    @CheckForNull
    private final String agentContainerName;

    /**
     * Task Definition Override to use, instead of a Jenkins-managed Task definition. May be a family name or an ARN.
     */
    @CheckForNull
    private final String taskDefinitionOverride;

    /**
     * ARN of the task definition created for a dynamic agent
     */
    private String dynamicTaskDefinitionOverride;

    /**
     * Docker image
     * @see ContainerDefinition#withImage(String)
     */
    private final String image;
    /**
     * Agent remote FS
     */
    @Nullable
    private final String remoteFSRoot;
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
     * The ephemeral storage settings to use for tasks run with the task definition.
     *
     * @see com.amazonaws.services.ecs.model.TaskDefinition#withEphemeralStorage
     */
    private Integer ephemeralStorageSizeInGiB;

    /**
     * Sets the size of Share Memory (in MiB) using the
     * <code>--shm-size</code> option for the container.
     * A container instance has 64mb <code>/dev/shm</code> size
     * by default.
     *
     * @see LinuxParameters#withSharedMemorySize(Integer)
     */
    private final int sharedMemorySize;

    /**
     * Subnets to be assigned on the awsvpc network when using Fargate
     *
     * @see AwsVpcConfiguration#setSubnets(Collection)
     */
    private String subnets;

    /**
     * Security groups to be assigned on the awsvpc network when using Fargate
     *
     * @see AwsVpcConfiguration#setSecurityGroups(Collection)
     */
    private String securityGroups;

    /**
     * Assign a public Ip to instance on awsvpc network when using Fargate
     *
     * @see AwsVpcConfiguration#setAssignPublicIp(String)
     */
    private boolean assignPublicIp;

    /**
     * Space delimited list of Docker dns search domains
     *
     * @see ContainerDefinition#withDnsSearchDomains(Collection)
     */
    @CheckForNull
    private String dnsSearchDomains;

    /**
     * Space delimited list of Docker entry points
     *
     * @see ContainerDefinition#withEntryPoint(String...)
     */
    @CheckForNull
    private String entrypoint;

    /**
     * ARN of the IAM role to use for the agent ECS task
     *
     * @see RegisterTaskDefinitionRequest#withTaskRoleArn(String)
     */
    @CheckForNull
    private String taskrole;

    /**
     * ARN of the IAM role to use for the agent ECS task
     *
     * @see RegisterTaskDefinitionRequest#withExecutionRoleArn(String)
     */
    @CheckForNull
    private String executionRole;

    /**
     * ARN of the Secrets Manager to use for the agent ECS task
     *
     * @see ContainerDefinition#withRepositoryCredentials(RepositoryCredentials)
     */
    @CheckForNull
    private String repositoryCredentials;

    /**
     * JVM arguments to start slave.jar
     */
    @CheckForNull
    private String jvmArgs;

    /**
    * Container mount points, imported from volumes
     */
    private List<MountPointEntry> mountPoints;

    /**
     * Container mount points connecting to EFS
     */
    private List<EFSMountPointEntry> efsMountPoints;

    /**
     * Task launch type
     */
    private final String launchType;

    /**
     * Task operating system family type
     */
    private final String operatingSystemFamily;

    /**
     * Task CPU architecture type
     */
    private final String cpuArchitecture;

    /**
     * Use default capacity provider will omit launch types and capacity strategies
     *
     */
    private boolean defaultCapacityProvider;

    /**
     * Task network mode
     */
    private final String networkMode;

    /**
     * Indicates whether the container should run in privileged mode
     */
    private final boolean privileged;

    /**
     * Indicates whether to append a unique agent ID (the agent name)
     * at the end of the remoteFSRoot path.
     */
    private final boolean uniqueRemoteFSRoot;

    /**
     * Task launch type platform version
     */
    private final String platformVersion;

    /**
     * User for container
     */
    @Nullable
    private String containerUser;

    /**
     * List of kernel capabilities to be added
     */
    @Nullable
    private String kernelCapabilities;

    private List<EnvironmentEntry> environments;
    private List<ExtraHostEntry> extraHosts;
    private List<PortMappingEntry> portMappings;
    private List<UlimitEntry> ulimits;
    private List<PlacementStrategyEntry> placementStrategies;
    private List<CapacityProviderStrategyEntry> capacityProviderStrategies;

    private List<Tag> tags;

    /**
    * The log configuration specification for the container.
    * This parameter maps to LogConfig in the Create a container section of
    * the Docker Remote API and the --log-driver option to docker run.
    * Valid log drivers are displayed in the LogConfiguration data type.
    * This parameter requires version 1.18 of the Docker Remote API or greater
    * on your container instance. To check the Docker Remote API version on
    * your container instance, log into your container instance and run the
    * following command: sudo docker version | grep "Server API version"
    * The Amazon ECS container agent running on a container instance must
    * register the logging drivers available on that instance with the
    * ECS_AVAILABLE_LOGGING_DRIVERS environment variable before containers
    * placed on that instance can use these log configuration options.
    * For more information, see Amazon ECS Container Agent Configuration
    * in the Amazon EC2 Container Service Developer Guide.
    */
    @CheckForNull
    private String logDriver;
    private List<LogDriverOption> logDriverOptions;

    private String inheritFrom;

    /**
     * Enable command execution during runtime (comparable to a `docker exec ...`).
     * Cf. https://aws.amazon.com/blogs/containers/new-using-amazon-ecs-exec-access-your-containers-fargate-ec2/
     */
    private boolean enableExecuteCommand;

    @DataBoundConstructor
    public ECSTaskTemplate(String templateName,
                           @Nullable String label,
                           @Nullable String agentContainerName,
                           @Nullable String taskDefinitionOverride,
                           @Nullable String dynamicTaskDefinitionOverride,
                           String image,
                           @Nullable final String repositoryCredentials,
                           @Nullable String launchType,
                           @Nullable String operatingSystemFamily,
                           @Nullable String cpuArchitecture,
                           boolean defaultCapacityProvider,
                           @Nullable List<CapacityProviderStrategyEntry> capacityProviderStrategies,
                           String networkMode,
                           @Nullable String remoteFSRoot,
                           boolean uniqueRemoteFSRoot,
                           String platformVersion,
                           int memory,
                           int memoryReservation,
                           int cpu,
                           @Nullable Integer ephemeralStorageSizeInGiB,
                           @Nullable String subnets,
                           @Nullable String securityGroups,
                           boolean assignPublicIp,
                           boolean privileged,
                           @Nullable String containerUser,
                           @Nullable String kernelCapabilities,
                           @Nullable List<LogDriverOption> logDriverOptions,
                           @Nullable List<Tag> tags,
                           @Nullable List<EnvironmentEntry> environments,
                           @Nullable List<ExtraHostEntry> extraHosts,
                           @Nullable List<MountPointEntry> mountPoints,
                           @Nullable List<EFSMountPointEntry> efsMountPoints,
                           @Nullable List<PortMappingEntry> portMappings,
                           @Nullable List<UlimitEntry> ulimits,
                           @Nullable String executionRole,
                           @Nullable List<PlacementStrategyEntry> placementStrategies,
                           @Nullable String taskrole,
                           @Nullable String inheritFrom,
                           int sharedMemorySize,
                           boolean enableExecuteCommand) {
        // if the user enters a task definition override, always prefer to use it, rather than the jenkins template.
        if (taskDefinitionOverride != null && !taskDefinitionOverride.trim().isEmpty()) {
            this.taskDefinitionOverride = taskDefinitionOverride.trim();

            if (agentContainerName != null && !agentContainerName.trim().isEmpty()) {
                this.agentContainerName = agentContainerName.trim();
            } else {
                this.agentContainerName = null;
            }

            // Always set the template name to the empty string if we are using a task definition override,
            // since we don't want Jenkins to touch our definitions.
            this.templateName = "";
        } else {
            // If the template name is empty we will add a default name and a
            // random element that will help to find it later when we want to delete it.
            this.templateName = templateName.isEmpty() ?
                    "jenkinsTask-" + UUID.randomUUID().toString() : templateName;
            // Make sure we don't have both a template name and a task definition override.
            this.taskDefinitionOverride = null;
            // An agent container name doesn't make sense when there is only one container
            // definition.
            this.agentContainerName = null;
        }

        this.label = label;
        this.image = image;
        this.repositoryCredentials = StringUtils.trimToNull(repositoryCredentials);
        this.remoteFSRoot = remoteFSRoot;
        this.uniqueRemoteFSRoot = uniqueRemoteFSRoot;
        this.platformVersion = platformVersion;
        this.memory = memory;
        this.memoryReservation = memoryReservation;
        this.cpu = cpu;
        this.ephemeralStorageSizeInGiB = ephemeralStorageSizeInGiB;
        this.launchType = launchType;
        this.operatingSystemFamily = operatingSystemFamily;
        this.cpuArchitecture = cpuArchitecture;
        this.defaultCapacityProvider = defaultCapacityProvider;
        this.capacityProviderStrategies = capacityProviderStrategies;
        this.networkMode = networkMode;
        this.subnets = subnets;
        this.securityGroups = securityGroups;
        this.assignPublicIp = assignPublicIp;
        this.privileged = privileged;
        this.containerUser = StringUtils.trimToNull(containerUser);
        this.kernelCapabilities = StringUtils.trimToNull(kernelCapabilities);
        this.logDriverOptions = logDriverOptions;
        this.tags = tags;
        this.environments = environments;
        this.extraHosts = extraHosts;
        this.mountPoints = mountPoints;
        this.efsMountPoints = efsMountPoints;
        this.portMappings = portMappings;
        this.ulimits = ulimits;
        this.executionRole = executionRole;
        this.placementStrategies = placementStrategies;
        this.taskrole = taskrole;
        this.inheritFrom = inheritFrom;
        this.sharedMemorySize = sharedMemorySize;
        this.dynamicTaskDefinitionOverride = StringUtils.trimToNull(dynamicTaskDefinitionOverride);
        this.enableExecuteCommand = enableExecuteCommand;
    }

    @DataBoundSetter
    public void setDynamicTaskDefinition(String dynamicTaskDefArn) {
        this.dynamicTaskDefinitionOverride = StringUtils.trimToNull(dynamicTaskDefArn);
    }

    @DataBoundSetter
    public void setTaskrole(String taskRoleArn) {
        this.taskrole = StringUtils.trimToNull(taskRoleArn);
    }

    @DataBoundSetter
    public void setExecutionRole(String executionRole) {
        this.executionRole = StringUtils.trimToNull(executionRole);
    }

    @DataBoundSetter
    public void setRepositoryCredentials(final String repositoryCredentials) {
        this.repositoryCredentials = StringUtils.trimToNull(repositoryCredentials);
    }

    @DataBoundSetter
    public void setEntrypoint(String entrypoint) {
        this.entrypoint = StringUtils.trimToNull(entrypoint);
    }

    @DataBoundSetter
    public void setJvmArgs(String jvmArgs) {
        this.jvmArgs = StringUtils.trimToNull(jvmArgs);
    }

    @DataBoundSetter
    public void setContainerUser(String containerUser) {
        this.containerUser = StringUtils.trimToNull(containerUser);
    }

    @DataBoundSetter
    public void setKernelCapabilities(String kernelCapabilities) {
        this.kernelCapabilities = StringUtils.trimToNull(kernelCapabilities);
    }

    @DataBoundSetter
    public void setLogDriver(String logDriver) {
        this.logDriver = StringUtils.trimToNull(logDriver);
    }

    @DataBoundSetter
    public void setInheritFrom(String inheritFrom) {
        this.inheritFrom = inheritFrom;
    }

    @DataBoundSetter
    public void setSubnets(String subnets) { this.subnets = StringUtils.trimToNull(subnets); }

    @DataBoundSetter
    public void setSecurityGroups(String securityGroups) { this.securityGroups = StringUtils.trimToNull(securityGroups); }

    @DataBoundSetter
    public void setDnsSearchDomains(String dnsSearchDomains) {
        this.dnsSearchDomains = StringUtils.trimToNull(dnsSearchDomains);
    }

    public boolean isFargate() {
        if (!this.defaultCapacityProvider && this.capacityProviderStrategies != null && ! this.capacityProviderStrategies.isEmpty()) {
            for (CapacityProviderStrategyEntry capacityProviderStrategy : this.capacityProviderStrategies) {
                String provider = capacityProviderStrategy.provider;
                if (provider.contains(LaunchType.FARGATE.toString())) {
                    return true;
                }
            }
            return false;
        }
        return StringUtils.trimToNull(this.launchType) != null && launchType.equals(LaunchType.FARGATE.toString());
    }

    public boolean isEC2() {
        return StringUtils.trimToNull(this.launchType) != null && launchType.equals(LaunchType.EC2.toString());
    }

    public String getLabel() {
        return label;
    }

    public String getAgentContainerName() {
        return agentContainerName;
    }

    public String getTaskDefinitionOverride() {
        return taskDefinitionOverride;
    }

    public String getDynamicTaskDefinition() {
        return dynamicTaskDefinitionOverride;
    }

    public String getImage() {
        return image;
    }

    public String getRemoteFSRoot() {
        return remoteFSRoot;
    }

    public String makeRemoteFSRoot(@Nonnull String name) {
        if (!uniqueRemoteFSRoot || remoteFSRoot == null) {
            return remoteFSRoot;
        }
        return remoteFSRoot + "/" + name;
    }

    public boolean getUniqueRemoteFSRoot() {
        return uniqueRemoteFSRoot;
    }

    public String getPlatformVersion() {
        return platformVersion;
    }

    public int getMemory() {
        return memory;
    }

    public int getMemoryReservation() {
        return memoryReservation;
    }

    public int getCpu() {
        return cpu;
    }

    public int getSharedMemorySize() { return sharedMemorySize; }

    public Integer getEphemeralStorageSizeInGiB() {
        return ephemeralStorageSizeInGiB;
    }

    public String getSubnets() {
        return subnets;
    }

    public String getSecurityGroups() {
        return securityGroups;
    }

    public boolean getAssignPublicIp() {
        return assignPublicIp;
    }

    public boolean getDefaultCapacityProvider() {
        return defaultCapacityProvider;
    }

    public String getDnsSearchDomains() {
        return dnsSearchDomains;
    }

    public String getEntrypoint() {
        return entrypoint;
    }

    public String getTaskrole() {
        return taskrole;
    }

    public String getExecutionRole() {
        return executionRole;
    }

    public String getRepositoryCredentials() {
        return repositoryCredentials;
    }

    public String getJvmArgs() {
        return jvmArgs;
    }

    public boolean getPrivileged() {
        return privileged;
    }

    public String getContainerUser() {
        return containerUser;
    }

    public String getKernelCapabilities() {
        return kernelCapabilities;
    }

    public String getLaunchType() {
        if (StringUtils.trimToNull(this.launchType) == null) {
            return LaunchType.
                    EC2.toString();
        }
        return launchType;
    }

    public String getOperatingSystemFamily() {
        if (StringUtils.trimToNull(this.operatingSystemFamily) == null) {
            return OSFamily.
                    LINUX.toString();
        }
        return operatingSystemFamily;
    }

    public String getCpuArchitecture() {
        if (StringUtils.trimToNull(this.cpuArchitecture) == null) {
            return CPUArchitecture.
                    X86_64.toString();
        }
        return cpuArchitecture;
    }


    public String getNetworkMode() {
        return networkMode;
    }

    public String getLogDriver() {
        return logDriver;
    }

    public String getInheritFrom() {
        return inheritFrom;
    }

    public String getTemplateName() {return templateName; }

    public boolean isEnableExecuteCommand() {
        return enableExecuteCommand;
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

    public static class LogDriverOption extends AbstractDescribableImpl<LogDriverOption> implements Serializable {
        private static final long serialVersionUID = 8585792353105873086L;
        public String name, value;

        @DataBoundConstructor
        public LogDriverOption(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String toString() {
            return "LogDriverOption{" + name + ": " + value + "}";
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<LogDriverOption> {
            @Override
            public String getDisplayName() {
                return "logDriverOption";
            }
        }
    }

    public List<LogDriverOption> getLogDriverOptions() {
        return logDriverOptions;
    }

    public static class Tag extends AbstractDescribableImpl<Tag> implements Serializable {
        private static final long serialVersionUID = 4357423231051873086L;
        public String name, value;

        @DataBoundConstructor
        public Tag(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Tag tag = (Tag) o;
            return Objects.equals(name, tag.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }

        @Override
        public String toString() {
            return "Tag{" + name + ": " + value + "}";
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<Tag> {
            @Override
            public String getDisplayName() {
                return "tag";
            }
        }
    }

    public List<Tag> getTags() { return tags; }

    Map<String,String> getLogDriverOptionsMap() {
        if (null == logDriverOptions || logDriverOptions.isEmpty()) {
            return null;
        }
        Map<String,String> options = new HashMap<String,String>();
        for (LogDriverOption logDriverOption : logDriverOptions) {
            String name = logDriverOption.name;
            String value = logDriverOption.value;
            if (StringUtils.isEmpty(name) || StringUtils.isEmpty(value)) {
                continue;
            }
            options.put(name, value);
        }
        return options;
    }

    Map<String,String> getTagsMap() {
        if (null == tags || tags.isEmpty()) {
            return null;
        }
        Map<String,String> options = new HashMap<String,String>();
        for (Tag tag : tags) {
            String name = tag.name;
            String value = tag.value;
            if (StringUtils.isEmpty(name) || StringUtils.isEmpty(value)) {
                continue;
            }
            options.put(name, value);
        }
        return options;
    }

    public List<EnvironmentEntry> getEnvironments() {
        return environments;
    }

    public List<ExtraHostEntry> getExtraHosts() {
        return extraHosts;
    }

    public List<PortMappingEntry> getPortMappings() {
        return portMappings;
    }
    public List<UlimitEntry> getUlimits() {
        return ulimits;
    }

    public List<PlacementStrategyEntry> getPlacementStrategies() {
        return placementStrategies;
    }

    public List<CapacityProviderStrategyEntry> getCapacityProviderStrategies() {
        return capacityProviderStrategies;
    }


    /**
     * This merge does not take an into consideration the child intentionally setting empty values for parameters like "entrypoint" - in fact
     * it's not uncommon to override the entrypoint of a container and set it to blank so you can use your own entrypoint as part of the command.
     * What's really needed is a "MergeStrategy <pre>BinaryOperator&lt;ECSTaskTemplate&gt;</pre> that's user selectable.
     *
     * @param parent inherit settings from
     * @return a 'merged' template
     */
    public ECSTaskTemplate merge(ECSTaskTemplate parent) {
        if(parent == null) {
            return this;
        }
        String templateName = isNullOrEmpty(this.templateName) ? parent.getTemplateName() : this.templateName;
        String label = isNullOrEmpty(this.label) ? parent.getLabel() : this.label;
        String agentContainerName = isNullOrEmpty(this.agentContainerName) ? parent.getAgentContainerName() : this.agentContainerName;
        String taskDefinitionOverride = isNullOrEmpty(this.taskDefinitionOverride) ? parent.getTaskDefinitionOverride() : this.taskDefinitionOverride;
        String image = isNullOrEmpty(this.image) ? parent.getImage() : this.image;
        String repositoryCredentials = isNullOrEmpty(this.repositoryCredentials) ? parent.getRepositoryCredentials() : this.repositoryCredentials;
        String launchType = isNullOrEmpty(this.launchType) ? parent.getLaunchType() : this.launchType;
        String operatingSystemFamily = isNullOrEmpty(this.operatingSystemFamily) ? parent.getOperatingSystemFamily() : this.operatingSystemFamily;
        String cpuArchitecture = isNullOrEmpty(this.cpuArchitecture) ? parent.getCpuArchitecture() : this.cpuArchitecture;
        boolean defaultCapacityProvider = this.defaultCapacityProvider ? this.defaultCapacityProvider : parent.getDefaultCapacityProvider();
        String networkMode = isNullOrEmpty(this.networkMode) ? parent.getNetworkMode() : this.networkMode;
        String remoteFSRoot = isNullOrEmpty(this.remoteFSRoot) ? parent.getRemoteFSRoot() : this.remoteFSRoot;

        // Bug potential here. If I intenionally set it false in the child, then it will be ignored and take the parent. Need null to mean 'unset'
        boolean uniqueRemoteFSRoot = this.uniqueRemoteFSRoot || parent.getUniqueRemoteFSRoot();
        String platformVersion = isNullOrEmpty(this.platformVersion) ? parent.getPlatformVersion() : this.platformVersion;
        int memory = this.memory == 0 ? parent.getMemory() : this.memory;
        int memoryReservation = this.memoryReservation == 0 ? parent.getMemoryReservation() : this.memoryReservation;
        int cpu = this.cpu == 0 ? parent.getCpu() : this.cpu;
        Integer ephemeralStorageSizeInGiB = this.ephemeralStorageSizeInGiB == null ? parent.getEphemeralStorageSizeInGiB() : this.ephemeralStorageSizeInGiB;
        int sharedMemorySize = this.sharedMemorySize == 0 ? parent.getSharedMemorySize() : this.sharedMemorySize;
        String subnets = isNullOrEmpty(this.subnets) ? parent.getSubnets() : this.subnets;
        String securityGroups = isNullOrEmpty(this.securityGroups) ? parent.getSecurityGroups() : this.securityGroups;

        // Bug potential here. If I intenionally set it false in the child, then it will be ignored and take the parent. Need null to mean 'unset'
        boolean assignPublicIp = this.assignPublicIp ? this.assignPublicIp : parent.getAssignPublicIp();

        // Bug potential here. If I intenionally set it false in the child, then it will be ignored and take the parent. Need null to mean 'unset'
        boolean privileged = this.privileged ? this.privileged : parent.getPrivileged();
        String containerUser = isNullOrEmpty(this.containerUser) ? parent.getContainerUser() : this.containerUser;
        String kernelCapabilities = isNullOrEmpty(this.kernelCapabilities) ? parent.getKernelCapabilities() : this.kernelCapabilities;
        String logDriver = isNullOrEmpty(this.logDriver) ? parent.getLogDriver() : this.logDriver;
        String entrypoint = isNullOrEmpty(this.entrypoint) ? parent.getEntrypoint() : this.entrypoint;
        Set<Tag> mergedTagsSet = new HashSet<>(isEmpty(this.tags) ? new ArrayList<>() : this.tags);
        Optional.ofNullable(parent.getTags()).ifPresent(mergedTagsSet::addAll);
        List<Tag> tags = new ArrayList<>(mergedTagsSet);
        // TODO probably merge lists with parent instead of overriding them
        List<LogDriverOption> logDriverOptions = isEmpty(this.logDriverOptions) ? parent.getLogDriverOptions() : this.logDriverOptions;
        List<EnvironmentEntry> environments = isEmpty(this.environments) ? parent.getEnvironments() : this.environments;
        List<ExtraHostEntry> extraHosts = isEmpty(this.extraHosts) ? parent.getExtraHosts() : this.extraHosts;
        List<MountPointEntry> mountPoints = isEmpty(this.mountPoints) ? parent.getMountPoints() : this.mountPoints;
        List<EFSMountPointEntry> efsMountPoints = isEmpty(this.efsMountPoints) ? parent.getEfsMountPoints() : this.efsMountPoints;
        List<PortMappingEntry> portMappings = isEmpty(this.portMappings) ? parent.getPortMappings() : this.portMappings;
        List<UlimitEntry> ulimits = isEmpty(this.ulimits) ? parent.getUlimits() : this.ulimits;
        List<PlacementStrategyEntry> placementStrategies = isEmpty(this.placementStrategies) ? parent.getPlacementStrategies() : this.placementStrategies;
        List<CapacityProviderStrategyEntry> capacityProviderStrategies = isEmpty(this.capacityProviderStrategies) ? parent.getCapacityProviderStrategies() : this.capacityProviderStrategies;

        String executionRole = isNullOrEmpty(this.executionRole) ? parent.getExecutionRole() : this.executionRole;
        String taskrole = isNullOrEmpty(this.taskrole) ? parent.getTaskrole() : this.taskrole;
        boolean enableExecuteCommand = this.enableExecuteCommand ? true : parent.isEnableExecuteCommand();

        ECSTaskTemplate merged = new ECSTaskTemplate(templateName,
                                                       label,
                                                       agentContainerName,
                                                       taskDefinitionOverride,
                                                       null,
                                                       image,
                                                       repositoryCredentials,
                                                       launchType,
                                                       operatingSystemFamily,
                                                       cpuArchitecture,
                                                       defaultCapacityProvider,
                                                       capacityProviderStrategies,
                                                       networkMode,
                                                       remoteFSRoot,
                                                       uniqueRemoteFSRoot,
                                                       platformVersion,
                                                       memory,
                                                       memoryReservation,
                                                       cpu,
                                                       ephemeralStorageSizeInGiB,
                                                       subnets,
                                                       securityGroups,
                                                       assignPublicIp,
                                                       privileged,
                                                       containerUser,
                                                       kernelCapabilities,
                                                       logDriverOptions,
                                                       tags,
                                                       environments,
                                                       extraHosts,
                                                       mountPoints,
                                                       efsMountPoints,
                                                       portMappings,
                                                       ulimits,
                                                       executionRole,
                                                       placementStrategies,
                                                       taskrole,
                                                       null,
                                                        sharedMemorySize,
                                                        enableExecuteCommand);
        merged.setLogDriver(logDriver);
        merged.setEntrypoint(entrypoint);

        return merged;
    }

    Collection<KeyValuePair> getEnvironmentKeyValuePairs() {
        if (null == environments || environments.isEmpty()) {
            return null;
        }
        Collection<KeyValuePair> items = new ArrayList<KeyValuePair>();
        for (EnvironmentEntry environment : environments) {
            String name = environment.name;
            String value = environment.value;
            if (StringUtils.isEmpty(name) || StringUtils.isEmpty(value)) {
                continue;
            }
            items.add(new KeyValuePair().withName(name).withValue(value));
        }
        return items;
    }

    Collection<HostEntry> getExtraHostEntries() {
        if (null == extraHosts || extraHosts.isEmpty()) {
            return null;
        }
        Collection<HostEntry> items = new ArrayList<HostEntry>();
        for (ExtraHostEntry extrahost : extraHosts) {
            String ipAddress = extrahost.ipAddress;
            String hostname = extrahost.hostname;
            if (StringUtils.isEmpty(ipAddress) || StringUtils.isEmpty(hostname)) {
                continue;
            }
            items.add(new HostEntry().withIpAddress(ipAddress).withHostname(hostname));
        }
        return items;
    }

    public List<MountPointEntry> getMountPoints() {
        return mountPoints;
    }

    public List<EFSMountPointEntry> getEfsMountPoints() {
        return efsMountPoints;
    }

    Collection<Volume> getVolumeEntries() {
        Collection<Volume> vols = new LinkedList<Volume>();
        if (null != mountPoints ) {
            for (MountPointEntry mount : mountPoints) {
                String name = mount.name;
                String sourcePath = mount.sourcePath;
                HostVolumeProperties hostVolume = new HostVolumeProperties();
                if (StringUtils.isEmpty(name))
                    continue;
                if (! StringUtils.isEmpty(sourcePath))
                    hostVolume.setSourcePath(sourcePath);
                vols.add(new Volume().withName(name)
                                     .withHost(hostVolume));
            }
        }

        if (null != efsMountPoints) {
            for (EFSMountPointEntry mount : efsMountPoints) {
                if (StringUtils.isEmpty(mount.name)) {
                    continue;
                }

                EFSAuthorizationConfig efsAuthorizationConfig = null;
                if (StringUtils.isNotEmpty(mount.accessPointId)) {
                    efsAuthorizationConfig = new EFSAuthorizationConfig()
                            .withAccessPointId(mount.accessPointId)
                            .withIam(BooleanUtils.isTrue(mount.iam)
                                    ? EFSAuthorizationConfigIAM.ENABLED
                                    : EFSAuthorizationConfigIAM.DISABLED
                            );
                }

                EFSVolumeConfiguration efsVolumeConfiguration = new EFSVolumeConfiguration()
                        .withFileSystemId(mount.fileSystemId)
                        .withRootDirectory(mount.rootDirectory)
                        .withAuthorizationConfig(efsAuthorizationConfig)
                        .withTransitEncryption(BooleanUtils.isTrue(mount.transitEncryption)
                                ? EFSTransitEncryption.ENABLED
                                : EFSTransitEncryption.DISABLED
                        );

                vols.add(new Volume().withName(mount.name)
                                     .withEfsVolumeConfiguration(efsVolumeConfiguration));
            }
        }

        return vols;
    }

    Collection<MountPoint> getMountPointEntries() {
        Collection<MountPoint> mounts = new ArrayList<MountPoint>();
        if (null != mountPoints) {
            for (MountPointEntry mount : mountPoints) {
                String src = mount.name;
                String path = mount.containerPath;
                Boolean ro = mount.readOnly;
                if (StringUtils.isEmpty(src) || StringUtils.isEmpty(path))
                    continue;
                mounts.add(new MountPoint().withSourceVolume(src)
                        .withContainerPath(path)
                        .withReadOnly(ro));
            }
        }

        if (null != efsMountPoints) {
            for (EFSMountPointEntry mount : efsMountPoints) {
                String src = mount.name;
                String path = mount.containerPath;
                Boolean ro = mount.readOnly;
                if (StringUtils.isEmpty(src) || StringUtils.isEmpty(path))
                    continue;
                mounts.add(new MountPoint().withSourceVolume(src)
                        .withContainerPath(path)
                        .withReadOnly(ro));
            }
        }

        return mounts.isEmpty() ? null : mounts;
    }

    Collection<PortMapping> getPortMappingEntries() {
        if (null == portMappings || portMappings.isEmpty())
            return null;
        Collection<PortMapping> ports = new ArrayList<PortMapping>();
        for (PortMappingEntry portMapping : this.portMappings) {
            Integer container = portMapping.containerPort;
            Integer host = portMapping.hostPort;
            String protocol = portMapping.protocol;

            ports.add(new PortMapping().withContainerPort(container)
                    .withHostPort(host)
                    .withProtocol(protocol));
        }
        return ports;
    }
    Collection<Ulimit> getUlimitsEntries() {
        if (null == ulimits || ulimits.isEmpty())
            return null;
        Collection<Ulimit> ulimits = new ArrayList<Ulimit>();
        for (UlimitEntry ulimitEntry : this.ulimits) {
            Integer hardLimit = ulimitEntry.hardLimit;
            Integer softLimit = ulimitEntry.softLimit;
            String ulimitName = ulimitEntry.ulimitName;

            ulimits.add(new Ulimit().withHardLimit(hardLimit)
                    .withSoftLimit(softLimit)
                    .withName(ulimitName));
        }
        return ulimits;
    }

    Collection<PlacementStrategy> getPlacementStrategyEntries() {
        if (null == placementStrategies || placementStrategies.isEmpty())
            return null;
        Collection<PlacementStrategy> placements = new ArrayList<PlacementStrategy>();
        for (PlacementStrategyEntry placementStrategy : this.placementStrategies) {
            String type = placementStrategy.type;
            String field = placementStrategy.field;

            placements.add(new PlacementStrategy().withType(type)
                                       .withField(field));
        }
        return placements;
    }

    Collection<CapacityProviderStrategyItem> getCapacityProviderStrategyEntries() {
        if (null == capacityProviderStrategies || capacityProviderStrategies.isEmpty())
            return null;
        Collection<CapacityProviderStrategyItem> stragies = new ArrayList<CapacityProviderStrategyItem>();
        for (CapacityProviderStrategyEntry capacityProviderStrategy : this.capacityProviderStrategies) {
            String provider = capacityProviderStrategy.provider;
            int base = capacityProviderStrategy.base;
            int weight = capacityProviderStrategy.weight;

            stragies.add(new CapacityProviderStrategyItem().withCapacityProvider(provider)
                                       .withWeight(weight)
                                       .withBase(base));
        }
        return stragies;
    }

    public static class EnvironmentEntry extends AbstractDescribableImpl<EnvironmentEntry> implements Serializable {
        private static final long serialVersionUID = 4195862080979262875L;
        public String name, value;

        @DataBoundConstructor
        public EnvironmentEntry(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String toString() {
            return "EnvironmentEntry{" + name + ": " + value + "}";
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<EnvironmentEntry> {
            @Override
            public String getDisplayName() {
                return "EnvironmentEntry";
            }
        }
    }

    public static class ExtraHostEntry extends AbstractDescribableImpl<ExtraHostEntry> implements Serializable {
        private static final long serialVersionUID = -23978859661031633L;
        public String ipAddress, hostname;

        @DataBoundConstructor
        public ExtraHostEntry(String ipAddress, String hostname) {
            this.ipAddress = ipAddress;
            this.hostname = hostname;
        }

        @Override
        public String toString() {
            return "ExtraHostEntry{" + ipAddress + ": " + hostname + "}";
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<ExtraHostEntry> {
            @Override
            public String getDisplayName() {
                return "ExtraHostEntry";
            }
        }
    }

    public static class MountPointEntry extends AbstractDescribableImpl<MountPointEntry> implements Serializable {
        private static final long serialVersionUID = -5363412950753423854L;
        public String name, sourcePath, containerPath;
        public Boolean readOnly;

        @DataBoundConstructor
        public MountPointEntry(String name,
                               String sourcePath,
                               String containerPath,
                               Boolean readOnly) {
            this.name = name;
            this.sourcePath = sourcePath;
            this.containerPath = containerPath;
            this.readOnly = readOnly;
        }

        @Override
        public String toString() {
            return "MountPointEntry{name:" + name +
                   ", sourcePath:" + sourcePath +
                   ", containerPath:" + containerPath +
                   ", readOnly:" + readOnly + "}";
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<MountPointEntry> {
            @Override
            public String getDisplayName() {
                return "MountPointEntry";
            }
        }
    }

    public static class EFSMountPointEntry extends AbstractDescribableImpl<EFSMountPointEntry> implements Serializable {
        private static final long serialVersionUID = -7894407420920480113L;
        public String name, containerPath, fileSystemId, rootDirectory, accessPointId;
        public Boolean transitEncryption, iam, readOnly;

        @DataBoundConstructor
        public EFSMountPointEntry(String name,
                                  String containerPath,
                                  Boolean readOnly,
                                  String fileSystemId,
                                  String rootDirectory,
                                  String accessPointId,
                                  Boolean transitEncryption,
                                  Boolean iam) {
            this.name = name;
            this.containerPath = containerPath;
            this.readOnly = readOnly;
            this.fileSystemId = fileSystemId;
            this.rootDirectory = rootDirectory;
            this.accessPointId = accessPointId;
            this.transitEncryption = transitEncryption;
            this.iam = iam;
        }

        @Override
        public String toString() {
            return "EFSMountPointEntry{name:" + name +
                    ", containerPath:" + containerPath +
                    ", readOnly:" + readOnly +
                    ", fileSystemId:" + fileSystemId +
                    ", rootDirectory:" + rootDirectory +
                    ", accessPointId:" + accessPointId +
                    ", transitEncryption:" + transitEncryption +
                    ", iam:" + iam + "}";
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<EFSMountPointEntry> {
            private static final Logger LOGGER = Logger.getLogger(EFSMountPointEntry.class.getName());

            @Override
            public String getDisplayName() {
                return "EFSMountPointEntry";
            }

            public ListBoxModel doFillFileSystemIdItems(
                    @RelativePath("../..") @QueryParameter String credentialsId,
                    @RelativePath("../..") @QueryParameter String regionName
            ) {
                EFSService efsService = new EFSService(credentialsId, regionName);
                try {
                    List<FileSystemDescription> allFileSystems = efsService.getAllFileSystems();
                    allFileSystems.sort(Comparator.comparing(FileSystemDescription::getName, Comparator.nullsFirst(Comparator.naturalOrder())));
                    final ListBoxModel options = new ListBoxModel();
                    for (final FileSystemDescription fileSystemDescription : allFileSystems) {
                        options.add(
                                optionalName(fileSystemDescription.getName(), fileSystemDescription.getFileSystemId()),
                                fileSystemDescription.getFileSystemId()
                        );
                    }
                    return options;
                } catch (RuntimeException e) {
                    LOGGER.log(Level.INFO, "Exception fetching file systems for credentials=" + credentialsId + ", regionName=" + regionName, e);
                    return new ListBoxModel();
                }
            }

            public ListBoxModel doFillAccessPointIdItems(
                    @RelativePath("../..") @QueryParameter String credentialsId,
                    @RelativePath("../..") @QueryParameter String regionName,
                    @QueryParameter String fileSystemId
            ) {
                EFSService efsService = new EFSService(credentialsId, regionName);
                try {
                    List<AccessPointDescription> accessPoints = efsService.getAccessPointsForFileSystem(fileSystemId);
                    accessPoints.sort(Comparator.comparing(AccessPointDescription::getName, Comparator.nullsFirst(Comparator.naturalOrder())));
                    final ListBoxModel options = new ListBoxModel();

                    options.add("None", "");
                    for (final AccessPointDescription accessPointDescription : accessPoints) {
                        options.add(
                                optionalName(accessPointDescription.getName(), accessPointDescription.getAccessPointId()),
                                accessPointDescription.getAccessPointId()
                        );
                    }
                    return options;
                } catch (RuntimeException e) {
                    LOGGER.log(Level.INFO, "Exception fetching access points for credentials=" + credentialsId + ", regionName=" + regionName, e);
                    return new ListBoxModel();
                }
            }

            private String optionalName(String name, String id) {
                if (StringUtils.isEmpty(name)) {
                    return id;
                }

                return name + " (" + id + ")";
            }
        }
    }

    public static class PortMappingEntry extends AbstractDescribableImpl<PortMappingEntry> implements Serializable {
        private static final long serialVersionUID = 8223725139080497839L;
        public Integer containerPort, hostPort;
        public String protocol;

        @DataBoundConstructor
        public PortMappingEntry(Integer containerPort, Integer hostPort, String protocol) {
            this.containerPort = containerPort;
            this.hostPort = hostPort;
            this.protocol = protocol;
        }

        @Override
        public String toString() {
            return "PortMappingEntry{" +
                    "containerPort=" + containerPort +
                    ", hostPort=" + hostPort +
                    ", protocol='" + protocol + "}";
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<PortMappingEntry> {
            public ListBoxModel doFillProtocolItems() {
                final ListBoxModel options = new ListBoxModel();
                options.add("TCP", "tcp");
                options.add("UDP", "udp");
                return options;
            }

            @Override
            public String getDisplayName() {
                return "PortMappingEntry";
            }
        }
    }
    public static class UlimitEntry extends AbstractDescribableImpl<UlimitEntry> implements Serializable {
        private static final long serialVersionUID = 8223725139080497838L;
        public Integer hardLimit, softLimit;
        public String ulimitName;

        @DataBoundConstructor
        public UlimitEntry(Integer softLimit,Integer hardLimit, String ulimitName) {
            this.softLimit = softLimit;
            this.hardLimit = hardLimit;
            this.ulimitName = ulimitName;
        }

        @Override
        public String toString() {
            return "UlimitEntry{" +
                    "softLimit=" + softLimit +
                    ", hardLimit=" + hardLimit +
                    ", ulimitName='" + ulimitName + "}";
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<UlimitEntry> {
            public ListBoxModel doFillUlimitNameItems() {
                final ListBoxModel options = new ListBoxModel();
                for (UlimitName ulimitName : UlimitName.values()) {
                    options.add(ulimitName.toString());
                }
                return options;
            }

            @Override
            public String getDisplayName() {
                return "UlimitEntry";
            }
        }
    }



    public static class PlacementStrategyEntry extends AbstractDescribableImpl<PlacementStrategyEntry> implements Serializable {
        //private static final long serialVersionUID = 4195862080979262875L;
        public String type, field;

        @DataBoundConstructor
        public PlacementStrategyEntry(String type, String field) {
            this.type = type;
            this.field = field;
        }

        @Override
        public String toString() {
            return "PlacementStrategyEntry{" + type + ": " + field + "}";
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<PlacementStrategyEntry> {
            public ListBoxModel doFillTypeItems() {
                final ListBoxModel options = new ListBoxModel();
                for (PlacementStrategyType placementStrategyType: PlacementStrategyType.values()) {
                    options.add(placementStrategyType.toString());
                }
                return options;
            }
            @Override
            public String getDisplayName() {
                return "PlacementStrategyEntry";
            }

            public FormValidation doCheckField(@QueryParameter("field") String field, @QueryParameter("type") String type) throws IOException, ServletException {
                if (!type.contentEquals("random") && field.isEmpty()) {
                    return FormValidation.error("Field needs to be set when using Type other then random");
                }
                return FormValidation.ok();
            }
        }
    }

    public static class CapacityProviderStrategyEntry extends AbstractDescribableImpl<CapacityProviderStrategyEntry> implements Serializable {
        //private static final long serialVersionUID = 4195862080979262875L;
        public String provider;
        public int base, weight;

        @DataBoundConstructor
        public CapacityProviderStrategyEntry(String provider, int base, int weight) {
            this.base = base;
            this.weight = weight;
            this.provider = provider;
        }

        @Override
        public String toString() {
            return "CapacityProviderStrategyEntry{" + provider + "base: " + base + "weight: " + weight + "}";
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<CapacityProviderStrategyEntry> {
            public ListBoxModel doFillProviderItems(
                @RelativePath("../..") @QueryParameter String credentialsId,
                @RelativePath("../..") @QueryParameter String assumedRoleArn,
                @RelativePath("../..") @QueryParameter String regionName,
                @RelativePath("../..") @QueryParameter String cluster
            ){
                ECSService ecsService = new ECSService(credentialsId, assumedRoleArn, regionName);
                final AmazonECS client = ecsService.getAmazonECSClient();
                final List<Cluster> allClusters = new ArrayList<Cluster>();
                DescribeClustersResult result = client.describeClusters(new DescribeClustersRequest().withClusters(cluster));
                allClusters.addAll(result.getClusters());
                final ListBoxModel options = new ListBoxModel();
                for ( Cluster c : allClusters) {
                    List<String> item = c.getCapacityProviders();
                    Collections.sort(item);
                    for (String provider : item) {
                        options.add(provider);
                    }
                }
                return options;
            }
            @Override
            public String getDisplayName() {
                return "CapacityProviderStrategyEntry";
            }

            public FormValidation doCheckField(@QueryParameter("base") int base, @QueryParameter("weight") int weight, @QueryParameter("provider") String provider) throws IOException, ServletException {
                return FormValidation.ok();
            }
        }
    }

    public Set<LabelAtom> getLabelSet() {
        return Label.parse(label);
    }

    public String getDisplayName() {
        return "ECS Agent " + label;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ECSTaskTemplate> {

        private static String TEMPLATE_NAME_PATTERN = "[a-z|A-Z|0-9|_|-]{1,127}";

        @Override
        public String getDisplayName() {
            return com.cloudbees.jenkins.plugins.amazonecs.Messages.template();
        }

        public ListBoxModel doFillLaunchTypeItems() {
            final ListBoxModel options = new ListBoxModel();
            for (LaunchType launchType: LaunchType.values()) {
                options.add(launchType.toString());
            }
            return options;
        }

        public ListBoxModel doFillOperatingSystemFamilyItems() {
            final ListBoxModel options = new ListBoxModel();
            for (OSFamily operatingSystemFamily: OSFamily.values()) {
                options.add(operatingSystemFamily.toString());
            }
            return options;
        }

        public ListBoxModel doFillCpuArchitectureItems() {
            final ListBoxModel options = new ListBoxModel();
            for (CPUArchitecture cpuArchitecture: CPUArchitecture.values()) {
                options.add(cpuArchitecture.toString());
            }
            return options;
        }

        public ListBoxModel doFillNetworkModeItems() {
            final ListBoxModel options = new ListBoxModel();

            //Need to support Windows Containers - Need to allow Default which equal Null
            options.add("default");

            for (NetworkMode networkMode: NetworkMode.values()) {
                options.add(networkMode.toString());
            }

            return options;
        }

        public FormValidation doCheckTemplateName(
            @QueryParameter String value,
            @QueryParameter String taskDefinitionOverride
        ) throws IOException, ServletException {
            if (!isNullOrEmpty(taskDefinitionOverride)) {
                return FormValidation.ok();
            }
            if (value.length() > 0 && value.length() <= 127 && value.matches(TEMPLATE_NAME_PATTERN)) {
                return FormValidation.ok();
            }
            return FormValidation.error("Up to 127 letters (uppercase and lowercase), numbers, hyphens, and underscores are allowed");
        }

        public FormValidation doCheckSubnetsLaunchType(@QueryParameter("subnets") String subnets, @QueryParameter("launchType") String launchType) throws IOException, ServletException {
            if (launchType.contentEquals(LaunchType.FARGATE.toString())) {
                return FormValidation.error("Subnets need to be set, when using FARGATE");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckOperatingSystemFamilyLaunchType(@QueryParameter("operatingSystemFamily") String operatingSystemFamily, @QueryParameter("launchType") String launchType) throws IOException, ServletException {
            if (launchType.contentEquals(LaunchType.FARGATE.toString())) {
                return FormValidation.error("Operating system family need to be set, when using FARGATE");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckCpuArchitectureLaunchType(@QueryParameter("cpuArchitecture") String cpuArchitecture, @QueryParameter("launchType") String launchType) throws IOException, ServletException {
            if (launchType.contentEquals(LaunchType.FARGATE.toString())) {
                return FormValidation.error("CPU architecture need to be set, when using FARGATE");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckSubnetsNetworkMode(@QueryParameter("subnets") String subnets, @QueryParameter("networkMode") String networkMode) throws IOException, ServletException {
            if (networkMode.equals(NetworkMode.Awsvpc.toString()) && subnets.isEmpty()) {
                return FormValidation.error("Subnets need to be set when using awsvpc network mode");
            }
            return FormValidation.ok();
        }

        /* we validate both memory and memoryReservation fields to the same rules */
        public FormValidation doCheckMemory(
            @QueryParameter("memory") int memory,
            @QueryParameter("memoryReservation") int memoryReservation,
            @QueryParameter String taskDefinitionOverride
        ) throws IOException, ServletException {
            if (!isNullOrEmpty(taskDefinitionOverride)) {
                return FormValidation.ok();
            }
            return validateMemorySettings(memory,memoryReservation);
        }

        public FormValidation doCheckMemoryReservation(
            @QueryParameter("memory") int memory,
            @QueryParameter("memoryReservation") int memoryReservation,
            @QueryParameter String taskDefinitionOverride
        ) throws IOException, ServletException {
            if (!isNullOrEmpty(taskDefinitionOverride)) {
                return FormValidation.ok();
            }
            return validateMemorySettings(memory,memoryReservation);
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ECSTaskTemplate that = (ECSTaskTemplate) o;

        if (memory != that.memory) {
            return false;
        }
        if (memoryReservation != that.memoryReservation) {
            return false;
        }
        if (cpu != that.cpu) {
            return false;
        }
        if (!Objects.equals(ephemeralStorageSizeInGiB, that.ephemeralStorageSizeInGiB)) {
            return false;
        }
        if (sharedMemorySize != that.sharedMemorySize) {
            return false;
        }
        if (assignPublicIp != that.assignPublicIp) {
            return false;
        }
        if (privileged != that.privileged) {
            return false;
        }
        if (uniqueRemoteFSRoot != that.uniqueRemoteFSRoot) {
            return false;
        }
        if (platformVersion != null ? !platformVersion.equals(that.platformVersion) : that.platformVersion != null) {
            return false;
        }
        if (!templateName.equals(that.templateName)) {
            return false;
        }
        if (label != null ? !label.equals(that.label) : that.label != null) {
            return false;
        }
        if (agentContainerName != null ? !agentContainerName.equals(that.agentContainerName) : that.agentContainerName != null) {
            return false;
        }
        if (taskDefinitionOverride != null ? !taskDefinitionOverride.equals(that.taskDefinitionOverride) : that.taskDefinitionOverride != null) {
            return false;
        }
        if (image != null ? !image.equals(that.image) : that.image != null) {
            return false;
        }
        if (remoteFSRoot != null ? !remoteFSRoot.equals(that.remoteFSRoot) : that.remoteFSRoot != null) {
            return false;
        }
        if (subnets != null ? !subnets.equals(that.subnets) : that.subnets != null) {
            return false;
        }
        if (securityGroups != null ? !securityGroups.equals(that.securityGroups) : that.securityGroups != null) {
            return false;
        }
        if (dnsSearchDomains != null ? !dnsSearchDomains.equals(that.dnsSearchDomains) : that.dnsSearchDomains != null) {
            return false;
        }
        if (entrypoint != null ? !entrypoint.equals(that.entrypoint) : that.entrypoint != null) {
            return false;
        }
        if (taskrole != null ? !taskrole.equals(that.taskrole) : that.taskrole != null) {
            return false;
        }
        if (executionRole != null ? !executionRole.equals(that.executionRole) : that.executionRole != null) {
            return false;
        }
        if (repositoryCredentials != null ? !repositoryCredentials.equals(that.repositoryCredentials) : that.repositoryCredentials != null) {
            return false;
        }
        if (jvmArgs != null ? !jvmArgs.equals(that.jvmArgs) : that.jvmArgs != null) {
            return false;
        }
        if (mountPoints != null ? !mountPoints.equals(that.mountPoints) : that.mountPoints != null) {
            return false;
        }
        if (efsMountPoints != null ? !efsMountPoints.equals(that.efsMountPoints) : that.efsMountPoints != null) {
            return false;
        }
        if (launchType != null ? !launchType.equals(that.launchType) : that.launchType != null) {
            return false;
        }
        if (operatingSystemFamily != null ? !operatingSystemFamily.equals(that.operatingSystemFamily) : that.operatingSystemFamily != null) {
            return false;
        }
        if (cpuArchitecture != null ? !cpuArchitecture.equals(that.cpuArchitecture) : that.cpuArchitecture != null) {
            return false;
        }
        if (defaultCapacityProvider != that.defaultCapacityProvider) {
            return false;
        }
        if (capacityProviderStrategies != null ? !capacityProviderStrategies.equals(that.capacityProviderStrategies) : that.capacityProviderStrategies != null) {
            return false;
        }
        if (networkMode != null ? !networkMode.equals(that.networkMode) : that.networkMode != null) {
            return false;
        }
        if (containerUser != null ? !containerUser.equals(that.containerUser) : that.containerUser != null) {
            return false;
        }
        if (kernelCapabilities != null ? !kernelCapabilities.equals(that.kernelCapabilities) : that.kernelCapabilities != null) {
            return false;
        }
        if (environments != null ? !environments.equals(that.environments) : that.environments != null) {
            return false;
        }
        if (extraHosts != null ? !extraHosts.equals(that.extraHosts) : that.extraHosts != null) {
            return false;
        }
        if (portMappings != null ? !portMappings.equals(that.portMappings) : that.portMappings != null) {
            return false;
        }
        if (ulimits != null ? !ulimits.equals(that.ulimits) : that.ulimits != null) {
            return false;
        }
        if (placementStrategies != null ? !placementStrategies.equals(that.placementStrategies) : that.placementStrategies != null) {
            return false;
        }
        if (logDriver != null ? !logDriver.equals(that.logDriver) : that.logDriver != null) {
            return false;
        }
        if (logDriverOptions != null ? !logDriverOptions.equals(that.logDriverOptions) : that.logDriverOptions != null) {
            return false;
        }
        if (tags != null ? !tags.equals(that.tags) : that.tags != null) {
            return false;
        }

        return inheritFrom != null ? inheritFrom.equals(that.inheritFrom) : that.inheritFrom == null;
    }

    @Override
    public int hashCode() {
        int result = templateName.hashCode();
        result = 31 * result + (label != null ? label.hashCode() : 0);
        result = 31 * result + (agentContainerName != null ? agentContainerName.hashCode() : 0);
        result = 31 * result + (taskDefinitionOverride != null ? taskDefinitionOverride.hashCode() : 0);
        result = 31 * result + (image != null ? image.hashCode() : 0);
        result = 31 * result + (remoteFSRoot != null ? remoteFSRoot.hashCode() : 0);
        result = 31 * result + memory;
        result = 31 * result + memoryReservation;
        result = 31 * result + cpu;
        result = 31 * result + (ephemeralStorageSizeInGiB != null ? ephemeralStorageSizeInGiB : 0);
        result = 31 * result + sharedMemorySize;
        result = 31 * result + (platformVersion != null ? platformVersion.hashCode() : 0);
        result = 31 * result + (subnets != null ? subnets.hashCode() : 0);
        result = 31 * result + (securityGroups != null ? securityGroups.hashCode() : 0);
        result = 31 * result + (assignPublicIp ? 1 : 0);
        result = 31 * result + (dnsSearchDomains != null ? dnsSearchDomains.hashCode() : 0);
        result = 31 * result + (entrypoint != null ? entrypoint.hashCode() : 0);
        result = 31 * result + (taskrole != null ? taskrole.hashCode() : 0);
        result = 31 * result + (executionRole != null ? executionRole.hashCode() : 0);
        result = 31 * result + (repositoryCredentials != null ? repositoryCredentials.hashCode() : 0);
        result = 31 * result + (jvmArgs != null ? jvmArgs.hashCode() : 0);
        result = 31 * result + (mountPoints != null ? mountPoints.hashCode() : 0);
        result = 31 * result + (efsMountPoints != null ? efsMountPoints.hashCode() : 0);
        result = 31 * result + (launchType != null ? launchType.hashCode() : 0);
        result = 31 * result + (operatingSystemFamily != null ? operatingSystemFamily.hashCode() : 0);
        result = 31 * result + (cpuArchitecture != null ? cpuArchitecture.hashCode() : 0);
        result = 31 * result + (defaultCapacityProvider ? 1 : 0);
        result = 31 * result + (capacityProviderStrategies != null ? capacityProviderStrategies.hashCode() : 0);
        result = 31 * result + (networkMode != null ? networkMode.hashCode() : 0);
        result = 31 * result + (privileged ? 1 : 0);
        result = 31 * result + (uniqueRemoteFSRoot ? 1 : 0);
        result = 31 * result + (containerUser != null ? containerUser.hashCode() : 0);
        result = 31 * result + (kernelCapabilities != null ? kernelCapabilities.hashCode() : 0);
        result = 31 * result + (environments != null ? environments.hashCode() : 0);
        result = 31 * result + (extraHosts != null ? extraHosts.hashCode() : 0);
        result = 31 * result + (portMappings != null ? portMappings.hashCode() : 0);
        result = 31 * result + (ulimits != null ? ulimits.hashCode() : 0);
        result = 31 * result + (placementStrategies != null ? placementStrategies.hashCode() : 0);
        result = 31 * result + (logDriver != null ? logDriver.hashCode() : 0);
        result = 31 * result + (logDriverOptions != null ? logDriverOptions.hashCode() : 0);
        result = 31 * result + (tags != null ? tags.hashCode() : 0);
        result = 31 * result + (inheritFrom != null ? inheritFrom.hashCode() : 0);
        result = 31 * result + (enableExecuteCommand ? 1 : 0);
        return result;
    }
}

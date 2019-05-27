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

import com.amazonaws.services.ecs.model.*;
import com.amazonaws.services.ecs.model.Volume;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.collections.CollectionUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.ServletException;

import java.io.IOException;
import java.util.*;

import java.io.Serializable;
import com.google.common.base.Strings;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ECSTaskTemplate extends AbstractDescribableImpl<ECSTaskTemplate> implements Serializable {
    private static final long serialVersionUID = -426721853953018205L;
    /**
     * Template Name
     */
    @Nonnull
    private final String templateName;
    /**
     * White-space separated list of {@link hudson.model.Node} labels.
     *
     * @see Label
     */
    @CheckForNull
    private final String label;

    /**
     * Task Definition Override to use, instead of a Jenkins-managed Task definition. May be a family name or an ARN.
     */
    @CheckForNull
    private final String taskDefinitionOverride;

    /**
     * Docker image
     * @see ContainerDefinition#withImage(String)
     */
    @Nonnull
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
     * Task launch type
     */
    @Nonnull
    private final String launchType;

    /**
     * Task network mode
     */
    @Nonnull
    private final String networkMode;

    /**
     * Indicates whether the container should run in privileged mode
     */
    private final boolean privileged;

    /**
     * User for conatiner
     */
    @Nullable
    private String containerUser;

    private List<EnvironmentEntry> environments;
    private List<ExtraHostEntry> extraHosts;
    private List<PortMappingEntry> portMappings;

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

    @DataBoundConstructor
    public ECSTaskTemplate(@Nonnull String templateName,
                           @Nullable String label,
                           @Nullable String taskDefinitionOverride,
                           @Nonnull String image,
                           @Nullable final String repositoryCredentials,
                           @Nonnull String launchType,
                           @Nonnull String networkMode,
                           @Nullable String remoteFSRoot,
                           int memory,
                           int memoryReservation,
                           int cpu,
                           @Nullable String subnets,
                           @Nullable String securityGroups,
                           boolean assignPublicIp,
                           boolean privileged,
                           @Nullable String containerUser,
                           @Nullable List<LogDriverOption> logDriverOptions,
                           @Nullable List<EnvironmentEntry> environments,
                           @Nullable List<ExtraHostEntry> extraHosts,
                           @Nullable List<MountPointEntry> mountPoints,
                           @Nullable List<PortMappingEntry> portMappings,
                           @Nullable String executionRole,
                           @Nullable String taskrole,
                           @Nullable String inheritFrom) {
        // if the user enters a task definition override, always prefer to use it, rather than the jenkins template.
        if (taskDefinitionOverride != null && !taskDefinitionOverride.trim().isEmpty()) {
            this.taskDefinitionOverride = taskDefinitionOverride.trim();
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
        }

        this.label = label;
        this.image = image;
        this.repositoryCredentials = StringUtils.trimToNull(repositoryCredentials);
        this.remoteFSRoot = remoteFSRoot;
        this.memory = memory;
        this.memoryReservation = memoryReservation;
        this.cpu = cpu;
        this.launchType = launchType;
        this.networkMode = networkMode;
        this.subnets = subnets;
        this.securityGroups = securityGroups;
        this.assignPublicIp = assignPublicIp;
        this.privileged = privileged;
        this.containerUser = StringUtils.trimToNull(containerUser);
        this.logDriverOptions = logDriverOptions;
        this.environments = environments;
        this.extraHosts = extraHosts;
        this.mountPoints = mountPoints;
        this.portMappings = portMappings;
        this.executionRole = executionRole;
        this.taskrole = taskrole;
        this.inheritFrom = inheritFrom;
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
        return StringUtils.trimToNull(this.launchType) != null && launchType.equals(LaunchType.FARGATE.toString());
    }

    public String getLabel() {
        return label;
    }

    public String getTaskDefinitionOverride() {
        return taskDefinitionOverride;
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

    public int getMemoryReservation() {
        return memoryReservation;
    }

    public int getCpu() {
        return cpu;
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

    public String getLaunchType() {
        if (StringUtils.trimToNull(this.launchType) == null) {
            return LaunchType.EC2.toString();
        }
        return launchType;
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

    public List<EnvironmentEntry> getEnvironments() {
        return environments;
    }

    public List<ExtraHostEntry> getExtraHosts() {
        return extraHosts;
    }

    public List<PortMappingEntry> getPortMappings() {
        return portMappings;
    }

    public ECSTaskTemplate merge(ECSTaskTemplate parent) {
        if(parent == null) {
            return this;
        }

        String templateName = Strings.isNullOrEmpty(this.templateName) ? parent.getTemplateName() : this.templateName;
        String label = Strings.isNullOrEmpty(this.label) ? parent.getLabel() : this.label;
        String taskDefinitionOverride = Strings.isNullOrEmpty(this.taskDefinitionOverride) ? parent.getTaskDefinitionOverride() : this.taskDefinitionOverride;
        String image = Strings.isNullOrEmpty(this.image) ? parent.getImage() : this.image;
        String repositoryCredentials = Strings.isNullOrEmpty(this.repositoryCredentials) ? parent.getRepositoryCredentials() : this.repositoryCredentials;
        String launchType = Strings.isNullOrEmpty(this.launchType) ? parent.getLaunchType() : this.launchType;
        String networkMode = Strings.isNullOrEmpty(this.networkMode) ? parent.getNetworkMode() : this.networkMode;
        String remoteFSRoot = Strings.isNullOrEmpty(this.remoteFSRoot) ? parent.getRemoteFSRoot() : this.remoteFSRoot;
        int memory = this.memory == 0 ? parent.getMemory() : this.memory;
        int memoryReservation = this.memoryReservation == 0 ? parent.getMemoryReservation() : this.memoryReservation;
        int cpu = this.cpu == 0 ? parent.getCpu() : this.cpu;
        String subnets = Strings.isNullOrEmpty(this.subnets) ? parent.getSubnets() : this.subnets;
        String securityGroups = Strings.isNullOrEmpty(this.securityGroups) ? parent.getSecurityGroups() : this.securityGroups;
        boolean assignPublicIp = this.assignPublicIp ? this.assignPublicIp : parent.getAssignPublicIp();
        boolean privileged = this.privileged ? this.privileged : parent.getPrivileged();
        String containerUser = Strings.isNullOrEmpty(this.containerUser) ? parent.getContainerUser() : this.containerUser;
        String logDriver = Strings.isNullOrEmpty(this.logDriver) ? parent.getLogDriver() : this.logDriver;

        // TODO probably merge lists with parent instead of overriding them
        List<LogDriverOption> logDriverOptions = CollectionUtils.isEmpty(this.logDriverOptions) ? parent.getLogDriverOptions() : this.logDriverOptions;
        List<EnvironmentEntry> environments = CollectionUtils.isEmpty(this.environments) ? parent.getEnvironments() : this.environments;
        List<ExtraHostEntry> extraHosts = CollectionUtils.isEmpty(this.extraHosts) ? parent.getExtraHosts() : this.extraHosts;
        List<MountPointEntry> mountPoints = CollectionUtils.isEmpty(this.mountPoints) ? parent.getMountPoints() : this.mountPoints;
        List<PortMappingEntry> portMappings = CollectionUtils.isEmpty(this.portMappings) ? parent.getPortMappings() : this.portMappings;

        String executionRole = Strings.isNullOrEmpty(this.executionRole) ? parent.getExecutionRole() : this.executionRole;
        String taskrole = Strings.isNullOrEmpty(this.taskrole) ? parent.getTaskrole() : this.taskrole;

        ECSTaskTemplate merged = new ECSTaskTemplate(templateName,
                                                       label,
                                                       taskDefinitionOverride,
                                                       image,
                                                       repositoryCredentials,
                                                       launchType,
                                                       networkMode,
                                                       remoteFSRoot,
                                                       memory,
                                                       memoryReservation,
                                                       cpu,
                                                       subnets,
                                                       securityGroups,
                                                       assignPublicIp,
                                                       privileged,
                                                       containerUser,
                                                       logDriverOptions,
                                                       environments,
                                                       extraHosts,
                                                       mountPoints,
                                                       portMappings,
                                                       executionRole,
                                                       taskrole,
                                                       null);
        merged.setLogDriver(logDriver);

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
        return vols;
    }

    Collection<MountPoint> getMountPointEntries() {
        if (null == mountPoints || mountPoints.isEmpty())
            return null;
        Collection<MountPoint> mounts = new ArrayList<MountPoint>();
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
        return mounts;
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
            return Messages.template();
        }

        public ListBoxModel doFillLaunchTypeItems() {
            final ListBoxModel options = new ListBoxModel();
            for (LaunchType launchType: LaunchType.values()) {
                options.add(launchType.toString());
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

        public FormValidation doCheckTemplateName(@QueryParameter String value) throws IOException, ServletException {
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

        public FormValidation doCheckSubnetsNetworkMode(@QueryParameter("subnets") String subnets, @QueryParameter("networkMode") String networkMode) throws IOException, ServletException {
            if (networkMode.equals(NetworkMode.Awsvpc.toString()) && subnets.isEmpty()) {
                return FormValidation.error("Subnets need to be set when using awsvpc network mode");
            }
            return FormValidation.ok();
        }

        /* we validate both memory and memoryReservation fields to the same rules */
        public FormValidation doCheckMemory(@QueryParameter("memory") int memory, @QueryParameter("memoryReservation") int memoryReservation) throws IOException, ServletException {
            return validateMemorySettings(memory,memoryReservation);
        }

        public FormValidation doCheckMemoryReservation(@QueryParameter("memory") int memory, @QueryParameter("memoryReservation") int memoryReservation) throws IOException, ServletException {
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
}

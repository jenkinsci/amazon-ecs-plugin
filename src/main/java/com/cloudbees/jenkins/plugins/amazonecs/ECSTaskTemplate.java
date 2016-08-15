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

import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.HostEntry;
import com.amazonaws.services.ecs.model.Volume;
import com.amazonaws.services.ecs.model.VolumeFrom;
import com.amazonaws.services.ecs.model.HostVolumeProperties;
import com.amazonaws.services.ecs.model.MountPoint;
import com.amazonaws.services.ecs.model.DescribeTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.amazonaws.services.ecs.model.PortMapping;
import com.amazonaws.services.ecs.model.KeyValuePair;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.LogConfiguration;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ECSTaskTemplate extends AbstractDescribableImpl<ECSTaskTemplate> {

    /**
     * White-space separated list of {@link hudson.model.Node} labels.
     *
     * @see Label
     */
    @CheckForNull
    private final String label;
    /**
     * Docker image
     * @see ContainerDefinition#withImage(String)
     */
    @Nonnull
    private final String image;
    /**
     * Slave remote FS
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
     * Space delimited list of Docker entry points
     *
     * @see ContainerDefinition#withEntryPoint(String...)
     */
    @CheckForNull
    private String entrypoint;
    /**
      JVM arguments to start slave.jar
     */
    @CheckForNull
    private String jvmArgs;

    /**
      Container mount points, imported from volumes
     */
    private List<MountPointEntry> mountPoints;
    private List<VolumeFromEntry> volumesFrom;

    /**
      A list of tasks, linked to the current definition
          - Task definitions are managed using ECS
              - Task definitions can be locked to a specific revision (default latest)
              - The obvious disadvantage is, when changes are made to the ECS tasks, 
                the ECSTaskTemplate must be updated manually
          - The tasks' container definitions are copied to the new task definition
              - Each container will be linked to the new container definition
              - Each port mapping's host port will be set to 0 (random assignment)
          - All volumes, defined in ECS tasks, will be copied to the new task
     */
    private List<LinkedTaskEntry> linkedTasks;

    /**
     * Indicates whether the container should run in privileged mode
     */
    private final boolean privileged;

    private List<EnvironmentEntry> environments;
    private List<ExtraHostEntry> extraHosts;

    private String taskDefinitionArn;

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

    @DataBoundConstructor
    public ECSTaskTemplate(@Nullable String label,
                           @Nonnull String image,
                           @Nullable String remoteFSRoot,
                           int memory,
                           int cpu,
                           boolean privileged,
                           @Nullable List<LogDriverOption> logDriverOptions,
                           @Nullable List<EnvironmentEntry> environments,
                           @Nullable List<ExtraHostEntry> extraHosts,
                           @Nullable List<MountPointEntry> mountPoints,
                           @Nullable List<VolumeFromEntry> volumesFrom,
                           @Nullable List<LinkedTaskEntry> linkedTasks
                          ) {
        this.label = label;
        this.image = image;
        this.remoteFSRoot = remoteFSRoot;
        this.memory = memory;
        this.cpu = cpu;
        this.privileged = privileged;
        this.logDriverOptions = logDriverOptions;
        this.environments = environments;
        this.extraHosts = extraHosts;
        this.mountPoints = mountPoints;
        this.volumesFrom = volumesFrom;
        this.linkedTasks = linkedTasks;
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
    public void setLogDriver(String logDriver) {
        this.logDriver = StringUtils.trimToNull(logDriver);
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

    public boolean getPrivileged() {
        return privileged;
    }

    public String getLogDriver() {
        return logDriver;
    }

    public static class LogDriverOption extends AbstractDescribableImpl<LogDriverOption>{
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

    private Map<String,String> getLogDriverOptionsMap() {
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

    public String getTaskDefinitionArn() {
        return taskDefinitionArn;
    }

    public List<EnvironmentEntry> getEnvironments() {
        return environments;
    }

    public List<ExtraHostEntry> getExtraHosts() {
        return extraHosts;
    }

    public List<LinkedTaskEntry> getLinkedTasks() {
        return linkedTasks;
    }

    private Collection<ECSLinkedTask> getECSLinkedTasks(AmazonECSClient client) {
        if (null == linkedTasks || linkedTasks.isEmpty()) {
            return new ArrayList<ECSLinkedTask>();
        }
        Collection<ECSLinkedTask> items = new ArrayList<ECSLinkedTask>();
        for (LinkedTaskEntry linkedTask : linkedTasks) {
            ECSLinkedTask ecsLinked = new ECSLinkedTask(linkedTask.taskName, client);
            items.add(ecsLinked);
        }
        return items;
    }

    public static class ECSLinkedTask {
        public String taskName;
        public Collection<ContainerDefinition> containers = new ArrayList<ContainerDefinition>();
        public Collection<Volume> volumes = new ArrayList<Volume>();

        public ECSLinkedTask(String taskName, AmazonECSClient client) {
            this.taskName = taskName;

            try {
                DescribeTaskDefinitionRequest req = new DescribeTaskDefinitionRequest().
                    withTaskDefinition(taskName);
                TaskDefinition task = client.describeTaskDefinition(req).getTaskDefinition();
                for (ContainerDefinition container : task.getContainerDefinitions()) {
                    for (PortMapping p : container.getPortMappings()) {
                        p.setHostPort(0);
                    }
                    addContainerDefinition(container);
                }
                for (Volume volume : task.getVolumes()) {
                    addVolume(volume);
                }
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Exception getting task definition=" + taskName, e);
                throw e;
            }
        }

        public Collection<ContainerDefinition> getContainerDefinitions() {
            return containers;
        }

        public void addContainerDefinition(ContainerDefinition def) {
            containers.add(def);
        }

        public Collection<Volume> getVolumes() {
            return volumes;
        }

        public void addVolume(Volume vol) {
            volumes.add(vol);
        }
    }

    private Collection<KeyValuePair> getEnvironmentKeyValuePairs() {
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

    private Collection<HostEntry> getExtraHostEntries() {
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

    private Collection<Volume> getVolumeEntries() {
        Collection<Volume> vols = new ArrayList<Volume>();
        if (null == mountPoints || mountPoints.isEmpty())
            return vols;
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
        return vols;
    }

    private Collection<MountPoint> getMountPointEntries() {
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

    public List<VolumeFromEntry> getVolumesFrom() {
        return volumesFrom;
    }

    public Collection<VolumeFrom> getVolumesFromEntries() {
        if (null == volumesFrom || volumesFrom.isEmpty())
            return null;
        Collection<VolumeFrom> volumes = new ArrayList<VolumeFrom>();
        for (VolumeFromEntry vol : volumesFrom) {
            if (StringUtils.isEmpty(vol.sourceContainer))
                continue;
            volumes.add(new VolumeFrom().withSourceContainer(vol.sourceContainer)
                                        .withReadOnly(vol.readOnly));
        }
        return volumes;
    }

    public static class EnvironmentEntry extends AbstractDescribableImpl<EnvironmentEntry> {
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

    public static class VolumeFromEntry extends AbstractDescribableImpl<VolumeFromEntry> {
        public String sourceContainer;
        public Boolean readOnly;

        @DataBoundConstructor
        public VolumeFromEntry(String sourceContainer, Boolean readOnly) {
            this.sourceContainer = sourceContainer;
            this.readOnly = readOnly;
        }

        @Override
        public String toString() {
            return "VolumeFromEntry{sourceContainer:" + sourceContainer + ", readOnly:" + readOnly + "}";
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<VolumeFromEntry> {
            @Override
            public String getDisplayName() {
                return "VolumeFromEntry";
            }
        }
    }

    public static class ExtraHostEntry extends AbstractDescribableImpl<ExtraHostEntry> {
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

    public static class MountPointEntry extends AbstractDescribableImpl<MountPointEntry> {
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

    public static class LinkedTaskEntry extends AbstractDescribableImpl<LinkedTaskEntry> {
        public String name, revision, taskName;

        @DataBoundConstructor
        public LinkedTaskEntry(String name, String revision) {
            this.name = name;
            this.revision = revision;

            // check for the AWS ECS task revision number
            if (null == revision || revision.isEmpty())
                taskName = name;
            else
                taskName = name + ":" + revision;
        }

        @Override
        public String toString() {
            return "LinkedTaskEntry{name:" + name + "}";
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<LinkedTaskEntry> {
            @Override
            public String getDisplayName() {
                return "LinkedTaskEntry";
            }
        }
    }

    public Set<LabelAtom> getLabelSet() {
        return Label.parse(label);
    }

    public String getDisplayName() {
        return "ECS Slave " + label;
    }

    public RegisterTaskDefinitionRequest asRegisterTaskDefinitionRequest(AmazonECSClient client) {
        Collection<Volume> ecsTaskVolumes = new ArrayList<Volume>();
        Collection<ContainerDefinition> defs = new ArrayList<ContainerDefinition>();
        final ContainerDefinition def = new ContainerDefinition()
                .withName("jenkins-slave")
                .withImage(image)
                .withEnvironment(getEnvironmentKeyValuePairs())
                .withExtraHosts(getExtraHostEntries())
                .withMemory(memory)
                .withMountPoints(getMountPointEntries())
                .withVolumesFrom(getVolumesFromEntries())
                .withCpu(cpu)
                .withPrivileged(privileged);
        if (entrypoint != null)
            def.withEntryPoint(StringUtils.split(entrypoint));

        if (jvmArgs != null)
            def.withEnvironment(new KeyValuePair()
                .withName("JAVA_OPTS").withValue(jvmArgs))
                .withEssential(true);

        if (logDriver != null) {
            LogConfiguration logConfig = new LogConfiguration();
            logConfig.setLogDriver(logDriver);
            logConfig.setOptions(getLogDriverOptionsMap());
            def.withLogConfiguration(logConfig);
        }

        for (ECSLinkedTask linked : getECSLinkedTasks(client)) {
            for (ContainerDefinition d : linked.getContainerDefinitions()) {
                def.withLinks(d.getName());
                defs.add(d);
            }
            for (Volume v : linked.getVolumes()) {
                ecsTaskVolumes.add(v);
            }
        }
        defs.add(def);
        ecsTaskVolumes.addAll(getVolumeEntries());

        return new RegisterTaskDefinitionRequest()
            .withFamily("jenkins-slave")
            .withVolumes(ecsTaskVolumes)
            .withContainerDefinitions(defs);
    }

    public void setOwer(ECSCloud owner) {
        final AmazonECSClient client = owner.getAmazonECSClient();

        if (taskDefinitionArn == null) {
            final RegisterTaskDefinitionRequest req = asRegisterTaskDefinitionRequest(client);
            final RegisterTaskDefinitionResult result = client.registerTaskDefinition(req);
            taskDefinitionArn = result.getTaskDefinition().getTaskDefinitionArn();
            LOGGER.log(Level.FINE, "Slave {0} - Created Task Definition {1}: {2}", new Object[]{label, taskDefinitionArn, req});
            LOGGER.log(Level.INFO, "Slave {0} - Created Task Definition: {1}", new Object[] { label, taskDefinitionArn });
            getDescriptor().save();
        } else {
            LOGGER.log(Level.FINE, "Slave {0} - Task Definition already exists {1}", new Object[]{label, taskDefinitionArn});
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ECSTaskTemplate.class.getName());

    @Extension
    public static class DescriptorImpl extends Descriptor<ECSTaskTemplate> {

        @Override
        public String getDisplayName() {

            return Messages.Template();
        }
    }
}

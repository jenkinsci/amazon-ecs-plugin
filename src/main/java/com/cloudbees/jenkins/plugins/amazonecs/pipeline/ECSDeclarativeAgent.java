package com.cloudbees.jenkins.plugins.amazonecs.pipeline;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgent;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.jenkinsci.plugins.variant.OptionalExtension;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate;
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.EnvironmentEntry;
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.ExtraHostEntry;
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.LogDriverOption;
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.MountPointEntry;
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.PortMappingEntry;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ECSDeclarativeAgent extends DeclarativeAgent<ECSDeclarativeAgent> {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(ECSDeclarativeAgent.class.getName());

    private String label;
    private String cloud;
    private String taskDefinitionOverride;
    private String image;
    private String launchType;
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
    private List<LogDriverOption> logDriverOptions;
    private List<EnvironmentEntry> environments;
    private List<ExtraHostEntry> extraHosts;
    private List<MountPointEntry> mountPoints;
    private List<PortMappingEntry> portMappings;

    @DataBoundConstructor
    public ECSDeclarativeAgent() {
    }

    public String getLabel() {
        return label;
    }

    @DataBoundSetter
    public void setLabel(String label) {
        this.label = label;
    }

    public String getCloud() {
        return cloud;
    }

    @DataBoundSetter
    public void setCloud(String cloud) {
        this.cloud = cloud;
    }

    @DataBoundSetter
    public void setTaskDefinitionOverride(String taskDefinitionOverride) {
        this.taskDefinitionOverride = taskDefinitionOverride;
    }

    @DataBoundSetter
    public void setImage(String image) {
        this.image = image;
    }

    @DataBoundSetter
    public void setLaunchType(String launchType) {
        this.launchType = launchType;
    }

    @DataBoundSetter
    public void setRemoteFSRoot(String remoteFSRoot) {
        this.remoteFSRoot = remoteFSRoot;
    }

    @DataBoundSetter
    public void setMemory(int memory) {
        this.memory = memory;
    }

    @DataBoundSetter
    public void setMemoryReservation(int memoryReservation) {
        this.memoryReservation = memoryReservation;
    }

    @DataBoundSetter
    public void setCpu(int cpu) {
        this.cpu = cpu;
    }

    @DataBoundSetter
    public void setSubnets(String subnets) {
        this.subnets = subnets;
    }

    @DataBoundSetter
    public void setSecurityGroups(String securityGroups) {
        this.securityGroups = securityGroups;
    }

    @DataBoundSetter
    public void setAssignPublicIp(boolean assignPublicIp) {
        this.assignPublicIp = assignPublicIp;
    }

    @DataBoundSetter
    public void setPrivileged(boolean privileged) {
        this.privileged = privileged;
    }

    @DataBoundSetter
    public void setContainerUser(String containerUser) {
        this.containerUser = containerUser;
    }

    @DataBoundSetter
    public void setTaskrole(String taskrole) {
        this.taskrole = taskrole;
    }

    @DataBoundSetter
    public void setInheritFrom(String inheritFrom) {
        this.inheritFrom = inheritFrom;
    }

    @DataBoundSetter
    public void setLogDriverOptions(List<LogDriverOption> logDriverOptions) {
        this.logDriverOptions = logDriverOptions;
    }

    @DataBoundSetter
    public void setEnvironments(List<EnvironmentEntry> environments) {
        this.environments = environments;
    }

    @DataBoundSetter
    public void setExtraHosts(List<ExtraHostEntry> extraHosts) {
        this.extraHosts = extraHosts;
    }

    @DataBoundSetter
    public void setMountPoints(List<MountPointEntry> mountPoints) {
        this.mountPoints = mountPoints;
    }

    @DataBoundSetter
    public void setPortMappings(List<PortMappingEntry> portMappings) {
        this.portMappings = portMappings;
    }

    public Map<String,Object> getAsArgs() {
        Map<String,Object> argMap = new TreeMap<String, Object>();

        LOGGER.log(Level.INFO, "In getAsArgs. label: {0}", label);

        argMap.put("label", label);
        argMap.put("name", label);

        if (!StringUtils.isEmpty(cloud)) {
            argMap.put("cloud", cloud);
        }

        if (!StringUtils.isEmpty(taskDefinitionOverride)) {
            argMap.put("taskDefinitionOverride", taskDefinitionOverride);
        }

        if (!StringUtils.isEmpty(image)) {
            argMap.put("image", image);
        }

        if (!StringUtils.isEmpty(launchType)) {
            argMap.put("launchType", launchType);
        }

        if (!StringUtils.isEmpty(remoteFSRoot)) {
            argMap.put("remoteFSRoot", remoteFSRoot);
        }

        if (memory != 0) {
            argMap.put("memory", memory);
        }

        if (memoryReservation != 0) {
            argMap.put("memoryReservation", memoryReservation);
        }

        if (cpu != 0) {
            argMap.put("cpu", cpu);
        }

        if (!StringUtils.isEmpty(subnets)) {
            argMap.put("subnets", subnets);
        }

        if (!StringUtils.isEmpty(securityGroups)) {
            argMap.put("securityGroups", securityGroups);
        }

        argMap.put("assignPublicIp", assignPublicIp);
        argMap.put("privileged", privileged);

        if (!StringUtils.isEmpty(containerUser)) {
            argMap.put("containerUser", containerUser);
        }

        if (!StringUtils.isEmpty(taskrole)) {
            argMap.put("taskrole", taskrole);
        }

        if (!StringUtils.isEmpty(inheritFrom)) {
            argMap.put("inheritFrom", inheritFrom);
        }

        if (logDriverOptions.size() > 0) {
            argMap.put("logDriverOptions", logDriverOptions);
        }

        if (environments.size() > 0) {
            argMap.put("environments", environments);
        }

        if (extraHosts.size() > 0) {
            argMap.put("extraHosts", extraHosts);
        }

        if (mountPoints.size() > 0) {
            argMap.put("mountPoints", mountPoints);
        }

        if (portMappings.size() > 0) {
            argMap.put("portMappings", portMappings);
        }

        LOGGER.log(Level.INFO, "In getAsArgs. argMap: {0}", argMap.toString());

        return argMap;
    }

    @OptionalExtension(requirePlugins = "pipeline-model-extensions")
    @Symbol("ecs")
    public static class DescriptorImpl extends DeclarativeAgentDescriptor<ECSDeclarativeAgent> {
        @Override
        public String getDisplayName() {
            return "ECS Agent";
        }
    }
}

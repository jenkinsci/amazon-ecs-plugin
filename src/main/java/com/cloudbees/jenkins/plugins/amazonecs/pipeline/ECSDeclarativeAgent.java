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

import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.EnvironmentEntry;
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.ExtraHostEntry;
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.LogDriverOption;
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.MountPointEntry;
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.PortMappingEntry;

import java.util.ArrayList;
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
    private String executionRole;
    private String taskrole;
    private String inheritFrom;
    private String logDriver;
    private List<LogDriverOption> logDriverOptions;
    private List<EnvironmentEntry> environments;
    private List<ExtraHostEntry> extraHosts;
    private List<MountPointEntry> mountPoints;
    private List<PortMappingEntry> portMappings;

    private ArrayList<String> overrides = new ArrayList<String>();

    @DataBoundConstructor
    public ECSDeclarativeAgent() {
    }

    public String getLabel() {
        return label;
    }

    @DataBoundSetter
    public void setLabel(String label) {
        this.label = label;
        overrides.add("label");
    }

    public String getCloud() {
        return cloud;
    }

    @DataBoundSetter
    public void setCloud(String cloud) {
        this.cloud = cloud;
        overrides.add("cloud");
    }

    public String getTaskDefinitionOverride() {
        return taskDefinitionOverride;
    }

    @DataBoundSetter
    public void setTaskDefinitionOverride(String taskDefinitionOverride) {
        this.taskDefinitionOverride = taskDefinitionOverride;
        overrides.add("taskDefinitionOverride");
    }

    public String getImage() {
        return image;
    }

    @DataBoundSetter
    public void setImage(String image) {
        this.image = image;
        overrides.add("image");
    }

    public String getLaunchType() {
        return launchType;
    }

    @DataBoundSetter
    public void setLaunchType(String launchType) {
        this.launchType = launchType;
        overrides.add("launchType");
    }

    public String getRemoteFSRoot() {
        return remoteFSRoot;
    }

    @DataBoundSetter
    public void setRemoteFSRoot(String remoteFSRoot) {
        this.remoteFSRoot = remoteFSRoot;
        overrides.add("remoteFSRoot");
    }

    public int getMemory() {
        return memory;
    }

    @DataBoundSetter
    public void setMemory(int memory) {
        this.memory = memory;
        overrides.add("memory");
    }

    public int getMemoryReservation() {
        return memoryReservation;
    }

    @DataBoundSetter
    public void setMemoryReservation(int memoryReservation) {
        this.memoryReservation = memoryReservation;
        overrides.add("memoryReservation");
    }

    public int getCpu() {
        return cpu;
    }

    @DataBoundSetter
    public void setCpu(int cpu) {
        this.cpu = cpu;
        overrides.add("cpu");
    }

    public String getSubnets() {
        return subnets;
    }

    @DataBoundSetter
    public void setSubnets(String subnets) {
        this.subnets = subnets;
        overrides.add("subnets");
    }

    public String getSecurityGroups() {
        return securityGroups;
    }

    @DataBoundSetter
    public void setSecurityGroups(String securityGroups) {
        this.securityGroups = securityGroups;
        overrides.add("securityGroups");
    }

    public boolean getAssignPublicIp() {
        return assignPublicIp;
    }

    @DataBoundSetter
    public void setAssignPublicIp(boolean assignPublicIp) {
        this.assignPublicIp = assignPublicIp;
        overrides.add("assignPublicIp");
    }

    public boolean getPrivileged() {
        return privileged;
    }

    @DataBoundSetter
    public void setPrivileged(boolean privileged) {
        this.privileged = privileged;
        overrides.add("privileged");
    }

    public String getContainerUser() {
        return containerUser;
    }

    @DataBoundSetter
    public void setContainerUser(String containerUser) {
        this.containerUser = containerUser;
        overrides.add("containerUser");
    }

    public String getExecutionRole() {
        return executionRole;
    }

    @DataBoundSetter
    public void setExecutionRole(String executionRole) {
        this.executionRole = executionRole;
        overrides.add("executionRole");
    }

    public String getTaskrole() {
        return taskrole;
    }

    @DataBoundSetter
    public void setTaskrole(String taskrole) {
        this.taskrole = taskrole;
        overrides.add("taskrole");
    }

    public String getInheritFrom() {
        return inheritFrom;
    }

    @DataBoundSetter
    public void setInheritFrom(String inheritFrom) {
        this.inheritFrom = inheritFrom;
        overrides.add("inheritFrom");
    }

    public String getLogDriver() {
        return logDriver;
    }

    @DataBoundSetter
    public void setLogDriver(String logDriver) {
        this.logDriver = logDriver;
        overrides.add("logDriver");
    }

    public List<LogDriverOption> getLogDriverOptions() {
        return logDriverOptions;
    }

    @DataBoundSetter
    public void setLogDriverOptions(List<LogDriverOption> logDriverOptions) {
        this.logDriverOptions = logDriverOptions;
        overrides.add("logDriverOptions");
    }

    public List<EnvironmentEntry> getEnvironments() {
        return environments;
    }

    @DataBoundSetter
    public void setEnvironments(List<EnvironmentEntry> environments) {
        this.environments = environments;
        overrides.add("environments");
    }

    public List<ExtraHostEntry> getExtraHosts() {
        return extraHosts;
    }

    @DataBoundSetter
    public void setExtraHosts(List<ExtraHostEntry> extraHosts) {
        this.extraHosts = extraHosts;
        overrides.add("extraHosts");
    }

    public List<MountPointEntry> getMountPoints() {
        return mountPoints;
    }

    @DataBoundSetter
    public void setMountPoints(List<MountPointEntry> mountPoints) {
        this.mountPoints = mountPoints;
        overrides.add("mountPoints");
    }

    public List<PortMappingEntry> getPortMappings() {
        return portMappings;
    }

    @DataBoundSetter
    public void setPortMappings(List<PortMappingEntry> portMappings) {
        this.portMappings = portMappings;
        overrides.add("portMappings");
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

        if (!StringUtils.isEmpty(executionRole)) {
            argMap.put("executionRole", executionRole);
        }

        if (!StringUtils.isEmpty(taskrole)) {
            argMap.put("taskrole", taskrole);
        }

        if (!StringUtils.isEmpty(inheritFrom)) {
            argMap.put("inheritFrom", inheritFrom);
        }

        if (!StringUtils.isEmpty(logDriver)) {
            argMap.put("logDriver", logDriver);
        }

        if (logDriverOptions != null && logDriverOptions.size() > 0) {
            argMap.put("logDriverOptions", logDriverOptions);
        }

        if (environments != null && environments.size() > 0) {
            argMap.put("environments", environments);
        }

        if (extraHosts != null && extraHosts.size() > 0) {
            argMap.put("extraHosts", extraHosts);
        }

        if (extraHosts != null && mountPoints.size() > 0) {
            argMap.put("mountPoints", mountPoints);
        }

        if (extraHosts != null && portMappings.size() > 0) {
            argMap.put("portMappings", portMappings);
        }

        argMap.put("overrides", overrides);

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

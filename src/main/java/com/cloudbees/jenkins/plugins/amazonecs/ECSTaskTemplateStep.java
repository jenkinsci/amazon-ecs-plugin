package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.services.ecs.model.*;

import hudson.Extension;
import hudson.util.ListBoxModel;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.*;

import com.google.inject.Inject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.ServletException;

import java.util.*;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ECSTaskTemplateStep extends AbstractStepImpl {
    private static final Logger LOGGER = Logger.getLogger(ECSCloud.class.getName());

    private ECSTaskTemplate template;
    private ECSCloud cloud;
    private String cloudName;

    @DataBoundConstructor
    public ECSTaskTemplateStep(
        @Nonnull String cloudName,
        @Nonnull String templateName,
        @Nullable String label,
        @Nullable String taskDefinitionOverride,
        @Nonnull String image,
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
        @Nullable List<ECSTaskTemplate.LogDriverOption> logDriverOptions,
        @Nullable List<ECSTaskTemplate.EnvironmentEntry> environments,
        @Nullable List<ECSTaskTemplate.ExtraHostEntry> extraHosts,
        @Nullable List<ECSTaskTemplate.MountPointEntry> mountPoints,
        @Nullable List<ECSTaskTemplate.PortMappingEntry> portMappings) {
            this.cloudName = cloudName;
            try {
                this.cloud = (ECSCloud) Jenkins.get().clouds.getByName(cloudName);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Cannot create template for cloud {0}", cloudName);
                LOGGER.log(Level.WARNING, "Cloud {0} is not configured", cloudName);
            }
            this.template = new ECSTaskTemplate(
                templateName,
                label,
                taskDefinitionOverride,
                image,
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
                portMappings);        
    }

    public String getCloudName() {
        return cloudName;
    }

    public ECSCloud getCloud() {
        return cloud;
    }

    public ECSTaskTemplate getTemplate() {
        return template;
    }

    public String getTemplateName() {
        return template.getTemplateName();
    }

    public String getLabel() {
        return template.getLabel();
    }

    public String getTaskDefinitionOverride() {
        return template.getTaskDefinitionOverride();
    }

    public String getImage() {
        return template.getImage();
    }

    public String getLaunchType() {
        return template.getLaunchType();
    }

    public String getNetworkMode() {
        return template.getNetworkMode();
    }

    public String getRemoteFSRoot() {
        return template.getRemoteFSRoot();
    }

    public int getMemory() {
        return template.getMemory();
    }

    public int getMemoryReservation() {
        return template.getMemoryReservation();
    }

    public int getCpu() {
        return template.getCpu();
    }

    public String getSubnets() {
        return template.getSubnets();
    }

    public String getSecurityGroups() {
        return template.getSecurityGroups();
    }

    public boolean getAssignPublicIp() {
        return template.getAssignPublicIp();
    }

    public boolean getPrivileged() {
        return template.getPrivileged();
    }

    public String getContainerUser() {
        return template.getContainerUser();
    }

    public List<ECSTaskTemplate.LogDriverOption> getLogDriverOptions() {
        return template.getLogDriverOptions();
    }

    public List<ECSTaskTemplate.EnvironmentEntry> getEnvironments() {
        return template.getEnvironments();
    }

    public List<ECSTaskTemplate.ExtraHostEntry> getExtraHosts() {
        return template.getExtraHosts();
    }

    public List<ECSTaskTemplate.MountPointEntry> getMountPoints() {
        return template.getMountPoints();
    }

    public List<ECSTaskTemplate.PortMappingEntry> getPortMappings() {
        return template.getPortMappings();
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        private static String TEMPLATE_NAME_PATTERN = "[a-z|A-Z|0-9|_|-]{1,127}";

        @Override
        public String getFunctionName() {
            return "ecsCreateTaskTemplate";
        }

        @Override
        public String getDisplayName() {
            return "ECS Task Template Provisioning";
        }

        public DescriptorImpl() {
            super(ECSTaskTemplateExecution.class);
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

        public FormValidation doCheckTemplateName(@QueryParameter String value) throws IOException, ServletException {
            if (value.length() > 0 && value.length() <= 127 && value.matches(TEMPLATE_NAME_PATTERN)) {
                return FormValidation.ok();
            }
            return FormValidation.error(
                    "Up to 127 letters (uppercase and lowercase), numbers, hyphens, and underscores are allowed");
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

    private static class ECSTaskTemplateExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {

        @Inject
        private transient ECSTaskTemplateStep step;

        @Override
        protected Void run() throws Exception {
            if (step.cloud != null) {
                step.cloud.getTemplates().add(step.template);
                Jenkins.get().save();
            }
            return null;
        }

    }
}

package com.cloudbees.jenkins.plugins.amazonecs.pipeline;

import jenkins.model.Jenkins;
import hudson.slaves.Cloud;
import hudson.AbortException;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.annotation.Nonnull;
import com.cloudbees.jenkins.plugins.amazonecs.ECSCloud;
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.RandomStringUtils;


public class ECSTaskTemplateStepExecution extends AbstractStepExecutionImpl {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(ECSTaskTemplateStepExecution.class.getName());

    private static final transient String NAME_FORMAT = "%s-%s";

    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "not needed on deserialization")
    private final transient ECSTaskTemplateStep step;
    private @Nonnull String cloudName;

    private ECSTaskTemplate newTemplate = null;

    ECSTaskTemplateStepExecution(ECSTaskTemplateStep ecsTaskTemplateStep, StepContext context) {
        super(context);
        this.step = ecsTaskTemplateStep;
        this.cloudName = ecsTaskTemplateStep.getCloud();
    }

    @Override
    public boolean start() throws Exception {
        LOGGER.log(Level.FINE, "In ECSTaskTemplateExecution run()");
        LOGGER.log(Level.FINE, "cloud: {0}", this.cloudName);
        LOGGER.log(Level.FINE, "label: {0}", step.getLabel());
        String randString = RandomStringUtils.random(5, "bcdfghjklmnpqrstvwxz0123456789");
        String name = String.format(NAME_FORMAT, step.getName(), randString);

        Cloud cloud = null;
        String parentLabel = step.getInheritFrom();
        if (parentLabel != null) {
            for (Cloud c: Jenkins.get().clouds) {
                if (c instanceof ECSCloud) {
                    ECSCloud ecsCloud = (ECSCloud)c;
                    if (ecsCloud.canProvision(parentLabel)){
                        cloud = c;
                        cloudName = cloud.name;
                        break;
                    }
                }
            }
        } else {
            cloud = Jenkins.get().getCloud(this.cloudName);
        }

        if (cloud == null) {
            throw new AbortException(String.format("Cloud does not exist: %s", cloudName));
        }

        if (!(cloud instanceof ECSCloud)) {
            throw new AbortException(String.format("Cloud is not an ECS cloud: %s (%s)", cloudName,
                    cloud.getClass().getName()));
        }

        ECSCloud ecsCloud = (ECSCloud) cloud;
        checkAllowedOverrides(ecsCloud, step);
        checkResourceLimits(ecsCloud, step);

        newTemplate = new ECSTaskTemplate(name,
                                          step.getLabel(),
                                          step.getTaskDefinitionOverride(),
                                          step.getImage(),
                                          step.getRepositoryCredentials(),
                                          step.getLaunchType(),
                                          step.getNetworkMode(),
                                          step.getRemoteFSRoot(),
                                          step.getMemory(),
                                          step.getMemoryReservation(),
                                          step.getCpu(),
                                          step.getSubnets(),
                                          step.getSecurityGroups(),
                                          step.getAssignPublicIp(),
                                          step.getPrivileged(),
                                          step.getContainerUser(),
                                          step.getLogDriverOptions(),
                                          step.getEnvironments(),
                                          step.getExtraHosts(),
                                          step.getMountPoints(),
                                          step.getPortMappings(),
                                          step.getExecutionRole(),
                                          step.getTaskrole(),
                                          step.getInheritFrom());
        newTemplate.setLogDriver(step.getLogDriver());

        LOGGER.log(Level.INFO, "Registering task template with name {0}", new Object[] { newTemplate.getTemplateName() });
        ecsCloud.addDynamicTemplate(newTemplate);
        getContext().newBodyInvoker().withContext(step).withCallback(new ECSTaskTemplateCallback(newTemplate)).start();
        return false;
    }

    private void checkAllowedOverrides(ECSCloud cloud, ECSTaskTemplateStep step) throws AbortException {
        for (String override : step.getOverrides()) {
            if(!cloud.isAllowedOverride(override)) {
                LOGGER.log(Level.FINE, "Override {0} is not allowed", new Object[] { override });
                throw new AbortException(String.format("Not allowed to override %s. Allowed overrides are %s", override, cloud.getAllowedOverrides()));
            }
        }
    }

    private void checkResourceLimits(ECSCloud cloud, ECSTaskTemplateStep step) throws AbortException {
        LOGGER.log(Level.FINE, "Cloud maxCpu: {0}", cloud.getMaxCpu());
        LOGGER.log(Level.FINE, "Cloud maxMemory: {0}", cloud.getMaxMemory());
        LOGGER.log(Level.FINE, "Cloud maxMemoryReservation: {0}", cloud.getMaxMemoryReservation());
        LOGGER.log(Level.FINE, "Step cpu: {0}", step.getCpu());
        LOGGER.log(Level.FINE, "Step memory: {0}", step.getMemory());
        LOGGER.log(Level.FINE, "Step memoryReservation: {0}", step.getMemoryReservation());

        if(cloud.getMaxCpu() != 0 && cloud.getMaxCpu() < step.getCpu()) {
            throw new AbortException("cpu is higher than maximum configured in cloud");
        }

        if(cloud.getMaxMemory() != 0 && cloud.getMaxMemory() < step.getMemory()) {
            throw new AbortException("memory is higher than maximum configured in cloud");
        }

        if(cloud.getMaxMemoryReservation() != 0 && cloud.getMaxMemoryReservation() < step.getMemoryReservation()) {
            throw new AbortException("memoryReservation is higher than maximum configured in cloud");
        }
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        super.stop(cause);
    }

    /**
     * Re-inject the dynamic template when resuming the pipeline
     */
    @Override
    public void onResume() {
        super.onResume();
        Cloud c = Jenkins.get().getCloud(cloudName);
        if (c == null) {
            throw new RuntimeException(String.format("Cloud does not exist: %s", cloudName));
        }
        if (!(c instanceof ECSCloud)) {
            throw new RuntimeException(String.format("Cloud is not an ECS cloud: %s (%s)", cloudName,
                    c.getClass().getName()));
        }
        ECSCloud ecsCloud = (ECSCloud) c;
        ecsCloud.addDynamicTemplate(newTemplate);
    }

    private class ECSTaskTemplateCallback extends BodyExecutionCallback.TailCall {

        private static final long serialVersionUID = 6043919968776851324L;

        private final ECSTaskTemplate taskTemplate;

        private ECSTaskTemplateCallback(ECSTaskTemplate taskTemplate) {
            this.taskTemplate = taskTemplate;
        }

        @Override
        /**
         * Remove the template after step is done
         */
        protected void finished(StepContext context) throws Exception {
            Cloud c = Jenkins.get().getCloud(cloudName);
            if (c == null) {
                LOGGER.log(Level.WARNING, "Cloud {0} no longer exists, cannot delete task template {1}",
                        new Object[] { cloudName, taskTemplate.getTemplateName() });
                return;
            }
            if (c instanceof ECSCloud) {
                LOGGER.log(Level.INFO, "Removing task template {1} from cloud {0}",
                        new Object[] { c.name, taskTemplate.getTemplateName() });
                ECSCloud ecsCloud = (ECSCloud) c;
                ecsCloud.removeDynamicTemplate(taskTemplate);
            } else {
                LOGGER.log(Level.WARNING, "Cloud is not an ECSCloud: {0} {1}",
                        new String[] { c.name, c.getClass().getName() });
            }
        }
    }

}

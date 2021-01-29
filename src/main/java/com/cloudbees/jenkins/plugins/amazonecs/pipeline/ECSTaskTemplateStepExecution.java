package com.cloudbees.jenkins.plugins.amazonecs.pipeline;

import com.amazonaws.services.codedeploy.model.ECSService;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.cloudbees.jenkins.plugins.amazonecs.ECSCloud;
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate;
import com.cloudbees.jenkins.plugins.amazonecs.SerializableSupplier;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;
import org.apache.commons.lang.RandomStringUtils;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import javax.annotation.Nonnull;
import java.util.logging.Level;
import java.util.logging.Logger;


public class ECSTaskTemplateStepExecution extends AbstractStepExecutionImpl {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(ECSTaskTemplateStepExecution.class.getName());

    private static final transient String NAME_FORMAT = "%s-%s";

    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "not needed on deserialization")
    private final transient ECSTaskTemplateStep     step;
    private SerializableSupplier<Jenkins.CloudList> cloudSupplier;
    private @Nonnull String                         cloudName;

    private ECSTaskTemplate newTemplate = null;

    ECSTaskTemplateStepExecution(ECSTaskTemplateStep ecsTaskTemplateStep, StepContext context, SerializableSupplier<Jenkins.CloudList> cloudSupplier ) {
        super(context);
        this.step = ecsTaskTemplateStep;
        this.cloudName = ecsTaskTemplateStep.getCloud();
        this.cloudSupplier = cloudSupplier;
    }

    @Override
    public boolean start() throws Exception {
        LOGGER.log(Level.FINE, "In ECSTaskTemplateExecution run()");
        LOGGER.log(Level.FINE, "cloud: {0}", this.cloudName);
        LOGGER.log(Level.FINE, "label: {0}", step.getLabel());
        String randString = RandomStringUtils.random(5, "bcdfghjklmnpqrstvwxz0123456789");
        String name = String.format(NAME_FORMAT, step.getName(), randString);
        final String parentLabel = step.getInheritFrom();

        Cloud cloud = validateCloud(findCloud(parentLabel));
        cloudName = cloud.getDisplayName();
        ECSCloud ecsCloud = (ECSCloud) cloud;
        checkAllowedOverrides(ecsCloud, step);
        checkResourceLimits(ecsCloud, step);

        newTemplate = new ECSTaskTemplate(name,
                                          step.getLabel(),
                                          step.getTaskDefinitionOverride(),
                                          null,
                                          step.getImage(),
                                          step.getRepositoryCredentials(),
                                          step.getLaunchType(),
                                          step.getDefaultCapacityProvider(),
                                          step.getCapacityProviderStrategies(),
                                          step.getNetworkMode(),
                                          step.getRemoteFSRoot(),
                                          step.getUniqueRemoteFSRoot(),
                                          step.getPlatformVersion(),
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
                                          step.getPlacementStrategies(),
                                          step.getTaskrole(),
                                          step.getInheritFrom(),
                                          step.getSharedMemorySize());
        newTemplate.setLogDriver(step.getLogDriver());

        ECSTaskTemplate parentTemplate = ecsCloud.findParentTemplate(parentLabel);
        if(parentLabel != null && parentTemplate == null){
            LOGGER.log(Level.WARNING, "InheritFrom specified as '{0}' but its template was not found. Continuing without a parent.", new Object[]{parentLabel});
        }
        newTemplate  = newTemplate.merge(parentTemplate);

        LOGGER.log(Level.INFO, "Registering task template with name {0}", new Object[] { newTemplate.getTemplateName() });
        newTemplate = ecsCloud.addDynamicTemplate(newTemplate);
        getContext().newBodyInvoker().withContext(step).withCallback(new ECSTaskTemplateCallback(newTemplate)).start();
        return false;
    }

    private Cloud validateCloud(Cloud cloud) throws AbortException {
        if (cloud == null) {
            throw new AbortException(String.format(
                    "Unable to determine cloud configuration using: Labels: [%s], inheritFrom: '%s', Cloud: '%s'",
                    step.getLabel(), step.getInheritFrom(), cloudName
            ));
        }

        if (!(cloud instanceof ECSCloud)) {
            throw new AbortException(String.format("Cloud is not an ECS cloud: %s (%s)", cloudName,
                    cloud.getClass().getName()));
        }
        return cloud;
    }



    private Cloud findCloud(String parentLabel) {
        Cloud  cloud  = null;
        Jenkins.CloudList clouds = cloudSupplier.get();

        if (parentLabel != null) {

            for (Cloud c: clouds) {
                if (c instanceof ECSCloud) {
                    ECSCloud ecsCloud = (ECSCloud)c;
                    if (ecsCloud.canProvision(parentLabel)){
                        cloud = c;
                        break;
                    }
                }
            }
        } else {
            cloud = clouds.getByName(this.cloudName);
        }
        return cloud;
    }

    private void checkAllowedOverrides(ECSCloud cloud, ECSTaskTemplateStep step) throws AbortException {
        for (String override : step.getOverrides()) {
            if(!cloud.isAllowedOverride(override)) {
                LOGGER.log(Level.FINE, "Override {0} is not allowed", new Object[] { override });
                throw new AbortException(String.format(
                        "Cloud (%s): Not allowed to override '%s'. Allowed overrides are [%s]",
                        cloud.getDisplayName(), override, cloud.getAllowedOverrides()
                ));
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
        LOGGER.log(Level.FINE, "Step shareMemorySize: {0}", step.getSharedMemorySize());

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
        /*
          Remove the template after step is done
         */
        protected void finished(StepContext context) throws Exception {
            Cloud c = Jenkins.get().getCloud(cloudName);
            if (c == null) {
                LOGGER.log(Level.WARNING, "Cloud {0} no longer exists, cannot delete task template {1}",
                        new Object[] { cloudName, taskTemplate.getTemplateName() });
                return;
            }
            if (c instanceof ECSCloud) {
                ECSCloud ecsCloud = (ECSCloud) c;
                LOGGER.log(Level.INFO, "Removing task template {1} from internal map and AWS cloud {0}",
                    new Object[] { c.name, taskTemplate.getTemplateName() });
                ecsCloud.removeDynamicTemplate(taskTemplate);
            } else {
                LOGGER.log(Level.WARNING, "Cloud is not an ECSCloud: {0} {1}",
                        new String[] { c.name, c.getClass().getName() });
            }
        }
    }
}

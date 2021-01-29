package com.cloudbees.jenkins.plugins.amazonecs.pipeline;

import com.cloudbees.jenkins.plugins.amazonecs.ECSCloud;
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate;
import com.cloudbees.jenkins.plugins.amazonecs.SerializableSupplier;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

/**
 * @author cbongiorno on 2020-04-09.
 */
public class ECSTaskTemplateStepExecutionTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();
    private ECSTaskTemplateStep step;


    @Before
    public void setUp() throws Exception {


    }

    @Test
    public void testMerge() throws Exception {
        StepContext  context = mock(StepContext.class);
        BodyInvoker invoker    = mock(BodyInvoker.class);
        when(context.newBodyInvoker()).thenReturn(invoker);
        when(invoker.withCallback(any(BodyExecutionCallback.TailCall.class))).thenReturn(invoker);


        ECSCloud cloud = mock(ECSCloud.class);
        when(cloud.getDisplayName()).thenReturn("my-cloud");
        when(cloud.isAllowedOverride("image")).thenReturn(true);
        when(cloud.isAllowedOverride("taskRole")).thenReturn(true);

        ECSTaskTemplate parentTemplate = getTaskTemplate("parent-name", "parent-label");
        when(cloud.findParentTemplate(parentTemplate.getLabel())).thenReturn(parentTemplate);
        when(cloud.canProvision(parentTemplate.getLabel())).thenReturn(true);

        step = new ECSTaskTemplateStep("child-label", "child-name");

        step.setInheritFrom(parentTemplate.getLabel());

        Jenkins.CloudList clouds = new Jenkins.CloudList();
        clouds.add(cloud);
        step.setOverrides(Arrays.asList("image","taskRole"));

        ECSTaskTemplateStepExecution executionStep = new ECSTaskTemplateStepExecution(step, context, (SerializableSupplier<Jenkins.CloudList>) () -> clouds);
        Random r = new Random();
        ECSTaskTemplate expected = new ECSTaskTemplate(
                "template-name",
                "child-label",
                UUID.randomUUID().toString(),
                null,
                "image-override",
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                false,
                null,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                false,
                null,
                r.nextInt(123),
                r.nextInt(456),
                r.nextInt(1024),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                r.nextBoolean(),
                r.nextBoolean(),
                UUID.randomUUID().toString(),
                null,
                null,
                null,
                null,
                null,
                UUID.randomUUID().toString(),
                null,
                "override-task-role",
                null,
                r.nextInt(123));

        // Overriding the entrypoint is inconsistent... why? You can't do it in the step
//        expected.setEntrypoint("entrypoint-override");
        step.setInheritFrom(parentTemplate.getLabel());

        step.setLogDriver(expected.getLogDriver());

        step.setImage(expected.getImage());
        step.setLaunchType(expected.getLaunchType());
        step.setTaskDefinitionOverride(expected.getTaskDefinitionOverride());
        step.setRepositoryCredentials(expected.getRepositoryCredentials());
        step.setNetworkMode(expected.getNetworkMode());
        step.setRemoteFSRoot(expected.getRemoteFSRoot());
        step.setUniqueRemoteFSRoot(expected.getUniqueRemoteFSRoot());
        step.setPlatformVersion(expected.getPlatformVersion());
        step.setMemory(expected.getMemory());
        step.setMemoryReservation(expected.getMemoryReservation());
        step.setCpu(expected.getCpu());
        step.setSubnets(expected.getSubnets());
        step.setSecurityGroups(expected.getSecurityGroups());
        step.setAssignPublicIp(expected.getAssignPublicIp());
        step.setPrivileged(expected.getPrivileged());
        step.setContainerUser(expected.getContainerUser());
        step.setLogDriverOptions(expected.getLogDriverOptions());
        step.setEnvironments(expected.getEnvironments());
        step.setExtraHosts(expected.getExtraHosts());
        step.setMountPoints(expected.getMountPoints());
        step.setPortMappings(expected.getPortMappings());
        step.setExecutionRole(expected.getExecutionRole());
        step.setPlacementStrategies(expected.getPlacementStrategies());
        step.setTaskrole(expected.getTaskrole());
        step.setSharedMemorySize(expected.getSharedMemorySize());

        when(invoker.withContext(step)).thenReturn(invoker);
        executionStep.start();

        verify(cloud,times(1)).addDynamicTemplate(expected);



    }
    private ECSTaskTemplate getTaskTemplate() {
        return getTaskTemplate(UUID.randomUUID().toString(),UUID.randomUUID().toString());
    }
    private ECSTaskTemplate getTaskTemplate(String templateName, String label) {
        return new ECSTaskTemplate(
                templateName,
                label,
                "",
                null,
                "image",
                "repositoryCredentials",
                "launchType",
                false,
                null,
                "networkMode",
                "remoteFSRoot",
                false,
                null,
                0,
                0,
                0,
                null,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0);
    }
}

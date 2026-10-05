package com.cloudbees.jenkins.plugins.amazonecs;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.ClientException;
import com.amazonaws.services.ecs.model.DescribeTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.DescribeTaskDefinitionResult;
import com.amazonaws.services.ecs.model.EphemeralStorage;
import com.amazonaws.services.ecs.model.ListTagsForResourceRequest;
import com.amazonaws.services.ecs.model.ListTagsForResourceResult;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.TaskDefinition;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Whoever wrote the original class should implement some tests
 */
public class ECSServiceTest {

    private static final String CLOUD_NAME = "cloud";
    private static final String TASK_DEFINITION_ARN = "arn:aws:ecs:us-east-1:123456789012:task-definition/cloud-template:1";

    private AmazonECS client;
    private ECSService service;

    @Before
    public void setUp() {
        client = mock(AmazonECS.class);
        service = new ECSService(() -> client);
        when(client.listTagsForResource(any(ListTagsForResourceRequest.class)))
                .thenReturn(new ListTagsForResourceResult());
        when(client.registerTaskDefinition(any(RegisterTaskDefinitionRequest.class)))
                .thenAnswer(invocation -> {
                    RegisterTaskDefinitionRequest request = invocation.getArgument(0);
                    return new RegisterTaskDefinitionResult().withTaskDefinition(toTaskDefinition(request));
                });
    }

    @Test
    @Ignore
    public void testDescribeTask() {
    }

    @Test
    @Ignore
    public void testStopTask() {
//        ECSService service = new ECSService("us-east-1");
    }

    @Test
    public void registerTemplateReusesTaskDefinitionWhenEphemeralStorageIsUnchanged() {
        ECSTaskTemplate template = fargateTemplate(30);
        givenExistingTaskDefinition(registeredTaskDefinitionFor(template));

        TaskDefinition result = service.registerTemplate(CLOUD_NAME, template);

        verify(client, never()).registerTaskDefinition(any(RegisterTaskDefinitionRequest.class));
        assertEquals(Integer.valueOf(30), result.getEphemeralStorage().getSizeInGiB());
    }

    @Test
    public void registerTemplateRegistersNewRevisionWhenEphemeralStorageIsAdded() {
        // Existing revision was registered before ephemeral storage was configured on the template.
        TaskDefinition existing = registeredTaskDefinitionFor(fargateTemplate(null));
        assertNull(existing.getEphemeralStorage());
        givenExistingTaskDefinition(existing);

        TaskDefinition result = service.registerTemplate(CLOUD_NAME, fargateTemplate(30));

        assertEquals(Integer.valueOf(30), registeredRequest().getEphemeralStorage().getSizeInGiB());
        assertEquals(Integer.valueOf(30), result.getEphemeralStorage().getSizeInGiB());
    }

    @Test
    public void registerTemplateRegistersNewRevisionWhenEphemeralStorageIsChanged() {
        givenExistingTaskDefinition(registeredTaskDefinitionFor(fargateTemplate(30)));

        service.registerTemplate(CLOUD_NAME, fargateTemplate(60));

        assertEquals(Integer.valueOf(60), registeredRequest().getEphemeralStorage().getSizeInGiB());
    }

    @Test
    public void registerTemplateRegistersNewRevisionWhenEphemeralStorageIsRemoved() {
        givenExistingTaskDefinition(registeredTaskDefinitionFor(fargateTemplate(30)));

        service.registerTemplate(CLOUD_NAME, fargateTemplate(null));

        assertNull(registeredRequest().getEphemeralStorage());
    }

    @Test
    public void registerTemplateIgnoresEphemeralStorageForNonFargateTemplates() {
        ECSTaskTemplate template = template("EC2", 30);
        TaskDefinition existing = registeredTaskDefinitionFor(template);
        assertNull(existing.getEphemeralStorage());
        givenExistingTaskDefinition(existing);

        service.registerTemplate(CLOUD_NAME, template);

        verify(client, never()).registerTaskDefinition(any(RegisterTaskDefinitionRequest.class));
    }

    /**
     * Registers the template against an empty family and returns the resulting task definition, i.e. exactly
     * what the plugin would have created for it. Resets the client interactions afterwards.
     */
    private TaskDefinition registeredTaskDefinitionFor(ECSTaskTemplate template) {
        when(client.describeTaskDefinition(any(DescribeTaskDefinitionRequest.class)))
                .thenThrow(new ClientException("not found"));
        TaskDefinition taskDefinition = service.registerTemplate(CLOUD_NAME, template);
        setUp(); // fresh client/service so the test only observes its own interactions
        return taskDefinition;
    }

    private void givenExistingTaskDefinition(TaskDefinition existing) {
        when(client.describeTaskDefinition(any(DescribeTaskDefinitionRequest.class)))
                .thenReturn(new DescribeTaskDefinitionResult().withTaskDefinition(existing));
    }

    private RegisterTaskDefinitionRequest registeredRequest() {
        ArgumentCaptor<RegisterTaskDefinitionRequest> captor = ArgumentCaptor.forClass(RegisterTaskDefinitionRequest.class);
        verify(client, times(1)).registerTaskDefinition(captor.capture());
        return captor.getValue();
    }

    private static TaskDefinition toTaskDefinition(RegisterTaskDefinitionRequest request) {
        TaskDefinition taskDefinition = new TaskDefinition()
                .withTaskDefinitionArn(TASK_DEFINITION_ARN)
                .withFamily(request.getFamily())
                .withContainerDefinitions(request.getContainerDefinitions())
                .withVolumes(request.getVolumes())
                .withNetworkMode(request.getNetworkMode())
                .withTaskRoleArn(request.getTaskRoleArn())
                .withExecutionRoleArn(request.getExecutionRoleArn())
                .withCpu(request.getCpu())
                .withMemory(request.getMemory());
        EphemeralStorage ephemeralStorage = request.getEphemeralStorage();
        if (ephemeralStorage != null) {
            taskDefinition.withEphemeralStorage(new EphemeralStorage().withSizeInGiB(ephemeralStorage.getSizeInGiB()));
        }
        return taskDefinition;
    }

    private static ECSTaskTemplate fargateTemplate(Integer ephemeralStorageSizeInGiB) {
        return template("FARGATE", ephemeralStorageSizeInGiB);
    }

    private static ECSTaskTemplate template(String launchType, Integer ephemeralStorageSizeInGiB) {
        return new ECSTaskTemplate(
                "template",
                "label",
                null,
                null,
                null,
                "image",
                null,
                launchType,
                "LINUX",
                "X86_64",
                false,
                null,
                "awsvpc",
                "/home/jenkins",
                false,
                "LATEST",
                0,
                2048,
                1024,
                ephemeralStorageSizeInGiB,
                "subnet-1",
                "sg-1",
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
                "arn:aws:iam::123456789012:role/execution",
                null,
                "arn:aws:iam::123456789012:role/task",
                null,
                0,
                false);
    }
}

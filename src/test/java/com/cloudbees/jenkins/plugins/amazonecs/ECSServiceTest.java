package com.cloudbees.jenkins.plugins.amazonecs;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.ClientException;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.DescribeTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.ResourceRequirement;
import com.amazonaws.services.ecs.model.ResourceType;
import com.amazonaws.services.ecs.model.TaskDefinition;


/**
 * Whoever wrote the original class should implement some tests
 */
public class ECSServiceTest {

    private AmazonECS client;
    private ECSService service;

    @Before
    public void setUp() {
        client = mock(AmazonECS.class);
        service = new ECSService(() -> client);
        // No task definition exists yet, so registerTemplate must register a new one.
        when(client.describeTaskDefinition(any(DescribeTaskDefinitionRequest.class)))
                .thenThrow(new ClientException("Unable to describe task definition."));
        when(client.registerTaskDefinition(any(RegisterTaskDefinitionRequest.class)))
                .thenReturn(new RegisterTaskDefinitionResult().withTaskDefinition(new TaskDefinition()));
    }

    private ECSTaskTemplate getTemplate() {
        return new ECSTaskTemplate(
                "template-name", "template-label",
                null, null, null, "image", null, "EC2", null, null, false, null, "bridge", "/home/jenkins",
                false, null, 0, 1024, 512, null, null, null, false, false,
                null, null, null, new ArrayList<>(), null, null, null, null, null, null, null, null, null, null, 0, false);
    }

    private ContainerDefinition registeredContainerDefinition() {
        ArgumentCaptor<RegisterTaskDefinitionRequest> captor = ArgumentCaptor.forClass(RegisterTaskDefinitionRequest.class);
        verify(client).registerTaskDefinition(captor.capture());
        return captor.getValue().getContainerDefinitions().get(0);
    }

    @Test
    public void registerTemplateWithGpuAddsResourceRequirement() {
        ECSTaskTemplate template = getTemplate();
        template.setGpu(2);

        service.registerTemplate("cloud", template);

        List<ResourceRequirement> requirements = registeredContainerDefinition().getResourceRequirements();
        assertEquals(1, requirements.size());
        assertEquals(ResourceType.GPU.toString(), requirements.get(0).getType());
        assertEquals("2", requirements.get(0).getValue());
    }

    @Test
    public void registerTemplateWithoutGpuHasNoResourceRequirement() {
        service.registerTemplate("cloud", getTemplate());

        List<ResourceRequirement> requirements = registeredContainerDefinition().getResourceRequirements();
        assertTrue(requirements == null || requirements.isEmpty());
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
}

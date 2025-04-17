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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.AmazonECSClientBuilder;
import com.amazonaws.services.ecs.model.*;
import com.amazonaws.services.ecs.waiters.AmazonECSWaiters;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.waiters.FixedDelayStrategy;
import com.amazonaws.waiters.PollingStrategy;
import com.amazonaws.waiters.Waiter;
import com.amazonaws.waiters.WaiterParameters;
import com.cloudbees.jenkins.plugins.amazonecs.aws.BaseAWSService;
import com.cloudbees.jenkins.plugins.amazonecs.aws.MaxTimeRetryStrategy;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;

import hudson.AbortException;
import hudson.slaves.SlaveComputer;

/**
 * Encapsulates interactions with Amazon ECS.
 *
 * @author Jan Roehrich {@literal <jan@roehrich.info> }
 */
public class ECSService extends BaseAWSService {
    private static final Logger LOGGER = Logger.getLogger(ECSCloud.class.getName());

    private static final String AWS_TAG_JENKINS_LABEL_KEY = "jenkins.label";
    private static final String AWS_TAG_JENKINS_TEMPLATENAME_KEY = "jenkins.templatename";

    @Nonnull
    private final Supplier<AmazonECS> clientSupplier;

    public ECSService(String credentialsId, String assumedRoleArn, String authRegion, String regionName) {
        this.clientSupplier = () -> {
            AmazonECSClientBuilder builder = AmazonECSClientBuilder
                    .standard()
                    .withClientConfiguration(createClientConfiguration())
                    .withRegion(regionName);

            AmazonWebServicesCredentials credentials = getCredentials(credentialsId);
            if (credentials != null) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    String awsAccessKeyId = credentials.getCredentials().getAWSAccessKeyId();
                    String obfuscatedAccessKeyId = StringUtils.left(awsAccessKeyId, 4) + StringUtils.repeat("*", awsAccessKeyId.length() - (2 * 4)) + StringUtils.right(awsAccessKeyId, 4);
                    LOGGER.log(Level.FINE, "Connect to Amazon ECS with IAM Access Key {1}", new Object[]{obfuscatedAccessKeyId});
                }
                builder
                        .withCredentials(credentials);
            }
            else if (StringUtils.isNotBlank(assumedRoleArn)) {
		if (StringUtils.isNotBlank(authRegion)) {
                    builder.withCredentials(getCredentialsForRole(assumedRoleArn, authRegion));
		}
		else {
                    builder.withCredentials(getCredentialsForRole(assumedRoleArn, regionName));
		}

            }

            LOGGER.log(Level.FINE, "Selected Region: {0}", regionName);

            return builder.build();
        };
    }

    @CheckForNull
    private AWSStaticCredentialsProvider getCredentialsForRole(String roleArn, String regionName) {
        AWSSecurityTokenService stsClient = AWSSecurityTokenServiceClientBuilder.standard()
                .withRegion(regionName)
                .build();

        AssumeRoleRequest roleRequest = new AssumeRoleRequest()
                .withRoleArn(roleArn)
                .withRoleSessionName("jenkins-role-session");

        AssumeRoleResult roleResponse = stsClient.assumeRole(roleRequest);
        Credentials sessionCredentials = roleResponse.getCredentials();

        BasicSessionCredentials awsCredentials = new BasicSessionCredentials(
                sessionCredentials.getAccessKeyId(),
                sessionCredentials.getSecretAccessKey(),
                sessionCredentials.getSessionToken());

        return new AWSStaticCredentialsProvider(awsCredentials);
    }

    public ECSService(Supplier<AmazonECS> clientSupplier) {
        this.clientSupplier = clientSupplier;
    }

    AmazonECS getAmazonECSClient() {
        return clientSupplier.get();
    }

    public Task describeTask(String taskArn, String clusterArn) {
        final AmazonECS client = clientSupplier.get();

        DescribeTasksResult result = client.describeTasks(new DescribeTasksRequest().withCluster(clusterArn).withTasks(taskArn));
        if (result.getTasks().size() == 0) {
            return null;
        } else {
            return result.getTasks().get(0);
        }
    }

    public void waitForTasksRunning(String tasksArn, String clusterArn, long timeoutInMillis, int DelayBetweenPollsInSeconds) {
        final AmazonECS client = clientSupplier.get();

        Waiter<DescribeTasksRequest> describeTaskWaiter = new AmazonECSWaiters(client).tasksRunning();

        describeTaskWaiter.run(new WaiterParameters<DescribeTasksRequest>(
            new DescribeTasksRequest()
                .withTasks(tasksArn)
                .withCluster(clusterArn)
                .withSdkClientExecutionTimeout((int)timeoutInMillis))
            .withPollingStrategy(new PollingStrategy(new MaxTimeRetryStrategy(timeoutInMillis), new FixedDelayStrategy(DelayBetweenPollsInSeconds))));
    }

    public void stopTask(String taskArn, String clusterArn) {
        final AmazonECS client = clientSupplier.get();

        LOGGER.log(Level.INFO, "Delete ECS agent task: {0}", taskArn);
        try {
            client.stopTask(new StopTaskRequest().withTask(taskArn).withCluster(clusterArn).withReason("Stopped by Jenkins Amazon ECS PlugIn"));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Couldn't stop task arn " + taskArn + " caught exception: " + e.getMessage(), e);
        }
    }

    /**
     * Looks whether the latest task definition matches the desired one. If yes, returns the full TaskDefinition of the existing one.
     * If no, register a new task definition with desired parameters and returns the new TaskDefinition.
     * If a TaskDefinitionOverride is set, we only look to see if the task definition exists and return it.
     */
    TaskDefinition registerTemplate(final String cloudName, final ECSTaskTemplate template) {
        if (template.getTaskDefinitionOverride() != null){
            TaskDefinition overrideTaskDefinition = findTaskDefinition(template.getTaskDefinitionOverride());
            if (overrideTaskDefinition == null) {
                LOGGER.log(Level.SEVERE, "Could not find task definition override: {0} for template: {1}", new Object[] {template.getTaskDefinitionOverride(), template.getDisplayName()});
                throw new RuntimeException("Could not find task definition override family or ARN: " + template.getTaskDefinitionOverride());
            }

            LOGGER.log(Level.FINE, "Found task definition override: {0}", new Object[] {overrideTaskDefinition.getTaskDefinitionArn()});
            return overrideTaskDefinition;
        }

        if (template.getDynamicTaskDefinition() != null){
            TaskDefinition overrideTaskDefinition = findTaskDefinition(template.getDynamicTaskDefinition());
            if (overrideTaskDefinition != null) {
                LOGGER.log(Level.FINE, "Found dynamic agent task definition: {0}", new Object[] {overrideTaskDefinition.getTaskDefinitionArn()});
                return overrideTaskDefinition;
            }

            LOGGER.log(Level.WARNING, "Could not find dynamic agent's task definition family or ARN: {0}, creating a new one.", new Object[] {template.getDynamicTaskDefinition()});
        }

        final AmazonECS client = clientSupplier.get();

        String familyName = fullQualifiedTemplateName(cloudName, template);
        final ContainerDefinition def = new ContainerDefinition()
                .withName(familyName)
                .withImage(template.getImage())
                .withEnvironment(template.getEnvironmentKeyValuePairs())
                .withExtraHosts(template.getExtraHostEntries())
                .withMountPoints(template.getMountPointEntries())
                .withPortMappings(template.getPortMappingEntries())
                .withCpu(template.getCpu())
                .withUlimits(template.getUlimitsEntries())
                .withPrivileged(template.getPrivileged())
                .withEssential(true);

        /*
            at least one of memory or memoryReservation has to be set
            the form validation will highlight if the settings are inappropriate
        */
        if (template.getMemoryReservation() > 0) /* this is the soft limit */
            def.withMemoryReservation(template.getMemoryReservation());

        if (template.getMemory() > 0) /* this is the hard limit */
            def.withMemory(template.getMemory());

        if (template.getDnsSearchDomains() != null)
            def.withDnsSearchDomains(StringUtils.split(template.getDnsSearchDomains()));

        if (template.getEntrypoint() != null)
            def.withEntryPoint(StringUtils.split(template.getEntrypoint()));

        if (template.getJvmArgs() != null)
            def.withEnvironment(new KeyValuePair()
                    .withName("JAVA_OPTS").withValue(template.getJvmArgs()))
                    .withEssential(true);

        if (template.getContainerUser() != null)
            def.withUser(template.getContainerUser());

        if (template.getKernelCapabilities() != null) {
            def.withLinuxParameters(new LinuxParameters().withCapabilities(new KernelCapabilities().withAdd(Arrays.asList(template.getKernelCapabilities().split(",")))));
        }

        if (template.getRepositoryCredentials() != null)
            def.withRepositoryCredentials(new RepositoryCredentials().withCredentialsParameter(template.getRepositoryCredentials()));

        if (template.getLogDriver() != null) {
            LogConfiguration logConfig = new LogConfiguration();
            logConfig.setLogDriver(template.getLogDriver());
            logConfig.setOptions(template.getLogDriverOptionsMap());
            def.withLogConfiguration(logConfig);
        }

        if (template.getSharedMemorySize() > 0) {
            def.withLinuxParameters(new LinuxParameters().withSharedMemorySize(template.getSharedMemorySize()));
        }

        TaskDefinition currentTaskDefinition = findTaskDefinition(familyName);

        boolean templateTagsMatchesExistingTags = false;
        boolean templateMatchesExistingContainerDefinition = false;
        boolean templateMatchesExistingVolumes = false;
        boolean templateMatchesExistingTaskRole = false;
        boolean templateMatchesExistingExecutionRole = false;
        boolean templateMatchesExistingNetworkMode = false;

        if (currentTaskDefinition != null) {
            final ContainerDefinition currentContainerDefinition = currentTaskDefinition.getContainerDefinitions().get(0);
            final List<Tag> tags = getTaskDefinitionTags(currentTaskDefinition.getTaskDefinitionArn());

            templateTagsMatchesExistingTags = ObjectUtils.equals(template.getTags(), tags);
            LOGGER.log(Level.INFO, "Match on tags: {0}", new Object[]{templateTagsMatchesExistingTags});
            LOGGER.log(Level.FINE, "Match on tags: {0}; template={1}; last={2}", new Object[]{templateTagsMatchesExistingTags, template.getTags(), tags});

            templateMatchesExistingContainerDefinition = def.equals(currentContainerDefinition);
            LOGGER.log(Level.INFO, "Match on container definition: {0}", new Object[]{templateMatchesExistingContainerDefinition});
            LOGGER.log(Level.FINE, "Match on container definition: {0}; template={1}; last={2}", new Object[]{templateMatchesExistingContainerDefinition, def, currentContainerDefinition});

            templateMatchesExistingVolumes = ObjectUtils.equals(template.getVolumeEntries(), currentTaskDefinition.getVolumes());
            LOGGER.log(Level.INFO, "Match on volumes: {0}", new Object[]{templateMatchesExistingVolumes});
            LOGGER.log(Level.FINE, "Match on volumes: {0}; template={1}; last={2}", new Object[]{templateMatchesExistingVolumes, template.getVolumeEntries(), currentTaskDefinition.getVolumes()});

            templateMatchesExistingTaskRole = StringUtils.equals(StringUtils.defaultString(template.getTaskrole()), StringUtils.defaultString(currentTaskDefinition.getTaskRoleArn()));
            LOGGER.log(Level.INFO, "Match on task role: {0}", new Object[]{templateMatchesExistingTaskRole});
            LOGGER.log(Level.FINE, "Match on task role: {0}; template={1}; last={2}", new Object[]{templateMatchesExistingTaskRole, template.getTaskrole(), currentTaskDefinition.getTaskRoleArn()});

            templateMatchesExistingExecutionRole = StringUtils.equals(StringUtils.defaultString(template.getExecutionRole()), StringUtils.defaultString(currentTaskDefinition.getExecutionRoleArn()));
            LOGGER.log(Level.INFO, "Match on execution role: {0}", new Object[]{templateMatchesExistingExecutionRole});
            LOGGER.log(Level.FINE, "Match on execution role: {0}; template={1}; last={2}", new Object[]{templateMatchesExistingExecutionRole, template.getExecutionRole(), currentTaskDefinition.getExecutionRoleArn()});

            //Compare to null if it is default network mode is selected
            String templateNetworkMode = "";
            if (StringUtils.equals(StringUtils.defaultString(template.getNetworkMode()), "default")) {
                templateMatchesExistingNetworkMode = null == currentTaskDefinition.getNetworkMode();
                templateNetworkMode = "null";
            } else {
                templateMatchesExistingNetworkMode = StringUtils.equals(StringUtils.defaultString(template.getNetworkMode()), StringUtils.defaultString(currentTaskDefinition.getNetworkMode()));
                templateNetworkMode = template.getNetworkMode();
            }

            LOGGER.log(Level.INFO, "Match on network mode: {0}", new Object[]{templateMatchesExistingNetworkMode});
            LOGGER.log(Level.FINE, "Match on network mode: {0}; template={1}; last={2}", new Object[]{templateMatchesExistingNetworkMode, templateNetworkMode, currentTaskDefinition.getNetworkMode()});
        }

        if (templateMatchesExistingContainerDefinition && templateMatchesExistingVolumes && templateMatchesExistingTaskRole && templateMatchesExistingExecutionRole && templateMatchesExistingNetworkMode) {
            LOGGER.log(Level.FINE, "Task Definition already exists: {0}", new Object[]{currentTaskDefinition.getTaskDefinitionArn()});
            return currentTaskDefinition;
        } else {
            Tag jenkinsLabelTag = new Tag().withKey(AWS_TAG_JENKINS_LABEL_KEY).withValue(template.getLabel());
            Tag jenkinsTemplateNameTag =
                    new Tag().withKey(AWS_TAG_JENKINS_TEMPLATENAME_KEY).withValue(template.getTemplateName());
            List<Tag> taskDefinitionTags = new ArrayList<>();
            taskDefinitionTags.add(jenkinsLabelTag);
            taskDefinitionTags.add(jenkinsTemplateNameTag);
            if (template.getTags() != null) {
                for (ECSTaskTemplate.Tag tag: template.getTags()) {
                    taskDefinitionTags.add(new Tag().withKey(tag.name).withValue(tag.value));
                }
            }

            final RegisterTaskDefinitionRequest request = new RegisterTaskDefinitionRequest()
                    .withFamily(familyName)
                    .withVolumes(template.getVolumeEntries())
                    .withTags(taskDefinitionTags)
                    .withContainerDefinitions(def);

            //If network mode is default, that means Null in the request, so do not set.
            if (!StringUtils.equals(StringUtils.defaultString(template.getNetworkMode()), "default")) {
                request.withNetworkMode(template.getNetworkMode());
            }

            if (!StringUtils.isEmpty(template.getExecutionRole())) {
                request.withExecutionRoleArn(template.getExecutionRole());
            }

            if (!StringUtils.isEmpty(template.getTaskrole())) {
                request.withTaskRoleArn(template.getTaskrole());
            }

            if (template.isFargate()) {
                request
                        .withRuntimePlatform(new RuntimePlatform()
                                .withOperatingSystemFamily(template.getOperatingSystemFamily())
                                .withCpuArchitecture(template.getCpuArchitecture()))
                        .withRequiresCompatibilities(LaunchType.FARGATE.toString())
                        .withNetworkMode(NetworkMode.Awsvpc.toString())
                        .withMemory(String.valueOf(template.getMemoryConstraint()))
                        .withCpu(String.valueOf(template.getCpu()));
                if (template.getEphemeralStorageSizeInGiB() != null && template.getEphemeralStorageSizeInGiB() > 0) {
                    request.withEphemeralStorage(new EphemeralStorage()
                            .withSizeInGiB(template.getEphemeralStorageSizeInGiB())
                    );
                }
            }

            final RegisterTaskDefinitionResult result = client.registerTaskDefinition(request);
            LOGGER.log(Level.FINE, "Created Task Definition {0}: {1}", new Object[]{result.getTaskDefinition(), request});
            LOGGER.log(Level.INFO, "Created Task Definition: {0}", new Object[]{result.getTaskDefinition()});

            if (template.getDynamicTaskDefinition() != null){
                // if we couldn't find the the dynamic task definition earlier, we'll set it
                // again here so it gets cleaned up once the task is finished
                template.setDynamicTaskDefinition(result.getTaskDefinition().getTaskDefinitionArn());
            }
            return result.getTaskDefinition();
        }
    }

    /**
     * Deregisters a task definition created for a template we are deleting.
     * It's expected that taskDefinitionArn is set
     * We don't attempt to de-register anything if TaskDefinitionOverride isn't null
     *
     * @param template       The template used to create the task definition
     * @return The task definition if found, otherwise null
     */
    void removeTemplate(final ECSTaskTemplate template) {
        AmazonECS client = clientSupplier.get();

        //no task definition was created for this template to delete
        if (template.getTaskDefinitionOverride() != null) {
            return;
        }

        String taskDefinitionArn = template.getDynamicTaskDefinition();
        try {
            if (taskDefinitionArn != null) {
                client.deregisterTaskDefinition(
                        new DeregisterTaskDefinitionRequest().withTaskDefinition(taskDefinitionArn));
            }

        } catch (ClientException e) {
            LOGGER.log(Level.WARNING, "Error de-registering task definition: " + taskDefinitionArn, e);
        }
    }

    /**
     * Finds the task definition for the specified family or ARN, or null if none is found.
     * The parameter may be a task definition family, family with revision, or full task definition ARN.
     */
    TaskDefinition findTaskDefinition(String familyOrArn) {
        AmazonECS client = clientSupplier.get();

        try {
            DescribeTaskDefinitionResult result = client.describeTaskDefinition(
                    new DescribeTaskDefinitionRequest().withTaskDefinition(familyOrArn)
            );

            return result.getTaskDefinition();
        } catch (ClientException e) {
            LOGGER.log(Level.FINE, "No existing task definition found for family or ARN: " + familyOrArn, e);
            LOGGER.log(Level.INFO, "No existing task definition found for family or ARN: " + familyOrArn);

            return null;
        }
    }

    List<Tag> getTaskDefinitionTags(String taskDefinitionArn){
        AmazonECS client = clientSupplier.get();

        try {
            ListTagsForResourceResult tagsResult = client.listTagsForResource(
                    new ListTagsForResourceRequest().withResourceArn(taskDefinitionArn)
            );

            return tagsResult.getTags();
        } catch (ClientException e) {
            LOGGER.log(Level.FINE, "No existing task definition found for ARN: " + taskDefinitionArn, e);
            LOGGER.log(Level.INFO, "No existing task definition found for ARN: " + taskDefinitionArn);

            return null;
        }
    }

    private String fullQualifiedTemplateName(final String cloudName, final ECSTaskTemplate template) {
        return cloudName.replaceAll("\\s+", "") + '-' + template.getTemplateName();
    }

    RunTaskResult runEcsTask(final ECSSlave agent, final ECSTaskTemplate template, String clusterArn, Collection<String> command, TaskDefinition taskDefinition) throws IOException, AbortException {
        AmazonECS client = clientSupplier.get();
        agent.setTaskDefinitonArn(taskDefinition.getTaskDefinitionArn());

        SlaveComputer agentComputer = agent.getComputer();

        if (agentComputer == null) {
            throw new IllegalStateException("Node was deleted, computer is null");
        }

        KeyValuePair envNodeName = new KeyValuePair();
        envNodeName.setName("SLAVE_NODE_NAME");
        envNodeName.setValue(agentComputer.getName());

        KeyValuePair envNodeSecret = new KeyValuePair();
        envNodeSecret.setName("SLAVE_NODE_SECRET");
        envNodeSecret.setValue(agentComputer.getJnlpMac());

        // by convention, unless agent container name is specified, we assume the jenkins agent container is the first
        // container in the task definition. ECS requires all task definitions to contain at least one container, and
        // all containers to have a name, so we do not need to null- or bounds-check for the presence of a container
        // definition.
        String agentContainerName;
        if (template.getAgentContainerName() != null) {
            agentContainerName = template.getAgentContainerName();

            if (!taskDefinition.getContainerDefinitions().stream().anyMatch(d -> d.getName().equals(agentContainerName))) {
                LOGGER.log(Level.SEVERE, "Could not find agent container name: {0} for template: {1}", new Object[] {agentContainerName, template.getDisplayName()});
                throw new RuntimeException("Could not find agent container name: " + agentContainerName);
            }
            LOGGER.log(Level.FINE, "Using the following container name as the Jenkins agent: {0}", agentContainerName);
        } else {
            agentContainerName = taskDefinition.getContainerDefinitions().get(0).getName();
            LOGGER.log(Level.FINE, "Found container definition with {0} container(s). Assuming first container is the Jenkins agent: {1}", new Object[]{taskDefinition.getContainerDefinitions().size(), agentContainerName});
        }

        Tag jenkinsLabelTag = new Tag().withKey(AWS_TAG_JENKINS_LABEL_KEY).withValue(template.getLabel());
        Tag jenkinsTemplateNameTag =
                new Tag().withKey(AWS_TAG_JENKINS_TEMPLATENAME_KEY).withValue(template.getTemplateName());
        RunTaskRequest req = new RunTaskRequest()
                .withTaskDefinition(taskDefinition.getTaskDefinitionArn())
                .withTags(jenkinsLabelTag, jenkinsTemplateNameTag)
                .withOverrides(new TaskOverride()
                        .withContainerOverrides(new ContainerOverride()
                                .withName(agentContainerName)
                                .withCommand(command)
                                .withEnvironment(envNodeName)
                                .withEnvironment(envNodeSecret)))
                .withPlacementStrategy(template.getPlacementStrategyEntries())
                .withCluster(clusterArn)
                .withPropagateTags("TASK_DEFINITION");
        if ( ! template.getDefaultCapacityProvider() && template.getCapacityProviderStrategies() == null ) {
            req.withLaunchType(LaunchType.fromValue(template.getLaunchType()));
        }
        if ( ! template.getDefaultCapacityProvider() && template.getCapacityProviderStrategies() != null ) {
            req.withCapacityProviderStrategy(template.getCapacityProviderStrategyEntries());
        }
        if (template.isFargate()) {
            req.withPlatformVersion(template.getPlatformVersion());
        }
        if (template.isFargate() || template.isEC2()) {
            req.setEnableExecuteCommand(template.isEnableExecuteCommand());
        }

        if (taskDefinition.getNetworkMode() != null && taskDefinition.getNetworkMode().equals("awsvpc")) {
            AwsVpcConfiguration awsVpcConfiguration = new AwsVpcConfiguration();
            awsVpcConfiguration.setAssignPublicIp(template.getAssignPublicIp() ? "ENABLED" : "DISABLED");
            awsVpcConfiguration.setSecurityGroups(Arrays.asList(template.getSecurityGroups().split(",")));
            awsVpcConfiguration.setSubnets(Arrays.asList(template.getSubnets().split(",")));

            NetworkConfiguration networkConfiguration = new NetworkConfiguration();
            networkConfiguration.withAwsvpcConfiguration(awsVpcConfiguration);

            req.withNetworkConfiguration(networkConfiguration);
        }
        return client.runTask(req);
    }

}

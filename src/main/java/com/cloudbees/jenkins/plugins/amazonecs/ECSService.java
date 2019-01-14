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
import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.AmazonECSClientBuilder;
import com.amazonaws.services.ecs.model.AwsVpcConfiguration;
import com.amazonaws.services.ecs.model.ClientException;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.ContainerOverride;
import com.amazonaws.services.ecs.model.DeregisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.DescribeTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.DescribeTaskDefinitionResult;
import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.DescribeTasksResult;
import com.amazonaws.services.ecs.model.KeyValuePair;
import com.amazonaws.services.ecs.model.LaunchType;
import com.amazonaws.services.ecs.model.LogConfiguration;
import com.amazonaws.services.ecs.model.NetworkConfiguration;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.RepositoryCredentials;
import com.amazonaws.services.ecs.model.RunTaskRequest;
import com.amazonaws.services.ecs.model.RunTaskResult;
import com.amazonaws.services.ecs.model.StopTaskRequest;
import com.amazonaws.services.ecs.model.Task;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.amazonaws.services.ecs.model.TaskOverride;
import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsHelper;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;

import hudson.AbortException;
import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;

/**
 * Encapsulates interactions with Amazon ECS.
 *
 * @author Jan Roehrich <jan@roehrich.info>
 *
 */
class ECSService {
    private static final Logger LOGGER = Logger.getLogger(ECSCloud.class.getName());

    private String credentialsId;

    private String regionName;

    public ECSService(String credentialsId, String regionName) {
        super();
        this.credentialsId = credentialsId;
        this.regionName = regionName;
    }

    AmazonECS getAmazonECSClient() {
        ProxyConfiguration proxy = Jenkins.get().proxy;
        ClientConfiguration clientConfiguration = new ClientConfiguration();

        if(proxy != null) {
            clientConfiguration.setProxyHost(proxy.name);
            clientConfiguration.setProxyPort(proxy.port);
            clientConfiguration.setProxyUsername(proxy.getUserName());
            clientConfiguration.setProxyPassword(proxy.getPassword());
        }

        AmazonECSClientBuilder builder = AmazonECSClientBuilder
                    .standard()
                    .withClientConfiguration(clientConfiguration)
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
        LOGGER.log(Level.FINE, "Selected Region: {0}", regionName);

        return builder.build();
    }

    Region getRegion(String regionName) {
        if (StringUtils.isNotEmpty(regionName)) {
            return RegionUtils.getRegion(regionName);
        } else {
            return Region.getRegion(Regions.US_EAST_1);
        }
    }

    @CheckForNull
    private AmazonWebServicesCredentials getCredentials(@Nullable String credentialsId) {
        return AWSCredentialsHelper.getCredentials(credentialsId, Jenkins.get());
    }

    public Task describeTask(String taskArn, String clusterArn) {
        final AmazonECS client = getAmazonECSClient();

        DescribeTasksResult result = client.describeTasks(new DescribeTasksRequest().withCluster(clusterArn).withTasks(taskArn));

        if (result.getTasks().size() == 0) {
            return null;
        } else {
            return result.getTasks().get(0);
        }
    }

    public void stopTask(String taskArn, String clusterArn) {
        final AmazonECS client = getAmazonECSClient();

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
     */
    TaskDefinition registerTemplate(final ECSCloud cloud, final ECSTaskTemplate template) {
        final AmazonECS client = getAmazonECSClient();
        
        String familyName = fullQualifiedTemplateName(cloud, template);
        final ContainerDefinition def = new ContainerDefinition()
                .withName(familyName)
                .withImage(template.getImage())
                .withEnvironment(template.getEnvironmentKeyValuePairs())
                .withExtraHosts(template.getExtraHostEntries())
                .withMountPoints(template.getMountPointEntries())
                .withPortMappings(template.getPortMappingEntries())
                .withCpu(template.getCpu())
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
        
        if (template.getRepositoryCredentials() != null)
            def.withRepositoryCredentials(new RepositoryCredentials().withCredentialsParameter(template.getRepositoryCredentials()));

        if (template.getLogDriver() != null) {
            LogConfiguration logConfig = new LogConfiguration();
            logConfig.setLogDriver(template.getLogDriver());
            logConfig.setOptions(template.getLogDriverOptionsMap());
            def.withLogConfiguration(logConfig);
        }

        TaskDefinition currentTaskDefinition = findTaskDefinition(familyName);

        boolean templateMatchesExistingContainerDefinition = false;
        boolean templateMatchesExistingVolumes = false;
        boolean templateMatchesExistingTaskRole = false;
        boolean templateMatchesExistingExecutionRole = false;
        boolean templateMatchesExistingNetworkMode = false;

        if (currentTaskDefinition != null) {
            final ContainerDefinition currentContainerDefinition = currentTaskDefinition.getContainerDefinitions().get(0);

            templateMatchesExistingContainerDefinition = def.equals(currentContainerDefinition);
            LOGGER.log(Level.INFO, "Match on container definition: {0}", new Object[] {templateMatchesExistingContainerDefinition});
            LOGGER.log(Level.FINE, "Match on container definition: {0}; template={1}; last={2}", new Object[] {templateMatchesExistingContainerDefinition, def, currentContainerDefinition});

            templateMatchesExistingVolumes = ObjectUtils.equals(template.getVolumeEntries(), currentTaskDefinition.getVolumes());
            LOGGER.log(Level.INFO, "Match on volumes: {0}", new Object[] {templateMatchesExistingVolumes});
            LOGGER.log(Level.FINE, "Match on volumes: {0}; template={1}; last={2}", new Object[] {templateMatchesExistingVolumes, template.getVolumeEntries(), currentTaskDefinition.getVolumes()});

            templateMatchesExistingTaskRole = StringUtils.equals(StringUtils.defaultString(template.getTaskrole()), StringUtils.defaultString(currentTaskDefinition.getTaskRoleArn()));
            LOGGER.log(Level.INFO, "Match on task role: {0}", new Object[] {templateMatchesExistingTaskRole});
            LOGGER.log(Level.FINE, "Match on task role: {0}; template={1}; last={2}", new Object[] {templateMatchesExistingTaskRole, template.getTaskrole(), currentTaskDefinition.getTaskRoleArn()});

            templateMatchesExistingExecutionRole = StringUtils.equals(StringUtils.defaultString(template.getExecutionRole()), StringUtils.defaultString(currentTaskDefinition.getExecutionRoleArn()));
            LOGGER.log(Level.INFO, "Match on execution role: {0}", new Object[] {templateMatchesExistingExecutionRole});
            LOGGER.log(Level.FINE, "Match on execution role: {0}; template={1}; last={2}", new Object[] {templateMatchesExistingExecutionRole, template.getExecutionRole(), currentTaskDefinition.getExecutionRoleArn()});

            //Compare to null if it is default network mode is selected
            String templateNetworkMode = "";
            if(StringUtils.equals(StringUtils.defaultString(template.getNetworkMode()),"default")){
                templateMatchesExistingNetworkMode = null == currentTaskDefinition.getNetworkMode(); 
                templateNetworkMode="null";
            }
            else{
                templateMatchesExistingNetworkMode = StringUtils.equals(StringUtils.defaultString(template.getNetworkMode()), StringUtils.defaultString(currentTaskDefinition.getNetworkMode()));
                templateNetworkMode=template.getNetworkMode();
            }

            LOGGER.log(Level.INFO, "Match on network mode: {0}", new Object[] {templateMatchesExistingNetworkMode});
            LOGGER.log(Level.FINE, "Match on network mode: {0}; template={1}; last={2}", new Object[] {templateMatchesExistingNetworkMode, templateNetworkMode, currentTaskDefinition.getNetworkMode()});
        }

        if(templateMatchesExistingContainerDefinition && templateMatchesExistingVolumes && templateMatchesExistingTaskRole && templateMatchesExistingExecutionRole && templateMatchesExistingNetworkMode) {
            LOGGER.log(Level.FINE, "Task Definition already exists: {0}", new Object[]{currentTaskDefinition.getTaskDefinitionArn()});
            return currentTaskDefinition;
        } else {
            final RegisterTaskDefinitionRequest request = new RegisterTaskDefinitionRequest()                
                    .withFamily(familyName)
                    .withVolumes(template.getVolumeEntries())
                    .withContainerDefinitions(def);

            //If network mode is default, that means Null in the request, so do not set.
            if(!StringUtils.equals(StringUtils.defaultString(template.getNetworkMode()),"default")){
                request.withNetworkMode(template.getNetworkMode());
            }

            if(!StringUtils.isEmpty(template.getExecutionRole())){
                request.withExecutionRoleArn(template.getExecutionRole());
            }

            if (!StringUtils.isEmpty(template.getTaskrole())) {
                request.withTaskRoleArn(template.getTaskrole());
            } 

            if (template.isFargate()) {
                request
                        .withRequiresCompatibilities(template.getLaunchType())
                        .withNetworkMode("awsvpc")
                        .withMemory(String.valueOf(template.getMemoryConstraint()))
                        .withCpu(String.valueOf(template.getCpu()));
            }

            final RegisterTaskDefinitionResult result = client.registerTaskDefinition(request);
            LOGGER.log(Level.FINE, "Created Task Definition {0}: {1}", new Object[]{result.getTaskDefinition(), request});
            LOGGER.log(Level.INFO, "Created Task Definition: {0}", new Object[]{result.getTaskDefinition()});
            return result.getTaskDefinition();
        }
    }

    void removeTemplate(final ECSCloud cloud, final ECSTaskTemplate template) {
        AmazonECS client = getAmazonECSClient();

        String familyName = fullQualifiedTemplateName(cloud, template);

        int revision = findTaskDefinition(familyName).getRevision();

        try {
            client.deregisterTaskDefinition(
                    new DeregisterTaskDefinitionRequest()
                            .withTaskDefinition(familyName + ":" + revision));

        } catch (ClientException e) {
            LOGGER.log(Level.FINE, "Error removing task definition: " + familyName + ":" + revision, e);
            LOGGER.log(Level.INFO, "Error removing task definition: " + familyName + ":" + revision);
        }
    }

    /**
     * Finds the task definition for the specified family or ARN, or null if none is found.
     * The parameter may be a task definition family, family with revision, or full task definition ARN.
     */
    TaskDefinition findTaskDefinition(String familyOrArn) {
        AmazonECS client = getAmazonECSClient();

        try {
            DescribeTaskDefinitionResult result = client.describeTaskDefinition(
                    new DescribeTaskDefinitionRequest()
                            .withTaskDefinition(familyOrArn));

            return result.getTaskDefinition();
        } catch (ClientException e) {
            LOGGER.log(Level.FINE, "No existing task definition found for family or ARN: " + familyOrArn, e);
            LOGGER.log(Level.INFO, "No existing task definition found for family or ARN: " + familyOrArn);

            return null;
        }
    }

    private String fullQualifiedTemplateName(final ECSCloud cloud, final ECSTaskTemplate template) {
        return cloud.getDisplayName().replaceAll("\\s+","") + '-' + template.getTemplateName();
    }

    RunTaskResult runEcsTask(final ECSSlave agent, final ECSTaskTemplate template, String clusterArn, Collection<String> command, TaskDefinition taskDefinition) throws IOException, AbortException {
        AmazonECS client = getAmazonECSClient();
        agent.setTaskDefinitonArn(taskDefinition.getTaskDefinitionArn());

        KeyValuePair envNodeName = new KeyValuePair();
        envNodeName.setName("SLAVE_NODE_NAME");
        envNodeName.setValue(agent.getComputer().getName());

        KeyValuePair envNodeSecret = new KeyValuePair();
        envNodeSecret.setName("SLAVE_NODE_SECRET");
        envNodeSecret.setValue(agent.getComputer().getJnlpMac());

        // by convention, we assume the jenkins agent container is the first container in the task definition. ECS requires
        // all task definitions to contain at least one container, and all containers to have a name, so we do not need
        // to null- or bounds-check for the presence of a container definition.
        String agentContainerName = taskDefinition.getContainerDefinitions().get(0).getName();

        LOGGER.log(Level.FINE, "Found container definition with {0} container(s). Assuming first container is the Jenkins agent: {1}", new Object[]{taskDefinition.getContainerDefinitions().size(), agentContainerName});

        RunTaskRequest req = new RunTaskRequest()
                .withTaskDefinition(taskDefinition.getTaskDefinitionArn())
                .withLaunchType(LaunchType.fromValue(template.getLaunchType()))
                .withOverrides(new TaskOverride()
                        .withContainerOverrides(new ContainerOverride()
                                .withName(agentContainerName)
                                .withCommand(command)
                                .withEnvironment(envNodeName)
                                .withEnvironment(envNodeSecret)))
                .withCluster(clusterArn);

        if (taskDefinition.getNetworkMode()!=null && taskDefinition.getNetworkMode().equals("awsvpc")) {
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

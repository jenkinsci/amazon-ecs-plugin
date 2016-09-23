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
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import org.apache.commons.lang.StringUtils;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.ClientException;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.ContainerOverride;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesRequest;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesResult;
import com.amazonaws.services.ecs.model.Failure;
import com.amazonaws.services.ecs.model.ListContainerInstancesRequest;
import com.amazonaws.services.ecs.model.ListContainerInstancesResult;
import com.amazonaws.services.ecs.model.Resource;
import com.amazonaws.services.ecs.model.RunTaskRequest;
import com.amazonaws.services.ecs.model.RunTaskResult;
import com.amazonaws.services.ecs.model.StopTaskRequest;
import com.amazonaws.services.ecs.model.TaskOverride;
import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsHelper;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;

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

	AmazonECSClient getAmazonECSClient() {
        final AmazonECSClient client;
        
        ProxyConfiguration proxy = Jenkins.getInstance().proxy;
        ClientConfiguration clientConfiguration = new ClientConfiguration();            
        if(proxy != null) {
        	clientConfiguration.setProxyHost(proxy.name);
        	clientConfiguration.setProxyPort(proxy.port);
        	clientConfiguration.setProxyUsername(proxy.getUserName());
        	clientConfiguration.setProxyPassword(proxy.getPassword());
        }
        
        AmazonWebServicesCredentials credentials = getCredentials(credentialsId);
        if (credentials == null) {
            // no credentials provided, rely on com.amazonaws.auth.DefaultAWSCredentialsProviderChain
            // to use IAM Role define at the EC2 instance level ...
            client = new AmazonECSClient(clientConfiguration);
        } else {
            if (LOGGER.isLoggable(Level.FINE)) {
                String awsAccessKeyId = credentials.getCredentials().getAWSAccessKeyId();
                String obfuscatedAccessKeyId = StringUtils.left(awsAccessKeyId, 4) + StringUtils.repeat("*", awsAccessKeyId.length() - (2 * 4)) + StringUtils.right(awsAccessKeyId, 4);
                LOGGER.log(Level.FINE, "Connect to Amazon ECS with IAM Access Key {1}", new Object[]{obfuscatedAccessKeyId});
            }
            client = new AmazonECSClient(credentials, clientConfiguration);
        }
        client.setRegion(getRegion(regionName));
        LOGGER.log(Level.FINE, "Selected Region: {0}", regionName);
        return client;
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
        return AWSCredentialsHelper.getCredentials(credentialsId, Jenkins.getActiveInstance());
    }
    
    void deleteTask(String taskArn, String clusterArn) {
        final AmazonECSClient client = getAmazonECSClient();

        LOGGER.log(Level.INFO, "Delete ECS Slave task: {0}", taskArn);
        try {
            client.stopTask(new StopTaskRequest().withTask(taskArn).withCluster(clusterArn));
        } catch (ClientException e) {
            LOGGER.log(Level.SEVERE, "Couldn't stop task arn " + taskArn + " caught exception: " + e.getMessage(), e);
        }
    }
    
	String runEcsTask(final ECSSlave slave, final ECSTaskTemplate template, String clusterArn, Collection<String> command) throws IOException, AbortException {
		AmazonECSClient client = getAmazonECSClient();
		String definitionArn = template.getTaskDefinitionArn();
		slave.setTaskDefinitonArn(definitionArn);	            
		    
		final RunTaskResult runTaskResult = client.runTask(new RunTaskRequest()
		  .withTaskDefinition(definitionArn)
		  .withOverrides(new TaskOverride()
		    .withContainerOverrides(new ContainerOverride()
		      .withName(template.getFullQualifiedTemplateName(slave.getCloud()))
		      .withCommand(command)))
		  .withCluster(clusterArn)
		);

		if (!runTaskResult.getFailures().isEmpty()) {
		    LOGGER.log(Level.WARNING, "Slave {0} - Failure to run task with definition {1} on ECS cluster {2}", new Object[]{slave.getNodeName(), definitionArn, clusterArn});
		    for (Failure failure : runTaskResult.getFailures()) {
		        LOGGER.log(Level.WARNING, "Slave {0} - Failure reason={1}, arn={2}", new Object[]{slave.getNodeName(), failure.getReason(), failure.getArn()});
		    }			    
		    throw new AbortException("Failed to run slave container " + slave.getNodeName());
		}
		return runTaskResult.getTasks().get(0).getTaskArn();            
	}
	
	void waitForSufficientClusterResources(Date timeout, ECSTaskTemplate template, String clusterArn) throws InterruptedException, AbortException {
		AmazonECSClient client = getAmazonECSClient();
		
		boolean hasEnoughResources = false;			
		WHILE:
		do {
			ListContainerInstancesResult listContainerInstances = client.listContainerInstances(new ListContainerInstancesRequest().withCluster(clusterArn));
			DescribeContainerInstancesResult containerInstancesDesc = client.describeContainerInstances(new DescribeContainerInstancesRequest().withContainerInstances(listContainerInstances.getContainerInstanceArns()).withCluster(clusterArn));
			LOGGER.log(Level.INFO, "Found {0} instances", containerInstancesDesc.getContainerInstances().size());
			for(ContainerInstance instance : containerInstancesDesc.getContainerInstances()) {
				LOGGER.log(Level.INFO, "Resources found in instance {1}: {0}", new Object[] {instance.getRemainingResources(), instance.getContainerInstanceArn()});
				Resource memoryResource = null;
				Resource cpuResource = null;
				for(Resource resource : instance.getRemainingResources()) {
					if("MEMORY".equals(resource.getName())) {
						memoryResource = resource;
					} else if("CPU".equals(resource.getName())) {
						cpuResource = resource;
					}        					
				}
				
				LOGGER.log(Level.INFO, "Instance {0} has {1}mb of free memory. {2}mb are required", new Object[]{ instance.getContainerInstanceArn(), memoryResource.getIntegerValue(), template.getMemory()});
				LOGGER.log(Level.INFO, "Instance {0} has {1} units of free cpu. {2} units are required", new Object[]{ instance.getContainerInstanceArn(), cpuResource.getIntegerValue(), template.getCpu()});
				if(memoryResource.getIntegerValue() >= template.getMemory() 
						&& cpuResource.getIntegerValue() >= template.getCpu()) {
					hasEnoughResources = true;
					break WHILE;
				}
			}
			
			// sleep 10s and check memory again
			Thread.sleep(10000);
		} while(!hasEnoughResources && timeout.after(new Date()));
		
		if(!hasEnoughResources) {
			final String msg = MessageFormat.format("Timeout while waiting for sufficient resources: {0} cpu units, {1}mb free memory", template.getCpu(), template.getMemory());
		    LOGGER.log(Level.WARNING, msg);
		    throw new AbortException(msg);
		}
	}
}
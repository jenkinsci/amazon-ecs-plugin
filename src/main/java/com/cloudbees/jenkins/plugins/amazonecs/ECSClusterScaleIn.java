package com.cloudbees.jenkins.plugins.amazonecs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;

import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.SetInstanceProtectionRequest;
import com.amazonaws.services.autoscaling.model.TerminateInstanceInAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.ContainerInstanceStatus;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesRequest;
import com.amazonaws.services.ecs.model.ListContainerInstancesRequest;
import com.amazonaws.services.ecs.model.ListContainerInstancesResult;
import com.amazonaws.services.ecs.model.UpdateContainerInstancesStateRequest;

public class ECSClusterScaleIn implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(ECSClusterScaleIn.class.getName());

    @Nonnull
    private final String ecsClusterArn;
    @Nonnull
    private final String autoScalingGroupName;
    private final AmazonAutoScalingClient autoScalingClient;
    private final AmazonECSClient ecsClient;
    private final AmazonEC2Client ec2Client;
    
    public ECSClusterScaleIn(final ECSService ecsService, final String ecsClusterArn, final String autoScalingGroupName) {
        this.ecsClusterArn = ecsClusterArn;
        this.autoScalingGroupName = autoScalingGroupName;

        // init AWS clients
        this.autoScalingClient = ecsService.getAmazonAutoScalingClient();
        this.ecsClient = ecsService.getAmazonECSClient();
        this.ec2Client = ecsService.getAmazonEC2Client();
    }

    private AutoScalingGroup getAutoScalingGroup() {
        // fetch auto scaling group
        final Collection<String> autoScalingGroupNames = new ArrayList<String>();
        autoScalingGroupNames.add(autoScalingGroupName);
        final List<AutoScalingGroup> autoScalingGroups = autoScalingClient.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(autoScalingGroupNames)).getAutoScalingGroups();
        if (autoScalingGroups.size() >= 1) {
            return autoScalingGroups.get(0);
        } else {
            return null;
        }
    }

    private void protectInstanceFromScaleIn(final String instanceId, final Boolean protectFromScaleIn) {
        autoScalingClient.setInstanceProtection(new SetInstanceProtectionRequest().withAutoScalingGroupName(autoScalingGroupName).withInstanceIds(instanceId).withProtectedFromScaleIn(protectFromScaleIn));
    }

    // private void protect(final String instanceId) {
    // protectFromScaleIn(instanceId, true);
    // }

    private void unprotect(final String instanceId) {
        protectInstanceFromScaleIn(instanceId, false);
    }

    private void terminate(final String instanceId) {
        unprotect(instanceId);
        autoScalingClient.terminateInstanceInAutoScalingGroup(new TerminateInstanceInAutoScalingGroupRequest().withInstanceId(instanceId).withShouldDecrementDesiredCapacity(true));
    }

    // private void grow(final int amount) {
    // final AutoScalingGroup autoScalingGroup = getAutoScalingGroup();
    // final int newDesiredCapacity = autoScalingGroup.getDesiredCapacity() + amount;
    // if (newDesiredCapacity <= autoScalingGroup.getMaxSize()) {
    // autoScalingClient
    // .setDesiredCapacity(new SetDesiredCapacityRequest().withAutoScalingGroupName(autoScalingGroupName)
    // .withHonorCooldown(true).withDesiredCapacity(newDesiredCapacity));
    // }
    // }

    private List<String> getInstanceArns(final ContainerInstanceStatus status) {
        final List<String> instanceArns = new ArrayList<String>();
        ListContainerInstancesRequest request = new ListContainerInstancesRequest().withCluster(ecsClusterArn).withStatus(status);
        ListContainerInstancesResult result = ecsClient.listContainerInstances(request);
        instanceArns.addAll(result.getContainerInstanceArns());
        String nextToken = result.getNextToken();
        while (nextToken != null) {
            request = new ListContainerInstancesRequest().withCluster(ecsClusterArn).withStatus(status).withNextToken(nextToken);
            result = ecsClient.listContainerInstances(request);
            instanceArns.addAll(result.getContainerInstanceArns());
            nextToken = result.getNextToken();
        }
        return instanceArns;
    }

    private List<ContainerInstance> describeInstances(final ContainerInstanceStatus status) {
        final List<String> instanceArns = getInstanceArns(status);
        if (!instanceArns.isEmpty()) {
            return ecsClient.describeContainerInstances(new DescribeContainerInstancesRequest().withCluster(ecsClusterArn).withContainerInstances(instanceArns)).getContainerInstances();
        }
        return Collections.emptyList();
    }

    // private List<String> getInstanceIds(final ContainerInstanceStatus status) {
    // final List<String> result = new ArrayList<String>();
    // for (final ContainerInstance instance : describeInstances(status)) {
    // result.add(instance.getEc2InstanceId());
    // }
    // return result;
    // }

    public void drain(final String instanceArn) {
        ecsClient.updateContainerInstancesState(new UpdateContainerInstancesStateRequest().withCluster(ecsClusterArn).withStatus(ContainerInstanceStatus.DRAINING).withContainerInstances(instanceArn));
    }

    @Override
    public void run() {
        final AutoScalingGroup autoScalingGroup = getAutoScalingGroup();
        LOGGER.log(Level.INFO, "Enabled auto scaling of ECS cluster {0} (using auto scaling group {1})", new Object[] {ecsClusterArn, autoScalingGroupName});

        // automatically protect new instances from scaling in
        if (!autoScalingGroup.isNewInstancesProtectedFromScaleIn()) {
            autoScalingClient.updateAutoScalingGroup(new UpdateAutoScalingGroupRequest().withAutoScalingGroupName(autoScalingGroup.getAutoScalingGroupName()).withNewInstancesProtectedFromScaleIn(true));
        }

        // loop until interrupted
        try {
            while (!Thread.currentThread().isInterrupted()) {
                // remove idle DRAINING jenkins slaves (=slaves put in DRAINING state in previous iteration of this loop)
                for (final ContainerInstance containerInstance : describeInstances(ContainerInstanceStatus.DRAINING)) {
                    final int taskCount = containerInstance.getPendingTasksCount() + containerInstance.getRunningTasksCount();
                    if (taskCount == 0) {
                        final String instanceId = containerInstance.getEc2InstanceId();
                        LOGGER.log(Level.INFO, "Terminating idle draining EC2 instance {0} of ECS cluster {1}", new Object[] {instanceId, ecsClusterArn});
                        terminate(instanceId);
                    }
                }

                // DRAIN idle jenkins slaves approaching the next billing hour
                // (ECS will not start new tasks on a draining slave)
                for (final ContainerInstance containerInstance : describeInstances(ContainerInstanceStatus.ACTIVE)) {
                    final String instanceId = containerInstance.getEc2InstanceId();
                    final String instanceArn = containerInstance.getContainerInstanceArn();
                    final int taskCount = containerInstance.getPendingTasksCount() + containerInstance.getRunningTasksCount();
                    final DescribeInstancesResult describeInstanceResult = ec2Client.describeInstances(new DescribeInstancesRequest().withInstanceIds(containerInstance.getEc2InstanceId()));
                    final Instance instance = describeInstanceResult.getReservations().get(0).getInstances().get(0);
                    final Date launchTime = instance.getLaunchTime();
                    final long upTimeInMilliSeconds = new Date().getTime() - launchTime.getTime();
                    final long upTimeInSeconds = upTimeInMilliSeconds / 1000;
                    final long remainingSecondsUntilNextHour = 3600 - upTimeInSeconds % 3600;
                    LOGGER.log(Level.FINE, "ECS cluster {0} instance {1} has uptime of {2} seconds - {3} seconds remaining until next billing hour", new Object[] {ecsClusterArn, instanceId, upTimeInSeconds, remainingSecondsUntilNextHour});
                    final String reasonToDrain;
                    if (upTimeInSeconds > 10 * 3600) {
                        reasonToDrain = "it's running for more than 10 hours";
                    } else if (taskCount == 0 && remainingSecondsUntilNextHour < 240) {
                        reasonToDrain = "it's idle and close to the next billing hour";
                    } else {
                        reasonToDrain = null;
                    }
                    if (StringUtils.isNotEmpty(reasonToDrain)) {
                        LOGGER.log(Level.INFO, "Draining ECS cluster {0} instance {1} because {2}", new Object[] {ecsClusterArn, instanceId, reasonToDrain});
                        drain(instanceArn);
                    }
                }

                // sleep for a minute
                Thread.sleep(60 * 1000);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

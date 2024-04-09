/*
 * The MIT License
 *
 *  Copyright (c) 2015, CloudBees, Inc.
 *  Copyright (c) Amazon.com, Inc. or its affiliates. All rights reserved.
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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import com.amazonaws.services.ecs.model.AccessDeniedException;
import com.amazonaws.services.ecs.model.ClientException;
import com.amazonaws.services.ecs.model.ClusterNotFoundException;
import com.amazonaws.services.ecs.model.InvalidParameterException;
import com.amazonaws.services.ecs.model.ServerException;
import com.amazonaws.services.ecs.model.Task;

import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.ComputerLauncher;

/**
 * This agent should only handle a single task and then be shutdown.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ECSSlave extends AbstractCloudSlave {

    private static final long serialVersionUID = -6324547877157811307L;

    private static final Logger LOGGER = Logger.getLogger(ECSSlave.class.getName());

    /**
     * ECS task lifecyle statuses.
     * See https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-lifecycle.html
     */
    private static final HashSet<String> upTaskStatuses = new HashSet<>(Arrays.asList("PROVISIONING", "PENDING", "ACTIVATING", "RUNNING"));
    private static final HashSet<String> downTaskStatuses = new HashSet<>(Arrays.asList("DEACTIVATING", "STOPPING", "DEPROVISIONING", "STOPPED", "DELETED"));

    @Nonnull
    private final ECSCloud cloud;
    @Nonnull
    private final ECSTaskTemplate template;

    /**
     * AWS Resource Name (ARN) of the ECS Cluster.
     */
    private String clusterArn;
    /**
     * AWS Resource Name (ARN) of the ECS Task Definition.
     */
    private String taskDefinitonArn;
    /**
     * AWS Resource Name (ARN) of the ECS Task.
     */
    @CheckForNull
    private String taskArn;

    private boolean survivable = true;
    private String jobName;

    public ECSSlave(@Nonnull ECSCloud cloud, @Nonnull String name, ECSTaskTemplate template, @Nonnull ComputerLauncher launcher) throws Descriptor.FormException, IOException {
        super(
            name,
            "ECS Agent",
            template.makeRemoteFSRoot(name),
            Math.max(1, cloud.getNumExecutors()),
            Mode.EXCLUSIVE,
            template.getLabel(),
            launcher,
            cloud.getRetainAgents() ?
                new CloudRetentionStrategy(cloud.getRetentionTimeout()) :
                new OnceRetentionStrategy(cloud.getRetentionTimeout()),
            Collections.emptyList()
        );
        this.cloud = cloud;
        this.template = template;
    }

    public String getClusterArn() {
        return clusterArn;
    }

    public String getTaskDefinitonArn() {
        return taskDefinitonArn;
    }

    public String getTaskArn() {
        return taskArn;
    }

    public ECSTaskTemplate getTemplate() {
        return template;
    }

    void setClusterArn(String clusterArn) {
        this.clusterArn = clusterArn;
    }

    void setTaskArn(String taskArn) {
        this.taskArn = taskArn;
    }

    public void setTaskDefinitonArn(String taskDefinitonArn) {
        this.taskDefinitonArn = taskDefinitonArn;
    }

    public boolean isSurvivable() {
        if (!survivable) {
            /* Task in this state will not come back again. */
            return false;
        }

        Task task = cloud.getEcsService().describeTask(taskArn, clusterArn);
        if (task == null) {
            survivable = false;
            return false;
        }

        String lastStatus = task.getLastStatus();
        String desiredStatus = task.getDesiredStatus();

        LOGGER.log(Level.FINE, "Task {0} last status={1}, desired status={2}", new Object[]{taskArn, lastStatus, desiredStatus});

        if (upTaskStatuses.contains(lastStatus) && !downTaskStatuses.contains(desiredStatus)) {
            return true;
        }

        survivable = false;
        return false;
    }

    @Override
    public AbstractCloudComputer<ECSSlave> createComputer() {
        return new ECSComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        if (taskArn == null) {
            throw new IllegalArgumentException("taskArn is null");
        }
        if (clusterArn == null) {
            throw new IllegalArgumentException("clusterArn is null");
        }

        try {
            LOGGER.log(Level.INFO, "[{0}]: Stopping: TaskArn {1}, ClusterArn {2}", new Object[]{this.getNodeName(), taskArn, clusterArn});

            cloud.getEcsService().stopTask(taskArn, clusterArn);

        } catch (ServerException ex) {
            LOGGER.log(Level.WARNING, MessageFormat.format("[{0}]: Failed to stop Task {1}", this.getNodeName(), taskArn), ex);
        } catch (AccessDeniedException ex) {
            LOGGER.log(Level.WARNING, MessageFormat.format("[{0}]: No permission to stop task {1}", this.getNodeName(), taskArn), ex);
        } catch (ClientException ex) {
            LOGGER.log(Level.WARNING, MessageFormat.format("[{0}]: Failed to stop Task {1} due {2}", this.getNodeName(), taskArn, ex.getErrorMessage()), ex);
        } catch (InvalidParameterException ex) {
            LOGGER.log(Level.WARNING, MessageFormat.format("[{0}]: Failed to stop Task {1}", this.getNodeName(), taskArn), ex);
        } catch (ClusterNotFoundException ex) {
            LOGGER.log(Level.WARNING, MessageFormat.format("[{0}]: Cluster {1} not found", this.getNodeName(), clusterArn));
        }
    }

    private void writeObject(ObjectOutputStream stream) throws IOException {
        stream.defaultWriteObject();
    }

    private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getJobName() {
        return jobName;
    }
}

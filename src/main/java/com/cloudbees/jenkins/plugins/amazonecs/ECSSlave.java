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
import java.util.Collections;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.node_monitors.ResponseTimeMonitor;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This slave should only handle a single task and then be shutdown.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ECSSlave extends AbstractCloudSlave {

    private static final Logger LOGGER = Logger.getLogger(ECSCloud.class.getName());

    @Nonnull
    private final ECSCloud cloud;

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

    private static RetentionStrategy deleteAfterFinished = new RetentionStrategy<ECSComputer>() {
        @Override
        public boolean isManualLaunchAllowed(ECSComputer c) {
            return false;
        }

        @Override
        @GuardedBy("hudson.model.Queue.lock")
        public long check(ECSComputer c) {
            LOGGER.log(Level.FINE, "Checking computer: {0}", c);

            AbstractCloudSlave node = c.getNode();

            // If the computer is NOT idle, then it is currently running some task.
            // In this case, we are going to tell Jenkins that it can no longer accept
            // any new tasks, which will cause it to create a new node for any subsequent
            // tasks.
            if(!c.isIdle() ) {
                LOGGER.log(Level.FINE, "Computer is not idle; setting it to no longer accept tasks.");
                c.setAcceptingTasks( false );
            }

            // If the computer IS idle AND it is no longer accepting tasks, then it has
            // already had a task and completed it.  In this case, we are going to terminate
            // the node.
            if(c.isIdle() && !c.isAcceptingTasks() && node != null) {
                LOGGER.log(Level.FINE, "Computer is idle and not accepting tasks; terminating it.");
                try {
                    node.terminate();
                } catch (InterruptedException e) {
                    LOGGER.log(Level.WARNING, "Failed to terminate " + c.getName(), e);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to terminate " + c.getName(), e);
                }
            }

            // If the Response Time Monitor has marked this computer as not responding, then
            // we are going to terminate the node to free up resources.
            if (c.getOfflineCause() instanceof ResponseTimeMonitor.Data && node != null) {
                LOGGER.log(Level.FINE, "Computer is not responding; terminating it");
                try {
                    node.terminate();
                } catch (InterruptedException e) {
                    LOGGER.log(Level.WARNING, "Failed to terminate " + c.getName(), e);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to terminate " + c.getName(), e);
                }
            }

            // Tell Jenkins to check again in 1 minute.
            return 1;
        }

    };

    public ECSSlave(@Nonnull ECSCloud cloud, @Nonnull String name, @Nullable String remoteFS, @Nullable String labelString, @Nonnull ComputerLauncher launcher) throws Descriptor.FormException, IOException {
        super(name, "ECS slave", remoteFS, 1, Mode.EXCLUSIVE, labelString, launcher, deleteAfterFinished, Collections.EMPTY_LIST);
        this.cloud = cloud;
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

    void setClusterArn(String clusterArn) {
        this.clusterArn = clusterArn;
    }

    void setTaskArn(String taskArn) {
        this.taskArn = taskArn;
    }

    public void setTaskDefinitonArn(String taskDefinitonArn) {
        this.taskDefinitonArn = taskDefinitonArn;
    }

    @Override
    public AbstractCloudComputer createComputer() {
        return new ECSComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        if (taskArn != null) {
            cloud.deleteTask(taskArn, clusterArn);
        }
    }

    public ECSCloud getCloud() {
        return cloud;
    }
}

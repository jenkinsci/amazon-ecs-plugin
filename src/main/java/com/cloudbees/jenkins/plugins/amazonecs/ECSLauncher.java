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

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;

import java.io.IOException;
import java.io.PrintStream;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ecs.model.Failure;
import com.amazonaws.services.ecs.model.RunTaskResult;
import com.amazonaws.services.ecs.model.Task;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.amazonaws.waiters.WaiterTimedOutException;
import com.amazonaws.waiters.WaiterUnrecoverableException;
import com.google.common.base.Throwables;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.AbortException;
import hudson.model.TaskListener;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import jenkins.slaves.RemotingWorkDirSettings;

/**
 * Launches on ECS the specified {@link ECSComputer} instance.
 */
public class ECSLauncher extends JNLPLauncher {

    private static final Logger LOGGER = Logger.getLogger(ECSLauncher.class.getName());

    private final ECSCloud cloud;
    private final ECSService ecsService;
    private boolean launched;
    private final int maxAttempts = 2;

    private static final List<String> FARGATE_RETRYABLE_MESSAGES = ImmutableList.of(
            "Timeout waiting for network interface provisioning to complete"
    );

    @DataBoundConstructor
    public ECSLauncher(ECSCloud cloud, String tunnel, String vmargs) {
        super(tunnel, vmargs, RemotingWorkDirSettings.getDisabledDefaults());
        this.cloud = cloud;
        this.ecsService = cloud.getEcsService();
    }

    @Override
    public boolean isLaunchSupported() {
        return !launched;
    }

    @Override
    public synchronized void launch(SlaveComputer computer, TaskListener listener) {

        PrintStream logger = listener.getLogger();
        logger.println("ECS: Launching agent");
        LOGGER.log(FINE, "ECS: Launching agent");

        if (!(computer instanceof ECSComputer)) {
            throw new IllegalArgumentException("This Launcher can be used only with ECSComputer");
        }

        ECSComputer ecsComputer = (ECSComputer) computer;
        computer.setAcceptingTasks(false);

        ECSSlave agent = ecsComputer.getNode();
        if (agent == null) {
            throw new IllegalStateException("Node has been removed, cannot launch " + computer.getName());
        }

        if (launched) {
            LOGGER.log(INFO, "[{0}]: Agent has already been launched, activating", agent.getNodeName());
            computer.setAcceptingTasks(true);
            return;
        }

        try {
            long timeout = System.currentTimeMillis() + Duration.ofSeconds(cloud.getSlaveTimeoutInSeconds()).toMillis();

            launchECSTaskWithRetry(ecsComputer, listener, timeout, maxAttempts);

            // now wait for agent to be online
            waitForAgent(agent, listener, timeout);

            computer.setAcceptingTasks(true);

        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING, MessageFormat.format("[{0}]: Error in provisioning; agent={1}", agent.getNodeName(), agent), ex);
            LOGGER.log(Level.FINER, "[{0}]: Removing Jenkins node", agent.getNodeName());
            try {
                agent.terminate();
            } catch (InterruptedException | IOException e) {
                LOGGER.log(Level.WARNING, "Unable to remove Jenkins node", e);
            }
            throw Throwables.propagate(ex);
        }

        ECSComputerListener.getInstance();
        launched = true;

        try {
            // We need to persist the "launched" setting...
            agent.save();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not save() agent: " + e.getMessage(), e);
        }
    }

    protected Task launchECSTaskWithRetry(ECSComputer ecsComputer, TaskListener listener, long timeout, int maxAttempts) throws IOException, InterruptedException {
        int attempt = 1;
        do {
            try {
                return launchECSTask(ecsComputer, listener, timeout);
            } catch (RetryableLaunchFailure e) {
                LOGGER.log(Level.WARNING, "Attempt {0}: Failed to start task due to {1}", new Object[]{attempt, e});
            }
            ++attempt;
        } while (attempt <= maxAttempts);

        throw new IllegalStateException(MessageFormat.format("Failed to start task after {0} attempts", maxAttempts));
    }

    protected Task launchECSTask(ECSComputer ecsComputer, TaskListener listener, long timeout) throws IOException, InterruptedException, RetryableLaunchFailure {
        PrintStream logger = listener.getLogger();

        ECSSlave agent = ecsComputer.getNode();
        if (agent == null) {
            throw new IllegalStateException("Node has been removed, cannot launch " + ecsComputer.getName());
        }

        LOGGER.log(Level.FINE, "[{0}]: Creating Task in cluster {1}", new Object[]{agent.getNodeName(), agent.getClusterArn()});

        TaskDefinition taskDefinition = ecsService.registerTemplate(cloud.getDisplayName(), agent.getTemplate());
        Task startedTask = runECSTask(taskDefinition, cloud, agent.getTemplate(), ecsService, agent);

        LOGGER.log(INFO, "[{0}]: TaskArn: {1}", new Object[]{agent.getNodeName(), startedTask.getTaskArn()});
        LOGGER.log(INFO, "[{0}]: TaskDefinitionArn: {1}", new Object[]{agent.getNodeName(), startedTask.getTaskDefinitionArn()});
        LOGGER.log(INFO, "[{0}]: ClusterArn: {1}", new Object[]{agent.getNodeName(), startedTask.getClusterArn()});
        LOGGER.log(INFO, "[{0}]: ContainerInstanceArn: {1}", new Object[]{agent.getNodeName(), startedTask.getContainerInstanceArn()});

        logger.printf("Waiting for agent to start: %1$s%n", agent.getNodeName());
        try {
            ecsService.waitForTasksRunning(startedTask.getTaskArn(), startedTask.getClusterArn(), timeout, cloud.getTaskPollingIntervalInSeconds());
        }
        catch (WaiterTimedOutException exception){
            Task task = null;
            task = ecsService.describeTask(startedTask.getTaskArn(), startedTask.getClusterArn());
            if (task != null) {
                LOGGER.log(SEVERE, "[{0}]: Task is not running or took too long to start. Last status: {1}, Exit code: {2}, Reason {3}", new Object[]{agent.getNodeName(), task.getLastStatus(), task.getContainers().get(0).getExitCode(), task.getContainers().get(0).getReason()});
            }
            throw new IllegalStateException("Task took too long to start");
        }
        catch (WaiterUnrecoverableException exception){
            LOGGER.log(Level.WARNING, MessageFormat.format("[{0}]: ECS Task stopped: {1}", agent.getNodeName(), startedTask.getTaskArn()), exception);

            if (FARGATE_RETRYABLE_MESSAGES.stream().anyMatch(exception.getMessage()::contains)) {
                throw new RetryableLaunchFailure(exception);
            }

            throw new IllegalStateException("Task stopped before coming online. TaskARN: " + startedTask.getTaskArn());
        }
        catch (AmazonServiceException exception){
            LOGGER.log(Level.SEVERE, MessageFormat.format("[{0}]: Unknown error trying to start ECS task {1}", agent.getNodeName()), exception);
            throw new IllegalStateException("Unknown error starting task " + startedTask.getTaskArn());
        }

        LOGGER.log(INFO, "[{0}]: Task started, waiting for agent to become online", new Object[]{agent.getNodeName()});

        return startedTask;
    }

    protected void waitForAgent(ECSSlave agent, TaskListener listener, long timeout) throws InterruptedException {
        PrintStream logger = listener.getLogger();

        while (System.currentTimeMillis() < timeout) {
            SlaveComputer agentComputer = agent.getComputer();

            if (agentComputer == null) {
                throw new IllegalStateException("Node was deleted, computer is null");
            }
            if (agentComputer.isOnline()) {
                break;
            }
            LOGGER.log(INFO, "[{0}]: Waiting for agent to connect", new Object[]{agent.getNodeName()});
            logger.printf("Waiting for agent to connect: %1$s%n", agent.getNodeName());
            Thread.sleep(1000);
        }
        SlaveComputer agentComputer = agent.getComputer();
        if (agentComputer == null) {
            throw new IllegalStateException("Node was deleted, computer is null");
        }

        if (!agentComputer.isOnline()) {
            throw new IllegalStateException("Agent is not connected");
        }
        LOGGER.log(INFO, "[{0}]: Agent connected", new Object[]{agent.getNodeName()});
    }

    private Task runECSTask(TaskDefinition taskDefinition, ECSCloud cloud, ECSTaskTemplate template, ECSService ecsService, ECSSlave agent) throws IOException {

        LOGGER.log(Level.INFO, "[{0}]: Starting agent with task definition {1}}", new Object[]{agent.getNodeName(), taskDefinition.getTaskDefinitionArn()});

        RunTaskResult runTaskResult = ecsService.runEcsTask(agent, template, cloud.getCluster(), getDockerRunCommand(agent, cloud.getJenkinsUrl()), taskDefinition);

        if (!runTaskResult.getFailures().isEmpty()) {
            LOGGER.log(Level.WARNING, "[{0}]: Failure to run task with definition {1} on ECS cluster {2}", new Object[]{agent.getNodeName(), taskDefinition.getTaskDefinitionArn(), cloud.getCluster()});
            for (Failure failure : runTaskResult.getFailures()) {
                LOGGER.log(Level.WARNING, "[{0}]: Failure reason={1}, arn={2}", new Object[]{agent.getNodeName(), failure.getReason(), failure.getArn()});
            }
            throw new AbortException("Failed to run agent container " + agent.getNodeName());
        }
        Task task = runTaskResult.getTasks().get(0);
        String taskArn = task.getTaskArn();

        LOGGER.log(Level.INFO, "[{0}]: Agent started with task arn : {1}", new Object[] { agent.getNodeName(), taskArn });
        agent.setTaskArn(taskArn);
        agent.setClusterArn(cloud.getCluster());

        return task;
    }

    private Collection<String> getDockerRunCommand(ECSSlave slave, String jenkinsUrl) {
        Collection<String> command = new ArrayList<>();
        command.add("-url");
        command.add(jenkinsUrl);
        if (StringUtils.isNotBlank(tunnel)) {
            command.add("-tunnel");
            command.add(tunnel);
        }
        SlaveComputer agent = slave.getComputer();
        if (agent == null) {
            throw new IllegalStateException("Node was deleted, computer is null");
        }
        command.add("-secret");
        command.add(agent.getJnlpMac());
        command.add("-name");
        command.add(agent.getName());
        return command;
    }

    protected static final class RetryableLaunchFailure extends Exception {
        public RetryableLaunchFailure(Exception e) {
            super(e);
        }
    }
}

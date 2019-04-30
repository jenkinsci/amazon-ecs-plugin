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

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;

import java.io.IOException;
import java.io.PrintStream;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.amazonaws.services.ecs.model.Failure;
import com.amazonaws.services.ecs.model.RunTaskResult;
import com.amazonaws.services.ecs.model.Task;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.google.common.base.Throwables;

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
    public void launch(SlaveComputer computer, TaskListener listener) {

        PrintStream logger = listener.getLogger();
        logger.println("ECS: Launching agent");
        LOGGER.log(FINE, "ECS: Launching agent");

        if (!(computer instanceof ECSComputer)) {
            throw new IllegalArgumentException("This Launcher can be used only with ECSComputer");
        }

        ECSComputer ecsComputer = (ECSComputer) computer;
        computer.setAcceptingTasks(false);

        ECSSlave agent = (ECSSlave)ecsComputer.getNode();
        if (agent == null) {
            throw new IllegalStateException("Node has been removed, cannot launch " + computer.getName());
        }

        if (launched) {
            LOGGER.log(INFO, "[{0}]: Agent has already been launched, activating", agent.getNodeName());
            computer.setAcceptingTasks(true);
            return;
        }

        try {
            LOGGER.log(Level.FINE, "[{0}]: Creating Task in cluster {1}", new Object[]{agent.getNodeName(), agent.getClusterArn()});

            TaskDefinition taskDefinition = getTaskDefinition(agent.getNodeName(), agent.getTemplate(), cloud, ecsService);

            Task task = runECSTask(taskDefinition, cloud, agent.getTemplate(), ecsService, agent);

            LOGGER.log(INFO, "[{0}]: TaskArn: {1}", new Object[]{agent.getNodeName(), task.getTaskArn()});
            LOGGER.log(INFO, "[{0}]: TaskDefinitionArn: {1}", new Object[]{agent.getNodeName(), task.getTaskDefinitionArn()});
            LOGGER.log(INFO, "[{0}]: ClusterArn: {1}", new Object[]{agent.getNodeName(), task.getClusterArn()});
            LOGGER.log(INFO, "[{0}]: ContainerInstanceArn: {1}", new Object[]{agent.getNodeName(), task.getContainerInstanceArn()});

            long timeout = System.currentTimeMillis() + Duration.ofSeconds(cloud.getSlaveTimeoutInSeconds()).toMillis();

            boolean taskRunning = false;
            while (System.currentTimeMillis() < timeout) {

                // Wait while PENDING
                task = ecsService.describeTask(task.getTaskArn(), task.getClusterArn());

                if (task != null) {
                    String taskStatus = task.getLastStatus();
                    LOGGER.log(Level.FINE, "[{0}]: Status of ECS Task is {1}", new Object[]{agent.getNodeName(), taskStatus});

                    if (taskStatus.equals("RUNNING")) {
                        taskRunning = true;
                        break;
                    }
                    if (taskStatus.equals("STOPPED")) {
                        LOGGER.log(Level.WARNING, "[{0}]: ECS Task stopped: {1}", new Object[]{agent.getNodeName(), task.getStoppedReason()});
                        logger.printf("ECS Task stopped: %1$s%n", task.getStoppedReason());
                        throw new IllegalStateException("Task stopped before coming online. TaskARN: " + task.getTaskArn());
                    }
                }

                LOGGER.log(INFO, "[{0}]: Waiting for agent to start", new Object[]{agent.getNodeName()});
                logger.printf("Waiting for agent to start: %1$s%n", agent.getNodeName());
                Thread.sleep(1000);
            }

            if (!taskRunning) {
                if (task != null) {
                    LOGGER.log(SEVERE, "[{0}]: Task is not running. Last status: {1}, Exit code: {2}, Reason {3}", new Object[]{agent.getNodeName(), task.getLastStatus(), task.getContainers().get(0).getExitCode(), task.getContainers().get(0).getReason()});
                }
                throw new IllegalStateException("Task took too long to start");
            }

            LOGGER.log(INFO, "[{0}]: Task started, waiting for agent to become online", new Object[]{agent.getNodeName()});

            // now wait for agent to be online
            while (System.currentTimeMillis() < timeout) {

                if (agent.getComputer() == null) {
                    throw new IllegalStateException("Node was deleted, computer is null");
                }
                if (agent.getComputer().isOnline()) {
                    break;
                }
                LOGGER.log(INFO, "[{0}]: Waiting for agent to connect", new Object[]{agent.getNodeName()});
                logger.printf("Waiting for agent to connect: %1$s%n", agent.getNodeName());
                Thread.sleep(1000);
            }

            if (!agent.getComputer().isOnline()) {
                throw new IllegalStateException("Agent is not connected");
            }
            LOGGER.log(INFO, "[{0}]: Agent connected", new Object[]{agent.getNodeName()});

            computer.setAcceptingTasks(true);

        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING, MessageFormat.format("[{0}]: Error in provisioning; agent={1}", agent.getNodeName(), agent), ex);
            LOGGER.log(Level.FINER, "[{0}]: Removing Jenkins node", agent.getNodeName());
            try {
                agent.terminate();
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING, "Unable to remove Jenkins node", e);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Unable to remove Jenkins node", e);
            }
            throw Throwables.propagate(ex);
        }

        launched = true;

        try {
            // We need to persist the "launched" setting...
            agent.save();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not save() agent: " + e.getMessage(), e);
        }
    }

    private TaskDefinition getTaskDefinition(String nodeName, ECSTaskTemplate template, ECSCloud cloud, ECSService ecsService) {
        TaskDefinition taskDefinition;

        if (template.getTaskDefinitionOverride() == null) {
            taskDefinition = ecsService.registerTemplate(cloud, template);

        } else {
            LOGGER.log(Level.FINE, "[{0}]: Attempting to find task definition family or ARN: {1}", new Object[] {nodeName, template.getTaskDefinitionOverride()});

            taskDefinition = ecsService.findTaskDefinition(template.getTaskDefinitionOverride());
            if (taskDefinition == null) {
                throw new RuntimeException("Could not find task definition family or ARN: " + template.getTaskDefinitionOverride());
            }

            LOGGER.log(Level.FINE, "[{0}]: Found task definition: {1}", new Object[] {nodeName, taskDefinition.getTaskDefinitionArn()});
        }

        return taskDefinition;
    }

    private Task runECSTask(TaskDefinition taskDefinition, ECSCloud cloud, ECSTaskTemplate template, ECSService ecsService, ECSSlave agent) throws IOException, AbortException {

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
        Collection<String> command = new ArrayList<String>();
        command.add("-url");
        command.add(jenkinsUrl);
        if (StringUtils.isNotBlank(tunnel)) {
            command.add("-tunnel");
            command.add(tunnel);
        }
        command.add(slave.getComputer().getJnlpMac());
        command.add(slave.getComputer().getName());
        return command;
    }
}

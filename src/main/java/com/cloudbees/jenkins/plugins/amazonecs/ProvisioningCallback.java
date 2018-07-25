package com.cloudbees.jenkins.plugins.amazonecs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.amazonaws.services.ecs.model.TaskDefinition;
import org.apache.commons.lang.StringUtils;

import hudson.model.Node;
import hudson.slaves.JNLPLauncher;
import jenkins.model.Jenkins;


public class ProvisioningCallback implements Callable<Node> {

    private static final Logger LOGGER = Logger.getLogger(ECSCloud.class.getName());

    private final ECSCloud cloud;
    private final ECSService ecs;
    private final ECSTaskTemplate template;

    public ProvisioningCallback(ECSCloud cloud, ECSService ecs, ECSTaskTemplate template) {
        this.cloud = cloud;
        this.template = template;
        this.ecs = ecs;
    }

    public Node call() throws Exception {
        final ECSSlave slave;

        Date now = new Date();
        Date timeout = new Date(now.getTime() + 1000 * cloud.getSlaveTimoutInSeconds());

        synchronized (cloud.getCluster()) {
            if (!template.isFargate()){
                ecs.waitForSufficientClusterResources(timeout, template, cloud.getCluster());
            }

            String uniq = Long.toHexString(System.nanoTime());
            slave = new ECSSlave(cloud, cloud.name + "-" + uniq, template.getRemoteFSRoot(),
                    template.getLabel(), new JNLPLauncher(false));

            slave.setClusterArn(cloud.getCluster());
            Jenkins.get().addNode(slave);

            while (Jenkins.get().getNode(slave.getNodeName()) == null) {
                Thread.sleep(1000);
            }
            LOGGER.log(Level.INFO, "Created Slave: {0}", slave.getNodeName());

            try {
                TaskDefinition taskDefinition;

                if (template.getTaskDefinitionOverride() == null) {
                    taskDefinition = ecs.registerTemplate(slave.getCloud(), template);
                } else {
                    LOGGER.log(Level.FINE, "Attempting to find task definition family or ARN: {0}", template.getTaskDefinitionOverride());

                    taskDefinition = ecs.findTaskDefinition(template.getTaskDefinitionOverride());
                    if (taskDefinition == null) {
                        throw new RuntimeException("Could not find task definition family or ARN: " + template.getTaskDefinitionOverride());
                    }

                    LOGGER.log(Level.FINE, "Found task definition: {0}", taskDefinition.getTaskDefinitionArn());
                }

                LOGGER.log(Level.INFO, "Running task definition {0} on slave {1}", new Object[]{taskDefinition.getTaskDefinitionArn(), slave.getNodeName()});

                String taskArn = ecs.runEcsTask(slave, template, cloud.getCluster(), getDockerRunCommand(slave), taskDefinition);
                LOGGER.log(Level.INFO, "Slave {0} - Slave Task Started : {1}", new Object[] { slave.getNodeName(), taskArn });

                slave.setTaskArn(taskArn);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Slave {0} - Cannot create ECS Task");
                Jenkins.get().removeNode(slave);
                throw ex;
            }
        }

        return slave;
    }

    private Collection<String> getDockerRunCommand(ECSSlave slave) {
        Collection<String> command = new ArrayList<String>();
        command.add("-url");
        command.add(cloud.getJenkinsUrl());
        if (StringUtils.isNotBlank(cloud.getTunnel())) {
            command.add("-tunnel");
            command.add(cloud.getTunnel());
        }
        command.add(slave.getComputer().getJnlpMac());
        command.add(slave.getComputer().getName());
        return command;
    }
}
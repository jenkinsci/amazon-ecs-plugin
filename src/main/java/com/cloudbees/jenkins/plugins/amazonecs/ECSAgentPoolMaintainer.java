package com.cloudbees.jenkins.plugins.amazonecs;

import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;
import org.apache.commons.lang.RandomStringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

@Extension
public class ECSAgentPoolMaintainer extends AsyncPeriodicWork {

    private static final Logger LOGGER = Logger.getLogger(ECSAgentPoolMaintainer.class.getName());

    public ECSAgentPoolMaintainer() {
        super("ECS Agent Pool Maintainer");
    }

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit.MINUTES.toMillis(1);
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        for (Cloud c : Jenkins.get().clouds) {
            if (c instanceof ECSCloud) {
                ECSCloud cloud = (ECSCloud) c;
                for (ECSAgentPool pool : cloud.getAgentPools()) {

                    LOGGER.log(Level.INFO, "Pool: {0}, Required: {1}, Labels: [{2}]",  new Object[]{pool.getId(), pool.getMinIdleAgents(), pool.getLabel()});

                    if (pool.getMinIdleAgents() <= 0 || !pool.isScheduleActive()) {
                        continue;
                    }

                    final ECSTaskTemplate template = cloud.getTemplate(pool.getLabel());

                    if (template != null) {

                        int current = countIdle(pool);
                        LOGGER.log(Level.INFO, "Pool: {0}, Required: {1}, Current: {2}, Labels: [{3}] ",  new Object[]{pool.getId(), pool.getMinIdleAgents(), current, pool.getLabel()});

                        while (current < pool.getMinIdleAgents()) {
                            try {
                                String agentName = cloud.getDisplayName() + "-" + pool.getLabel() + "-" + RandomStringUtils.random(5, "bcdfghjklmnpqrstvwxz0123456789");
                                ECSPoolSlave ecsPoolSlave = new ECSPoolSlave(cloud, pool, agentName, template, new ECSLauncher(cloud, cloud.getTunnel(), null));
                                Jenkins.get().addNode(ecsPoolSlave);
                                Computer computer = ecsPoolSlave.toComputer();
                                if (computer != null) {
                                    computer.connect(false);
                                }
                                LOGGER.log(Level.INFO, "Launch new agent.. Pool: {0}, Name: {1}",  new Object[]{pool.getId(), agentName});

                                current++;
                            } catch (Exception ex) {
                                LOGGER.log(Level.WARNING, "Failed to pre-launch agent for template " + template.getTemplateName(), ex);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private int countIdle(@Nonnull ECSAgentPool ecsAgentPool) {
        Set<String> poolLabels = new HashSet<>(Arrays.asList(ecsAgentPool.getLabel().split("\\s+")));
        int count = 0;

        for (Computer computer : Jenkins.get().getComputers()) {
            if (!(computer instanceof ECSComputer)) {
                continue;
            }

            if (computer.getNode() == null) {
                continue;
            }

            if (!(computer.getNode() instanceof ECSPoolSlave node)) {
                continue;
            }

            if (!ecsAgentPool.getId().equals(node.getId())) {
                continue;
            }

            Set<String> nodeLabels = new HashSet<>(Arrays.asList(node.getLabelString().split("\\s+")));
            if (nodeLabels.containsAll(poolLabels) && computer.isIdle()) {
                count++;
            }
        }

        return count;
    }
}
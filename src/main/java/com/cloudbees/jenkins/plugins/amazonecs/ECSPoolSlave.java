package com.cloudbees.jenkins.plugins.amazonecs;

import hudson.model.Descriptor;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.ComputerLauncher;
import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;

import javax.annotation.Nonnull;
import java.io.IOException;

public class ECSPoolSlave extends ECSSlave {

    private final String id;

    public ECSPoolSlave(@Nonnull ECSCloud cloud, @Nonnull ECSAgentPool ecsAgentPool, @Nonnull String name, ECSTaskTemplate template,
                        @Nonnull ComputerLauncher launcher) throws Descriptor.FormException, IOException {
        super(cloud, name, template, launcher, cloud.getRetainAgents() ?
                new CloudRetentionStrategy(cloud.getRetentionTimeout()) :
                new OnceRetentionStrategy(ecsAgentPool.getMaxIdleMinutes()));
        this.setNumExecutors(cloud.getNumExecutors());
        this.id = ecsAgentPool.getId();

    }

    public String getId() {
        return id;
    }
}

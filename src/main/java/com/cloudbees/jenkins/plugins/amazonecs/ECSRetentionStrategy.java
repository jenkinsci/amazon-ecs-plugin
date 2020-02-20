package com.cloudbees.jenkins.plugins.amazonecs;

import hudson.model.*;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.CloudRetentionStrategy;
import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ECSRetentionStrategy extends CloudRetentionStrategy implements ExecutorListener {
    private static final Logger LOGGER = Logger.getLogger(ECSRetentionStrategy.class.getName());

    // OnceRetentionStrategy is final, so let's delegate instead of extending
    private OnceRetentionStrategy onceRetentionStrategy;

    // This field is private in OnceRetentionStrategy, but we need it to lateinit this.onceRetentionStrategy
    private int idleMinutes;

    public ECSRetentionStrategy(int idleMinutes) {
        super(idleMinutes);
        this.idleMinutes = idleMinutes;
    }

    private OnceRetentionStrategy getOnceRetentionStrategy() {
        if (this.onceRetentionStrategy == null) {
            // onceRetentionStrategy isn't persisted to disk across master reboots, so let's get a fresh one if needed
            this.onceRetentionStrategy = new OnceRetentionStrategy(this.idleMinutes);
        }
        return this.onceRetentionStrategy;
    }

    @Override
    public long check(AbstractCloudComputer c) {
        if (proceedWithTermination((ECSSlave) c.getNode())) {
            return getOnceRetentionStrategy().check(c);
        }
        // Check again in one minute
        return 1;
    }

    @Override
    public void taskAccepted(Executor executor, Queue.Task task) { }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        if (proceedWithTermination((ECSSlave) executor.getOwner().getNode())) {
            getOnceRetentionStrategy().taskCompleted(executor, task, durationMS);
        }
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        if (proceedWithTermination((ECSSlave) executor.getOwner().getNode())) {
            getOnceRetentionStrategy().taskCompletedWithProblems(executor, task, durationMS, problems);
        }
    }

    private boolean proceedWithTermination(ECSSlave node) {
        if (node == null) {
            return false;
        }
        ECSTaskTemplate template = node.getTemplate();
        int minNumNodes = template.getMinRetainedNodes();
        for (Label label : template.getLabelSet()) {
            Set<Node> nodes = label.getNodes();
            int numNodes = nodes.size();
            if (numNodes <= minNumNodes) {
                LOGGER.log(
                    Level.FINER,
                    "Only {0} node(s) in label \"{1}\", retaining {2}",
                    new Object[]{
                        numNodes,
                        template.getLabel(),
                        node.getNodeName()
                    }
                );
                return false;
            }
        }
        LOGGER.log(
            Level.FINER,
            "More than {0} node(s) in label \"{1}\", no need to retain {2}",
            new Object[]{
                minNumNodes,
                template.getLabel(),
                node.getNodeName()
            }
        );
        return true;
    }
}

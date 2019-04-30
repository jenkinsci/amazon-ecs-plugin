package com.cloudbees.jenkins.plugins.amazonecs;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.CheckForNull;

import hudson.Extension;
import hudson.model.Label;
import hudson.model.Node;

@Extension
public class DefaultInProvisioning extends InProvisioning {

    private static boolean isNotAcceptingTasks(Node n) {
        return n.toComputer().isLaunchSupported() // Launcher hasn't been called yet
                || !n.isAcceptingTasks() // node is not ready yet
                ;
    }

    @Override
    public Set<String> getInProvisioning(@CheckForNull Label label) {
        if (label != null) {
            return label.getNodes().stream()
                    .filter(ECSSlave.class::isInstance)
                    .filter(DefaultInProvisioning::isNotAcceptingTasks)
                    .map(Node::getNodeName)
                    .collect(Collectors.toSet());
        } else {
            return Collections.emptySet();
        }
    }
}
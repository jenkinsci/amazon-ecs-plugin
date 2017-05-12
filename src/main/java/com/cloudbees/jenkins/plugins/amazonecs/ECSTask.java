package com.cloudbees.jenkins.plugins.amazonecs;

public interface ECSTask {

    int getMemoryConstraint();

    int getCpu();

    String getRemoteFSRoot();

    String getDisplayName();

    String getLabel();
}

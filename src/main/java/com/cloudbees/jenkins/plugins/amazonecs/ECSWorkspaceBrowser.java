package com.cloudbees.jenkins.plugins.amazonecs;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.lang.ObjectUtils;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.WorkspaceBrowser;

/**
 * Allows access to the Jenkins slave's workspace when the Jenkins slave is offline.
 * The assumption here is that the Jenkins slave's root path (configurable via "Remote FS Root"
 * option) points to a shared storage location (e.g., NFS mount) accessible to the Jenkins master.
 * Note that when the Jenkins slave is online, the workspace is directly provided 
 * by the slave and the control doesn't reach here.
 */
@Extension
public class ECSWorkspaceBrowser extends WorkspaceBrowser {

    private static final Logger LOGGER = Logger.getLogger(ECSWorkspaceBrowser.class.getName());

    private static final String WORKSPACE = "workspace";

    @Override
    public FilePath getWorkspace(final Job job) {
        LOGGER.info("Nodes went offline. Hence fetching it through master");
        final String jobName = job.getName();
        if (job instanceof AbstractProject) {
            final String assignedLabel = ((AbstractProject)job).getAssignedLabelString();
            final ECSCloud ecsCloud = ECSCloud.get();
            if (ecsCloud == null) {
                LOGGER.info("No ECS cloud found.");
            } else {
                LOGGER.info("Found ecs cloud: " + ecsCloud.name);
                final List<ECSTaskTemplate> templates = ecsCloud.getTemplates();
                for (final ECSTaskTemplate template : templates) {
                    LOGGER.info(String.format("Checking ECS template %s with FS root %s", template.getLabel(),
                        template.getRemoteFSRoot()));
                    if (ObjectUtils.equals(template.getLabel(), assignedLabel)) {
                        final String workspacePath =
                            template.getRemoteFSRoot() + File.separator + WORKSPACE + File.separator + jobName;
                        LOGGER.info("Workspace Path: " + workspacePath);
                        final File workspace = new File(workspacePath);
                        LOGGER.info("Workspace exists ? " + workspace.exists());
                        if (workspace.exists()) {
                            return new FilePath(workspace);
                        }
                    }
                }
            }
        }
        return null;
    }
}
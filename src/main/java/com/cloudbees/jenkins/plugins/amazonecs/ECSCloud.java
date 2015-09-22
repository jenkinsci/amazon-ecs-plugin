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

import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.DeregisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.Failure;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.RunTaskRequest;
import com.amazonaws.services.ecs.model.RunTaskResult;
import com.amazonaws.services.ecs.model.StopTaskRequest;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.security.ACL;
import hudson.slaves.Cloud;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.NodeProvisioner;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ECSCloud extends Cloud {

    private final List<ECSTaskTemplate> templates;

    private final String credentialsId;

    private final String cluster;

    private String tunnel;

    @DataBoundConstructor
    public ECSCloud(String name, List<ECSTaskTemplate> templates, String credentialsId, String cluster) {
        super(name);
        this.templates = templates;
        this.credentialsId = credentialsId;
        this.cluster = cluster;
    }

    public List<ECSTaskTemplate> getTemplates() {
        return templates;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getCluster() {
        return cluster;
    }

    public String getTunnel() {
        return tunnel;
    }

    @DataBoundSetter
    public void setTunnel(String tunnel) {
        this.tunnel = tunnel;
    }

    private static AmazonWebServicesCredentials getCredentials(String credentialsId) {
        return (AmazonWebServicesCredentials) CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(AmazonWebServicesCredentials.class, Jenkins.getInstance(),
                        ACL.SYSTEM, Collections.EMPTY_LIST),
                CredentialsMatchers.withId(credentialsId));
    }

    @Override
    public boolean canProvision(Label label) {
        return getTemplate(label) != null;
    }

    private ECSTaskTemplate getTemplate(Label label) {
        for (ECSTaskTemplate t : templates) {
            if (label == null || label.matches(t.getLabelSet())) {
                return t;
            }
        }
        return null;
    }


    @Override
    public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        try {

            List<NodeProvisioner.PlannedNode> r = new ArrayList<NodeProvisioner.PlannedNode>();
            final ECSTaskTemplate template = getTemplate(label);

            for (int i = 1; i <= excessWorkload; i++) {

                r.add(new NodeProvisioner.PlannedNode(template.getDisplayName(), Computer.threadPoolForRemoting
                        .submit(new ProvisioningCallback(template, label)), 1));
            }
            return r;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to provision ECS slave", e);
            return Collections.emptyList();
        }
    }

    void deleteTask(String taskArn, String taskDefinitonArn) {
        final AmazonECSClient client = new AmazonECSClient(getCredentials(credentialsId));

        LOGGER.log(Level.INFO, "Delete ECS Slave task: {0}", taskArn);
        client.stopTask(new StopTaskRequest().withTask(taskArn));

        LOGGER.log(Level.INFO, "Delete ECS task definition: {0}", taskDefinitonArn);
        client.deregisterTaskDefinition(new DeregisterTaskDefinitionRequest().withTaskDefinition(taskDefinitonArn));
    }

    private class ProvisioningCallback implements Callable<Node> {

        private final ECSTaskTemplate template;
        private Label label;

        public ProvisioningCallback(ECSTaskTemplate template, Label label) {
            this.template = template;
            this.label = label;
        }

        public Node call() throws Exception {
            ECSSlave slave = new ECSSlave(ECSCloud.this, UUID.randomUUID().toString(), template.getRemoteFSRoot(), label.toString(), new JNLPLauncher());
            Jenkins.getInstance().addNode(slave);
            LOGGER.log(Level.INFO, "Created Slave: {0}", slave.getNodeName());

            Collection<String> command = getDockerRunCommand(slave);
            final RegisterTaskDefinitionRequest req = template.asRegisterTaskDefinitionRequest(command);

            final AmazonECSClient client = new AmazonECSClient(getCredentials(credentialsId));
            final RegisterTaskDefinitionResult result = client.registerTaskDefinition(req);
            String definitionArn = result.getTaskDefinition().getTaskDefinitionArn();
            LOGGER.log(Level.INFO, "Created Task Definition: {0}", definitionArn);
            slave.setTaskDefinitonArn(definitionArn);

            final RunTaskResult runTaskResult = client.runTask(new RunTaskRequest()
                    .withTaskDefinition(definitionArn)
                    .withCluster(cluster)
            );

            if (! runTaskResult.getFailures().isEmpty()) {
                for (Failure failure : runTaskResult.getFailures()) {
                    LOGGER.log(Level.WARNING, "{0} : {1}", new Object[] { failure.getReason(), failure.getArn() });
                }
                throw new IOException("Failed to run slave container.");
            }

            String taskArn = runTaskResult.getTasks().get(0).getTaskArn();
            LOGGER.log(Level.INFO, "Slave Task Started : {0}", taskArn);
            slave.setTaskArn(taskArn);

            int i = 0;
            int j = 100; // wait 100 seconds

            // now wait for slave to be online
            for (; i < j; i++) {
                if (slave.getComputer() == null) {
                    throw new IllegalStateException("Node was deleted, computer is null");
                }
                if (slave.getComputer().isOnline()) {
                    break;
                }
                LOGGER.log(Level.FINE, "Waiting for slave to connect ({1}/{2}): {0}", new Object[] { taskArn, i, j});
                Thread.sleep(1000);
            }
            if (!slave.getComputer().isOnline()) {
                throw new IllegalStateException("Slave is not connected after " + j + " seconds");
            }

            LOGGER.log(Level.INFO, "Slave connected: {0}", taskArn);
            return slave;
        }
    }

    private Collection<String> getDockerRunCommand(ECSSlave slave) {
        Collection<String> command = new ArrayList<String>();
        command.add("-url");
        command.add(JenkinsLocationConfiguration.get().getUrl());
        if (StringUtils.isNotBlank(tunnel)) {
            command.add("-tunnel");
            command.add(tunnel);
        }
        command.add(slave.getComputer().getJnlpMac());
        command.add(slave.getComputer().getName());
        return command;
    }


    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {

        @Override
        public String getDisplayName() {
            return Messages.DisplayName();
        }

        public ListBoxModel doFillCredentialsIdItems() {
            return new StandardListBoxModel()
                    .withEmptySelection()
                    .withMatching(
                            CredentialsMatchers.always(),
                            CredentialsProvider.lookupCredentials(AmazonWebServicesCredentials.class,
                                    Jenkins.getInstance(),
                                    ACL.SYSTEM,
                                    Collections.EMPTY_LIST));
        }

        public ListBoxModel doFillClusterItems(@QueryParameter String credentialsId) {
            final ListBoxModel options = new ListBoxModel();
            final AmazonECSClient client = new AmazonECSClient(getCredentials(credentialsId));
            for (String arn : client.listClusters().getClusterArns()) {
                options.add(arn);
            }
            return options;
        }

    }

    private static final Logger LOGGER = Logger.getLogger(ECSCloud.class.getName());
}

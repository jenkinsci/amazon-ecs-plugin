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

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.*;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.AbortException;
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

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ECSCloud extends Cloud {

    private final List<ECSTaskTemplate> templates;


    @Nonnull
    private final String awsRegion;

    /**
     * Id of the {@link AmazonWebServicesCredentials} used to connect to Amazon ECS
     */
    @Nonnull
    private final String credentialsId;

    private final String cluster;

    /**
     * Tunnel connection through
     */
    @CheckForNull
    private String tunnel;

    @DataBoundConstructor
    public ECSCloud(String name, List<ECSTaskTemplate> templates, @Nonnull String credentialsId, @Nonnull String awsRegion, String cluster) {
        super(name);
        this.credentialsId = credentialsId;
        this.awsRegion = awsRegion;
        this.cluster = cluster;
        this.templates = templates;
        if (templates != null) {
            for (ECSTaskTemplate template : templates) {
                template.setOwer(this);
            }
        }
    }

    public List<ECSTaskTemplate> getTemplates() {
        return templates;
    }

    public String getAwsRegion() {
        return awsRegion;
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

    @CheckForNull
    static AmazonWebServicesCredentials getCredentials(@Nullable String credentialsId) {
        if (StringUtils.isBlank(credentialsId)) {
            return null;
        }
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

    protected static AmazonECSClient getAmazonECSClient(String credentialsId, String awsRegion){
        final AmazonECSClient client;
        AmazonWebServicesCredentials credentials = getCredentials(credentialsId);
        if (credentials == null) {
            // no credentials provided, rely on com.amazonaws.auth.DefaultAWSCredentialsProviderChain
            // to use IAM Role define at the EC2 instance level ...
            client = new AmazonECSClient();
        } else {
            if (LOGGER.isLoggable(Level.FINE)) {
                String awsAccessKeyId = credentials.getCredentials().getAWSAccessKeyId();
                String obfuscatedAccessKeyId = StringUtils.left(awsAccessKeyId, 4) + StringUtils.repeat("*", awsAccessKeyId.length() - (2 * 4)) + StringUtils.right(awsAccessKeyId, 4);
                LOGGER.log(Level.FINE, "Connect to Amazon ECS with IAM Access Key {1}", new Object[]{obfuscatedAccessKeyId});
            }
            client = new AmazonECSClient(credentials);
        }
        LOGGER.log(Level.INFO,"awsRegion={0}",awsRegion);
        // awsRegion can be null when called from method doFillClusterItems
        if (awsRegion != null && !awsRegion.isEmpty()) {
            client.setRegion(Region.getRegion(Regions.fromName(awsRegion)));
        }
        return client;

    }

    void deleteTask(String taskArn) {
        final AmazonECSClient client = getAmazonECSClient(credentialsId, awsRegion);

        LOGGER.log(Level.INFO, "Delete ECS Slave task: {0}", taskArn);
        try {
            client.stopTask(new StopTaskRequest().withTask(taskArn));
        } catch (ClientException clientException){
            LOGGER.log(Level.INFO, "Failed to delete ECS task definition: {0}", clientException.getLocalizedMessage());
        }
    }

    private class ProvisioningCallback implements Callable<Node> {

        private final ECSTaskTemplate template;
        @CheckForNull
        private Label label;

        public ProvisioningCallback(ECSTaskTemplate template, @Nullable Label label) {
            this.template = template;
            this.label = label;
        }

        public Node call() throws Exception {

            String uniq = Long.toHexString(System.nanoTime());
            ECSSlave slave = new ECSSlave(ECSCloud.this, name + "-" + uniq, template.getRemoteFSRoot(), label == null ? null : label.toString(), new JNLPLauncher());
            Jenkins.getInstance().addNode(slave);
            LOGGER.log(Level.INFO, "Created Slave: {0}", slave.getNodeName());

            final AmazonECSClient client = getAmazonECSClient(credentialsId, awsRegion);
            Collection<String> command = getDockerRunCommand(slave);
            String definitionArn = template.getTaskDefinitionArn();
            slave.setTaskDefinitonArn(definitionArn);

            final RunTaskResult runTaskResult = client.runTask(new RunTaskRequest()
                    .withTaskDefinition(definitionArn)
                    .withOverrides(new TaskOverride()
                        .withContainerOverrides(new ContainerOverride()
                            .withName("jenkins-slave")
                            .withCommand(command)))
                    .withCluster(cluster)
            );

            if (!runTaskResult.getFailures().isEmpty()) {
                LOGGER.log(Level.WARNING, "Slave {0} - Failure to run task with definition {1} on ECS cluster {2}", new Object[]{slave.getNodeName(), definitionArn, cluster});
                for (Failure failure : runTaskResult.getFailures()) {
                    LOGGER.log(Level.WARNING, "Slave {0} - Failure reason={1}, arn={2}", new Object[]{slave.getNodeName(), failure.getReason(), failure.getArn()});
                }
                throw new AbortException("Failed to run slave container " + slave.getNodeName());
            }

            String taskArn = runTaskResult.getTasks().get(0).getTaskArn();
            LOGGER.log(Level.INFO, "Slave {0} - Slave Task Started : {1}", new Object[]{slave.getNodeName(), taskArn});
            slave.setTaskArn(taskArn);

            int i = 0;
            int j = 100; // wait 100 seconds

            // now wait for slave to be online
            for (; i < j; i++) {
                if (slave.getComputer() == null) {
                    throw new IllegalStateException("Slave " + slave.getNodeName() + " - Node was deleted, computer is null");
                }
                if (slave.getComputer().isOnline()) {
                    break;
                }
                LOGGER.log(Level.FINE, "Waiting for slave {0} (ecs task {1}) to connect ({2}/{3}).", new Object[]{slave.getNodeName(), taskArn, i, j});
                Thread.sleep(1000);
            }
            if (!slave.getComputer().isOnline()) {
                throw new IllegalStateException("ECS Slave " + slave.getNodeName()  + " (ecs task " + taskArn + ") is not connected after " + j + " seconds");
            }

            LOGGER.log(Level.INFO, "ECS Slave " + slave.getNodeName() + " (ecs task {0}) connected", taskArn);
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

        public ListBoxModel doFillAwsRegionItems() {
            //please refer following link to get list of available regions  http://docs.aws.amazon.com/general/latest/gr/rande.html#ecs_region
            final String[] ecsEnabledRegions = {"us-east-1", "us-west-2", "us-west-1", "eu-west-1", "ap-northeast-1", "ap-southeast-2", "ap-southeast-1"};
            final ListBoxModel options = new ListBoxModel();

            for (String reg : ecsEnabledRegions) {
                options.add(reg);
            }
            return options;
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

        public ListBoxModel doFillClusterItems(@QueryParameter String credentialsId, @QueryParameter String awsRegion) {
            try {
                final AmazonECSClient client = getAmazonECSClient(credentialsId, awsRegion);

                final ListBoxModel options = new ListBoxModel();
                for (String arn : client.listClusters().getClusterArns()) {
                    options.add(arn);
                }
                return options;
            } catch (RuntimeException e) {
                // missing credentials will throw an "AmazonClientException: Unable to load AWS credentials from any provider in the chain"
                LOGGER.log(Level.INFO, "Exception searching clusters for credentials=" + credentialsId, e);
                return new ListBoxModel();
            }
        }

    }

    private static final Logger LOGGER = Logger.getLogger(ECSCloud.class.getName());
}

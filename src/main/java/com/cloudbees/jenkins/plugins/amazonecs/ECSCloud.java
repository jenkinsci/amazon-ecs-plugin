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

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.ServletException;

import hudson.util.FormValidation;
import com.amazonaws.AmazonClientException;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsHelper;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;

import hudson.AbortException;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.NodeProvisioner;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ECSCloud extends Cloud {
	
	private static final Logger LOGGER = Logger.getLogger(ECSCloud.class.getName());
	
	private static final int DEFAULT_SLAVE_TIMEOUT = 900;
		
    private final List<ECSTaskTemplate> templates;

    /**
     * Id of the {@link AmazonWebServicesCredentials} used to connect to Amazon ECS
     */
    @Nonnull
    private final String credentialsId;

    private final String cluster;

    private String regionName;

    /**
     * Tunnel connection through
     */
    @CheckForNull
    private String tunnel;

    private String jenkinsUrl;
        
    private int slaveTimoutInSeconds;
        
    private ECSService ecsService;
    
	@DataBoundConstructor
    public ECSCloud(String name, List<ECSTaskTemplate> templates, @Nonnull String credentialsId, 
    		String cluster, String regionName, String jenkinsUrl, int slaveTimoutInSeconds) throws InterruptedException{
        super(name);
        this.credentialsId = credentialsId;
        this.cluster = cluster;
        this.templates = templates;
        this.regionName = regionName;
        LOGGER.log(Level.INFO, "Create cloud {0} on ECS cluster {1} on the region {2}", new Object[]{name, cluster, regionName});
        if (templates != null) {
            for (Iterator<ECSTaskTemplate> it = templates.iterator(); it.hasNext(); ) {
                ECSTaskTemplate template = it.next();
                template.setOwner(this);
                if (it.hasNext()) {
                    // JENKINS-36857 AWS throttling error when saving master config
                    // http://docs.aws.amazon.com/AmazonECS/latest/developerguide/service_limits.html
                    // "Throttle on task definition registration rate -> 1 per second / 60 max per minute"
                    Thread.sleep(1000);
                }
            }
        }
        
        if(StringUtils.isNotBlank(jenkinsUrl)) {
        	this.jenkinsUrl = jenkinsUrl;
        } else {
        	this.jenkinsUrl = JenkinsLocationConfiguration.get().getUrl();
        }

        if(slaveTimoutInSeconds > 0) {
        	this.slaveTimoutInSeconds = slaveTimoutInSeconds;
        } else {
        	this.slaveTimoutInSeconds = DEFAULT_SLAVE_TIMEOUT;
        }
    }

	synchronized ECSService getEcsService() {
		if (ecsService == null) {
			ecsService = new ECSService(credentialsId, regionName);
		}
		return ecsService;
	}

	AmazonECSClient getAmazonECSClient() {
		return getEcsService().getAmazonECSClient();
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

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }

    public String getTunnel() {
        return tunnel;
    }

    @DataBoundSetter
    public void setTunnel(String tunnel) {
        this.tunnel = tunnel;
    }

    @CheckForNull
    private static AmazonWebServicesCredentials getCredentials(@Nullable String credentialsId) {
    	return AWSCredentialsHelper.getCredentials(credentialsId, Jenkins.getActiveInstance());
    }

    @Override
    public boolean canProvision(Label label) {
        return getTemplate(label) != null;
    }

    private ECSTaskTemplate getTemplate(Label label) {
        if (label == null) {
            return null;
        }
        for (ECSTaskTemplate t : templates) {
            if (label.matches(t.getLabelSet())) {
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

 	void deleteTask(String taskArn, String clusterArn) {
		getEcsService().deleteTask(taskArn, clusterArn);
	}

	public int getSlaveTimoutInSeconds() {
		return slaveTimoutInSeconds;
	}

	public void setSlaveTimoutInSeconds(int slaveTimoutInSeconds) {
		this.slaveTimoutInSeconds = slaveTimoutInSeconds;
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
			final ECSSlave slave;

			Date now = new Date();
			Date timeout = new Date(now.getTime() + 1000 * slaveTimoutInSeconds);

			synchronized (cluster) {
				getEcsService().waitForSufficientClusterResources(timeout, template, cluster);

				String uniq = Long.toHexString(System.nanoTime());
				slave = new ECSSlave(ECSCloud.this, name + "-" + uniq, template.getRemoteFSRoot(),
						label == null ? null : label.toString(), new JNLPLauncher());
				slave.setClusterArn(cluster);
				Jenkins.getInstance().addNode(slave);
				while (Jenkins.getInstance().getNode(slave.getNodeName()) == null) {
					Thread.sleep(1000);
				}
				LOGGER.log(Level.INFO, "Created Slave: {0}", slave.getNodeName());

				try {
					String taskArn = getEcsService().runEcsTask(slave, template, cluster, getDockerRunCommand(slave));
					LOGGER.log(Level.INFO, "Slave {0} - Slave Task Started : {1}",
							new Object[] { slave.getNodeName(), taskArn });
					slave.setTaskArn(taskArn);
				} catch (AbortException ex) {
					Jenkins.getInstance().removeNode(slave);
					throw ex;
				}
			}

			// now wait for slave to be online
			while (timeout.after(new Date())) {
				if (slave.getComputer() == null) {
					throw new IllegalStateException(
							"Slave " + slave.getNodeName() + " - Node was deleted, computer is null");
				}
				if (slave.getComputer().isOnline()) {
					break;
				}
				LOGGER.log(Level.FINE, "Waiting for slave {0} (ecs task {1}) to connect since {2}.",
						new Object[] { slave.getNodeName(), slave.getTaskArn(), now });
				Thread.sleep(1000);
			}
			if (!slave.getComputer().isOnline()) {
				final String msg = MessageFormat.format("ECS Slave {0} (ecs task {1}) not connected since {2} seconds",
						slave.getNodeName(), slave.getTaskArn(), now);
				LOGGER.log(Level.WARNING, msg);
				Jenkins.getInstance().removeNode(slave);
				throw new IllegalStateException(msg);
			}

			LOGGER.log(Level.INFO, "ECS Slave " + slave.getNodeName() + " (ecs task {0}) connected",
					slave.getTaskArn());
			return slave;
		}
	}

    private Collection<String> getDockerRunCommand(ECSSlave slave) {
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


    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {

        @Override
        public String getDisplayName() {
            return Messages.DisplayName();
        }

        public ListBoxModel doFillCredentialsIdItems() {
            return AWSCredentialsHelper.doFillCredentialsIdItems(Jenkins.getActiveInstance());
        }

        public ListBoxModel doFillRegionNameItems() {
            final ListBoxModel options = new ListBoxModel();
            for (Region region : RegionUtils.getRegions()) {
                options.add(region.getName());
            }
            return options;
        }

        public ListBoxModel doFillClusterItems(@QueryParameter String credentialsId, @QueryParameter String regionName) {
        	ECSService ecsService = new ECSService(credentialsId, regionName);
        	try {
        	    final AmazonECSClient client = ecsService.getAmazonECSClient();
                final ListBoxModel options = new ListBoxModel();
                for (String arn : client.listClusters().getClusterArns()) {
                    options.add(arn);
                }
                return options;
            } catch (AmazonClientException e) {
                // missing credentials will throw an "AmazonClientException: Unable to load AWS credentials from any provider in the chain"
                LOGGER.log(Level.INFO, "Exception searching clusters for credentials=" + credentialsId + ", regionName=" + regionName + ":" + e);
                LOGGER.log(Level.FINE, "Exception searching clusters for credentials=" + credentialsId + ", regionName=" + regionName, e);
                return new ListBoxModel();
            } catch (RuntimeException e) {
                LOGGER.log(Level.INFO, "Exception searching clusters for credentials=" + credentialsId + ", regionName=" + regionName, e);
                return new ListBoxModel();
            }
        }

        public FormValidation doCheckName(@QueryParameter String value) throws IOException, ServletException {
            if (value.length() == 0) {
                return FormValidation.error("Please set a name");
            }
            //Add check for spaces
            Pattern pattern = Pattern.compile("\\s");
            Matcher matcher = pattern.matcher(value);
            boolean found = matcher.find();
            if (found) {
                return FormValidation.error("Please do not use spaces.");
            }
            return FormValidation.ok();
        }

    }

    public static Region getRegion(String regionName) {
        if (StringUtils.isNotEmpty(regionName)) {
            return RegionUtils.getRegion(regionName);
        } else {
            return Region.getRegion(Regions.US_EAST_1);
        }
    }
    
	public String getJenkinsUrl() {
		return jenkinsUrl;
	}

	public void setJenkinsUrl(String jenkinsUrl) {
		this.jenkinsUrl = jenkinsUrl;
	}
}

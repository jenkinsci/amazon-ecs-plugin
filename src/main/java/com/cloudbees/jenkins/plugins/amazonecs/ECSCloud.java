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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;

import com.amazonaws.AmazonClientException;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.cloudbees.jenkins.plugins.amazonecs.pipeline.TaskTemplateMap;
import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsHelper;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ECSCloud extends Cloud {

    private static final Logger LOGGER = Logger.getLogger(ECSCloud.class.getName());

    private List<ECSTaskTemplate> templates;
    @Nonnull
    private final String credentialsId;
    private final String cluster;
    private String regionName;
    @CheckForNull
    private String tunnel;
    private String jenkinsUrl;
    private int retentionTimeout = DescriptorImpl.DEFAULT_RETENTION_TIMEOUT;
    private int slaveTimeoutInSeconds = DescriptorImpl.DEFAULT_SLAVE_TIMEOUT_IN_SECONDS;
    private ECSService ecsService;
    private String allowedOverrides;
    private int maxCpu;
    private int maxMemory;
    private int maxMemoryReservation;

    @DataBoundConstructor
    public ECSCloud(String name,
                    @Nonnull String credentialsId,
                    String cluster) throws InterruptedException {
        super(name);
        this.credentialsId = credentialsId;
        this.cluster = cluster;
    }

    public static @Nonnull ECSCloud getByName(@Nonnull String name) throws IllegalArgumentException {
        Cloud cloud = Jenkins.get().clouds.getByName(name);
        if (cloud instanceof ECSCloud) return (ECSCloud) cloud;
        throw new IllegalArgumentException("'" + name + "' is not an ECS cloud but " + cloud);
        }

    synchronized ECSService getEcsService() {
        if (ecsService == null) {
            ecsService = new ECSService(credentialsId, regionName);
        }
        return ecsService;
    }

    @Nonnull
    public List<ECSTaskTemplate> getTemplates() {
        return templates != null ? templates : Collections.<ECSTaskTemplate> emptyList();
    }

    @Nonnull
    private List<ECSTaskTemplate> getAllTemplates() {
        List<ECSTaskTemplate> dynamicTemplates = TaskTemplateMap.get().getTemplates(this);
        List<ECSTaskTemplate> allTemplates = new CopyOnWriteArrayList<>();

        allTemplates.addAll(dynamicTemplates);
        if (templates != null) {
            allTemplates.addAll(templates);
        }
        return allTemplates;
    }

    @DataBoundSetter
    public void setTemplates(List<ECSTaskTemplate> templates) {
        this.templates = templates;
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

    @DataBoundSetter
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

    @DataBoundSetter
    public void setAllowedOverrides(@Nonnull String allowedOverrides) {
        this.allowedOverrides = allowedOverrides.equals(DescriptorImpl.DEFAULT_ALLOWED_OVERRIDES) ? null : allowedOverrides;
    }

    @Nonnull
    public String getAllowedOverrides() {
        return allowedOverrides == null ? DescriptorImpl.DEFAULT_ALLOWED_OVERRIDES : allowedOverrides;
    }

    public boolean isAllowedOverride(String override) {
        List<String> allowedOverridesList = Arrays.asList(getAllowedOverrides().toLowerCase().replaceAll(" ", "").split(","));
        if (allowedOverridesList.contains("all")) return true;
        return allowedOverridesList.contains(override.toLowerCase());
    }

    @Override
    public boolean canProvision(Label label) {
        return getTemplate(label) != null;
    }

    public boolean canProvision(String label) {
        return getTemplate(label) != null;
    }

    private ECSTaskTemplate getTemplate(Label label) {
        if (label == null) {
            return null;
        }
        for (ECSTaskTemplate t : getAllTemplates()) {
            if (label.matches(t.getLabelSet())) {
                return t;
            }
        }
        return null;
    }

    private ECSTaskTemplate getTemplate(String label) {
        if (label == null) {
            return null;
        }
        for (ECSTaskTemplate t : getAllTemplates()) {
            if (label.matches(t.getLabel())) {
                return t;
            }
        }
        return null;
    }

    @Override
    public synchronized Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {

        try {
            LOGGER.log(Level.INFO, "Asked to provision {0} agent(s) for: {1}", new Object[]{excessWorkload, label});

            Set<String> allInProvisioning = InProvisioning.getAllInProvisioning(label);
            LOGGER.log(Level.INFO, "In provisioning : " + allInProvisioning);
            int toBeProvisioned = Math.max(0, excessWorkload - allInProvisioning.size());
            LOGGER.log(Level.INFO, "Excess workload after pending ECS agents: {0}", toBeProvisioned);

            List<NodeProvisioner.PlannedNode> r = new ArrayList<NodeProvisioner.PlannedNode>();
            final ECSTaskTemplate template = getTemplate(label);
            String parentLabel = template.getInheritFrom();
            final ECSTaskTemplate merged = template.merge(getTemplate(parentLabel));

            for (int i = 1; i <= toBeProvisioned; i++) {
            LOGGER.log(Level.INFO, "Will provision {0}, for label: {1}", new Object[]{merged.getDisplayName(), label} );

                r.add(new NodeProvisioner.PlannedNode(template.getDisplayName(), Computer.threadPoolForRemoting.submit(new ProvisioningCallback(merged)), 1));
            }
            return r;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to provision ECS agent", e);
        }
        return Collections.emptyList();
    }

    public int getSlaveTimeoutInSeconds() {
        // this is only needed for edge cases, where in the config was nothing set
        // and then 0 is assumed as default which breaks things.

        if (this.slaveTimeoutInSeconds == 0) {
            return DescriptorImpl.DEFAULT_SLAVE_TIMEOUT_IN_SECONDS;
        } else {
            return this.slaveTimeoutInSeconds;
        }
    }

    @DataBoundSetter
    public void setSlaveTimeoutInSeconds(int slaveTimeoutInSeconds) {
        this.slaveTimeoutInSeconds = slaveTimeoutInSeconds;
    }

    public int getRetentionTimeout() {
        // this is only needed for edge cases, where in the config was nothing set
        // and then 0 is assumed as default which breaks things.

        if (this.retentionTimeout == 0) {
            return DescriptorImpl.DEFAULT_RETENTION_TIMEOUT;
        } else {
            return this.retentionTimeout;
        }
    }

    @DataBoundSetter
    public void setRetentionTimeout(int retentionTimeout) {
        this.retentionTimeout = retentionTimeout;
    }

    public int getMaxCpu() {
        return maxCpu;
    }

    @DataBoundSetter
    public void setMaxCpu(int maxCpu) {
        this.maxCpu = maxCpu;
    }

    public int getMaxMemory() {
        return maxMemory;
    }

    @DataBoundSetter
    public void setMaxMemory(int maxMemory) {
        this.maxMemory = maxMemory;
    }

    public int getMaxMemoryReservation() {
        return maxMemoryReservation;
    }

    @DataBoundSetter
    public void setMaxMemoryReservation(int maxMemoryReservation) {
        this.maxMemoryReservation = maxMemoryReservation;
    }


    private class ProvisioningCallback implements Callable<Node> {

        private final ECSTaskTemplate template;

        public ProvisioningCallback(ECSTaskTemplate template) {
            this.template = template;
        }

        public Node call() throws Exception {
            String uniq = RandomStringUtils.random(5, "bcdfghjklmnpqrstvwxz0123456789");
            return new ECSSlave(ECSCloud.this, name + "-" + uniq, template, new ECSLauncher(ECSCloud.this, tunnel, null));
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

    @DataBoundSetter
    public void setJenkinsUrl(String jenkinsUrl) {
        if(StringUtils.isNotBlank(jenkinsUrl)) {
            this.jenkinsUrl = jenkinsUrl;
        } else {
            JenkinsLocationConfiguration config = JenkinsLocationConfiguration.get();
            if (config != null) {
                this.jenkinsUrl = config.getUrl();
            }
        }
    }

    /**
     * Add a dynamic task template. Won't be displayed in UI, and persisted separately from the cloud instance.
     * @param t the template to add
     */
    public void addDynamicTemplate(ECSTaskTemplate t) {
        TaskTemplateMap.get().addTemplate(this, t);
    }

    /**
     * Remove a dynamic task template.
     * @param t the template to remove
     */
    public void removeDynamicTemplate(ECSTaskTemplate t) {
        getEcsService().removeTemplate(this, t);
        TaskTemplateMap.get().removeTemplate(this, t);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {
        public static final int DEFAULT_RETENTION_TIMEOUT = 5;
        public static final int DEFAULT_SLAVE_TIMEOUT_IN_SECONDS= 900;
        public static final String DEFAULT_ALLOWED_OVERRIDES = "";
        private static String CLOUD_NAME_PATTERN = "[a-z|A-Z|0-9|_|-]{1,127}";

        @Override
        public String getDisplayName() {
            return Messages.displayName();
        }

        public ListBoxModel doFillCredentialsIdItems() {
            return AWSCredentialsHelper.doFillCredentialsIdItems(Jenkins.get());
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
                final AmazonECS client = ecsService.getAmazonECSClient();
                final List<String> allClusterArns = new ArrayList<String>();
                String lastToken = null;
                do {
                    ListClustersResult result = client.listClusters(new ListClustersRequest().withNextToken(lastToken));
                    allClusterArns.addAll(result.getClusterArns());
                    lastToken = result.getNextToken();
                } while (lastToken != null);
                Collections.sort(allClusterArns);
                final ListBoxModel options = new ListBoxModel();
                for (final String arn : allClusterArns) {
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
            if (value.length() > 0 && value.length() <= 127 && value.matches(CLOUD_NAME_PATTERN)) {
                return FormValidation.ok();
            }
            return FormValidation.error("Up to 127 letters (uppercase and lowercase), numbers, hyphens, and underscores are allowed");
        }

        public FormValidation doCheckRetentionTimeout(@QueryParameter Integer value) throws IOException, ServletException {
           if (value > 0) {
                return FormValidation.ok();
            }
            return FormValidation.error("Needs to be greater than 0");
        }

    }
}

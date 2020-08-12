package com.cloudbees.jenkins.plugins.amazonecs.aws;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsHelper;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public abstract class BaseAWSService {

    Region getRegion(String regionName) {
        if (StringUtils.isNotEmpty(regionName)) {
            return RegionUtils.getRegion(regionName);
        } else {
            return Region.getRegion(Regions.US_EAST_1);
        }
    }

    @CheckForNull
    protected AmazonWebServicesCredentials getCredentials(@Nullable String credentialsId) {
        return AWSCredentialsHelper.getCredentials(credentialsId, Jenkins.get());
    }

    protected ClientConfiguration createClientConfiguration() {
        ProxyConfiguration proxy = Jenkins.get().proxy;
        ClientConfiguration clientConfiguration = new ClientConfiguration();

        if (proxy != null) {
            clientConfiguration.setProxyHost(proxy.name);
            clientConfiguration.setProxyPort(proxy.port);
            clientConfiguration.setProxyUsername(proxy.getUserName());
            clientConfiguration.setProxyPassword(proxy.getPassword());
        }

        // Default is 3. 10 helps us actually utilize the SDK's backoff strategy
        // The strategy will wait up to 20 seconds per request (after multiple failures)
        clientConfiguration.setMaxErrorRetry(10);

        return clientConfiguration;
    }
}

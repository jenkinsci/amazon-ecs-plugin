package com.cloudbees.jenkins.plugins.amazonecs.aws;

import java.util.logging.Logger;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonWebServiceRequest;

import java.util.logging.Level;

public class RetryCondition extends com.amazonaws.retry.PredefinedRetryPolicies.SDKDefaultRetryCondition {
    private static final Logger LOGGER = Logger.getLogger(RetryCondition.class.getName());

    @Override
    public boolean shouldRetry(AmazonWebServiceRequest originalRequest, AmazonClientException exception, int retriesAttempted){
        if (super.shouldRetry(originalRequest, exception, retriesAttempted)){
            LOGGER.log(Level.INFO, "retrying request {0} because of {1}, retried {2} time(s)", new Object[]{originalRequest, exception, retriesAttempted});
            return true;
        }
        return false;
    }
}
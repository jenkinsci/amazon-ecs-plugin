package com.cloudbees.jenkins.plugins.amazonecs.aws;

import com.amazonaws.waiters.PollingStrategy;
import com.amazonaws.waiters.PollingStrategyContext;

public class MaxTimeRetryStrategy implements PollingStrategy.RetryStrategy {

    /**
     * Represents timestamp when to stop polling
     */
    private final long EndTime;

    /**
     * Constructs a new MaxTimeRetryStrategy with the given
     * default length of time to keep retrying
     *
     * @param defaultMaxAttempts
     */
    public MaxTimeRetryStrategy(long defaultMaxTime) {
        this.EndTime = System.currentTimeMillis() + defaultMaxTime;
    }

    /**
     * Default way of checking if polling should be retried or fast failed
     *
     * @param pollingStrategyContext Provides the polling context required to make the retry decision
     * @return false if we're passed our allowed time
     */
    @Override
    public boolean shouldRetry(PollingStrategyContext pollingStrategyContext) {
        return System.currentTimeMillis() < EndTime;
    }
}

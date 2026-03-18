package com.cloudbees.jenkins.plugins.amazonecs;

import hudson.model.Label;
import hudson.model.LoadStatistics;
import hudson.model.labels.LabelAtom;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.NodeProvisioner;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.lang.reflect.Constructor;
import java.util.Collections;

/**
 * Tests for {@link ECSProvisioningStrategy}, focusing on the pending-executor
 * adjustment that prevents duplicate ECS tasks from being launched while a
 * JNLP agent is starting up (issue #311).
 *
 * <p>ECS agents use JNLP (agent-initiated connection), so they are never
 * counted in {@code connectingExecutors} by Jenkins' LoadStatistics while the
 * container boots. Without the fix, every provisioner cycle that fires before
 * the agent phones home would see {@code excessWorkload > 0} and launch
 * another duplicate task. The strategy now subtracts offline ECSSlave nodes
 * matching the requested label from {@code excessWorkload} to treat them as
 * already-pending capacity.
 */
public class ECSProvisioningStrategyTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private ECSCloud makeCloud(String labelName) {
        ECSCloud cloud = new ECSCloud("test-cloud", "", "", "test-cluster");
        cloud.setTemplates(Collections.singletonList(makeTemplate(labelName)));
        cloud.setRegionName("eu-west-1");
        cloud.setNumExecutors(1);
        cloud.setJenkinsUrl("http://jenkins.local");
        cloud.setSlaveTimeoutInSeconds(5);
        cloud.setRetentionTimeout(5);
        return cloud;
    }

    private ECSTaskTemplate makeTemplate(String labelName) {
        return new ECSTaskTemplate(
                "template", labelName, "", "", null, "image",
                "repositoryCredentials", "launchType", "operatingSystemFamily",
                "cpuArchitecture", false, null, "networkMode", "remoteFSRoot",
                false, null, 0, 0, 0, null, null, null, false, false,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, 0, false);
    }

    /**
     * Creates a {@link NodeProvisioner.StrategyState} via reflection because
     * its constructor is package-private in Jenkins core.
     */
    private NodeProvisioner.StrategyState createStrategyState(Label label, int queueLength)
            throws Exception {
        return createStrategyState(label, queueLength, 0);
    }

    private NodeProvisioner.StrategyState createStrategyState(Label label, int queueLength, int plannedCapacity)
            throws Exception {
        LoadStatistics.LoadStatisticsSnapshot snap =
                LoadStatistics.LoadStatisticsSnapshot.builder()
                        .withQueueLength(queueLength)
                        .build();

        Constructor<NodeProvisioner.StrategyState> ctor =
                NodeProvisioner.StrategyState.class.getDeclaredConstructor(
                        NodeProvisioner.class,
                        LoadStatistics.LoadStatisticsSnapshot.class,
                        Label.class,
                        int.class);
        ctor.setAccessible(true);
        return ctor.newInstance(label.nodeProvisioner, snap, label, plannedCapacity);
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    /**
     * Baseline: when there are no offline ECS nodes the strategy should
     * provision the workload normally and record planned capacity.
     */
    @Test
    public void apply_noOfflineECSNodes_provisionsWhenWorkloadExists() throws Exception {
        Label label = new LabelAtom("my-label");
        j.jenkins.clouds.add(makeCloud("my-label"));

        NodeProvisioner.StrategyState state = createStrategyState(label, 1);
        new ECSProvisioningStrategy().apply(state);

        Assert.assertTrue(
                "Should have recorded planned capacity when no pending ECS nodes exist",
                state.getAdditionalPlannedCapacity() > 0);
    }

    /**
     * Core regression test for issue #311.
     * An offline ECSSlave with a matching label represents a JNLP agent that
     * has been created in Jenkins but has not yet phoned home.  The strategy
     * must treat its executors as already-pending and must NOT provision an
     * additional task.
     */
    @Test
    public void apply_offlineECSNodeMatchingLabel_suppressesProvisioning() throws Exception {
        Label label = new LabelAtom("my-label");
        ECSCloud cloud = makeCloud("my-label");
        j.jenkins.clouds.add(cloud);

        // Simulate a JNLP agent that was provisioned but has not yet connected
        ECSSlave pendingAgent = new ECSSlave(cloud, "pending-agent",
                makeTemplate("my-label"), new JNLPLauncher());
        j.jenkins.addNode(pendingAgent);   // offline by default (no JNLP channel)

        NodeProvisioner.StrategyState state = createStrategyState(label, 1);
        NodeProvisioner.StrategyDecision decision = new ECSProvisioningStrategy().apply(state);

        Assert.assertEquals(
                "Should return PROVISIONING_COMPLETED when pending node covers workload",
                NodeProvisioner.StrategyDecision.PROVISIONING_COMPLETED, decision);
        Assert.assertEquals(
                "Should not provision additional tasks while a JNLP agent is starting up",
                0, state.getAdditionalPlannedCapacity());
    }

    /**
     * Label filter: an offline ECSSlave with a *different* label must not
     * suppress provisioning for the requested label.
     */
    @Test
    public void apply_offlineECSNodeDifferentLabel_provisionsNormally() throws Exception {
        Label label = new LabelAtom("my-label");
        ECSCloud cloud = makeCloud("my-label");
        j.jenkins.clouds.add(cloud);

        // Offline agent has a DIFFERENT label — should not count as pending
        ECSSlave wrongLabelAgent = new ECSSlave(cloud, "wrong-label-agent",
                makeTemplate("other-label"), new JNLPLauncher());
        j.jenkins.addNode(wrongLabelAgent);

        NodeProvisioner.StrategyState state = createStrategyState(label, 1);
        new ECSProvisioningStrategy().apply(state);

        Assert.assertTrue(
                "Should provision when the only offline ECS node has a non-matching label",
                state.getAdditionalPlannedCapacity() > 0);
    }

    /**
     * Partial suppression: when queue depth exceeds the number of executors
     * on pending offline nodes the strategy should provision only the
     * remaining gap.
     *
     * <p>E.g. queue=2, one offline agent (1 executor) → gap of 1 → provisions 1 more.
     */
    @Test
    public void apply_offlineECSNodeCoversPartOfWorkload_provisionsRemainder() throws Exception {
        Label label = new LabelAtom("my-label");
        ECSCloud cloud = makeCloud("my-label");
        j.jenkins.clouds.add(cloud);

        // One offline agent covers 1 of the 2 queued jobs
        ECSSlave pendingAgent = new ECSSlave(cloud, "pending-agent",
                makeTemplate("my-label"), new JNLPLauncher());
        j.jenkins.addNode(pendingAgent);

        // Queue length 2, one pending agent → remaining excess = 1
        NodeProvisioner.StrategyState state = createStrategyState(label, 2);
        new ECSProvisioningStrategy().apply(state);

        Assert.assertEquals(
                "Should provision exactly the remaining gap after accounting for pending node",
                1, state.getAdditionalPlannedCapacity());
    }

    /**
     * plannedCapacitySnapshot covers nodes provisioned in a previous cycle whose
     * ProvisioningCallback futures have not yet completed (not yet in Jenkins.getNodes()).
     * The strategy must subtract this from excessWorkload to avoid launching duplicates
     * when the provisioner is triggered again before those futures resolve.
     */
    @Test
    public void apply_plannedCapacityFromPreviousCycle_suppressesProvisioning() throws Exception {
        Label label = new LabelAtom("my-label");
        j.jenkins.clouds.add(makeCloud("my-label"));

        // Simulate 1 job queued, 1 agent already planned in a previous cycle
        // (its ProvisioningCallback future not yet done → not in Jenkins.getNodes())
        NodeProvisioner.StrategyState state = createStrategyState(label, 1, 1);
        NodeProvisioner.StrategyDecision decision = new ECSProvisioningStrategy().apply(state);

        Assert.assertEquals(
                "Should return PROVISIONING_COMPLETED when plannedCapacitySnapshot covers workload",
                NodeProvisioner.StrategyDecision.PROVISIONING_COMPLETED, decision);
        Assert.assertEquals(
                "Should not provision additional tasks when previous cycle already planned enough",
                0, state.getAdditionalPlannedCapacity());
    }

    /**
     * Multiple pending agents: two offline ECSSlaves with matching labels
     * should fully suppress provisioning for a queue of two.
     */
    @Test
    public void apply_multipleOfflineECSNodesCoversWorkload_suppressesProvisioning() throws Exception {
        Label label = new LabelAtom("my-label");
        ECSCloud cloud = makeCloud("my-label");
        j.jenkins.clouds.add(cloud);

        j.jenkins.addNode(new ECSSlave(cloud, "pending-agent-1",
                makeTemplate("my-label"), new JNLPLauncher()));
        j.jenkins.addNode(new ECSSlave(cloud, "pending-agent-2",
                makeTemplate("my-label"), new JNLPLauncher()));

        NodeProvisioner.StrategyState state = createStrategyState(label, 2);
        NodeProvisioner.StrategyDecision decision = new ECSProvisioningStrategy().apply(state);

        Assert.assertEquals(NodeProvisioner.StrategyDecision.PROVISIONING_COMPLETED, decision);
        Assert.assertEquals(
                "Two offline agents should suppress provisioning for a queue of two",
                0, state.getAdditionalPlannedCapacity());
    }
}

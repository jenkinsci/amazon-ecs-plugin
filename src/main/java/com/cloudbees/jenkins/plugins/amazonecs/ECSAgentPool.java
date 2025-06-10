package com.cloudbees.jenkins.plugins.amazonecs;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.scheduler.CronTabList;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

public class ECSAgentPool extends AbstractDescribableImpl<ECSAgentPool> implements Serializable {

    private static final long serialVersionUID = 7831619059473567435L;

    /**
     * Unique identifier for this pool.
     */
    private String id;


    public ECSAgentPool(@CheckForNull String label, String id, int minIdleAgents, @CheckForNull String maintainSchedule, int maxIdleMinutes, @CheckForNull String description) {
        this.label = label;
        this.id = StringUtils.isBlank(id) ? UUID.randomUUID().toString() : id;
        this.minIdleAgents = minIdleAgents;
        this.maintainSchedule = maintainSchedule;
        this.maxIdleMinutes = maxIdleMinutes;
        this.description = description;
    }

    /**
     * White-space separated list of {@link hudson.model.Node} labels.
     *
     * @see Label
     */
    @CheckForNull
    private final String label;

    /**
     * Minimum number of idle agents to keep running for this template.
     */
    private int minIdleAgents;

    /**
     * Cron schedule defining when {@link #minIdleAgents} should be maintained.
     */
    @CheckForNull
    private String maintainSchedule;

    private int maxIdleMinutes;

    @CheckForNull
    private String description;

    @DataBoundSetter
    public void setMinIdleAgents(int minIdleAgents) {
        this.minIdleAgents = Math.max(0, minIdleAgents);
    }

    @DataBoundSetter
    public void setMaintainSchedule(String maintainSchedule) {
        this.maintainSchedule = StringUtils.trimToNull(maintainSchedule);
    }

    @DataBoundSetter
    public void setMaxIdleMinutes(int maxIdleMinutes) {
        this.maxIdleMinutes = maxIdleMinutes;
    }

    public String getId() {
        return id;
    }

    @DataBoundSetter
    public void setId(String id) {
        this.id = id;
    }

    @CheckForNull
    public String getLabel() {
        return label;
    }

    public int getMinIdleAgents() {
        return minIdleAgents;
    }

    public String getMaintainSchedule() {
        return maintainSchedule;
    }

    public boolean isScheduleActive() {
        if (StringUtils.isEmpty(maintainSchedule)) {
            return true;
        }
        try {
            CronTabList tabs = CronTabList.create(maintainSchedule);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date());
            return tabs.check(calendar);
        } catch (ANTLRException e) {
            return true;
        }
    }

    public int getMaxIdleMinutes() {
        return maxIdleMinutes;
    }

    @CheckForNull
    public String getDescription() {
        return description;
    }

    public void setDescription(@CheckForNull String description) {
        this.description = description;
    }

    private Object readResolve() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        return this;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ECSAgentPool> {
        @Override
        public String getDisplayName() {
            return Messages.agentPool();
        }

        public FormValidation doCheckLabel(@QueryParameter String value) {
            if (StringUtils.isBlank(value)) {
                return FormValidation.error("Label is required");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckMinIdleAgents(@QueryParameter String value) {
            if (StringUtils.isBlank(value)) {
                return FormValidation.ok();
            }
            try {
                int v = Integer.parseInt(value);
                if (v >= 0) {
                    return FormValidation.ok();
                }
            } catch (NumberFormatException e) {
                // fall through to error
            }
            return FormValidation.error("Must be a non-negative integer");
        }

        public FormValidation doCheckMaxIdleMinutes(@QueryParameter String value) {
            if (StringUtils.isBlank(value)) {
                return FormValidation.ok();
            }
            try {
                int v = Integer.parseInt(value);
                if (v >= 0) {
                    return FormValidation.ok();
                }
            } catch (NumberFormatException e) {
                // fall through to error
            }
            return FormValidation.error("Must be a non-negative integer");
        }

        public FormValidation doCheckMaintainSchedule(@QueryParameter String value) {
            if (StringUtils.isBlank(value)) {
                return FormValidation.ok();
            }
            try {
                CronTabList.create(value);
                return FormValidation.ok();
            } catch (ANTLRException e) {
                return FormValidation.error(e, e.getMessage());
            }
        }

        public FormValidation doCheckDescription(@QueryParameter String value) {
            if (StringUtils.isBlank(value)) {
                return FormValidation.error("Description is required");
            }
            return FormValidation.ok();
        }
    }
}


package com.cloudbees.jenkins.plugins.amazonecs.pipeline;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.cloudbees.jenkins.plugins.amazonecs.ECSCloud;
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.util.CopyOnWriteMap;

/**
 * A map of {@link ECSCloud} -&gt; List of {@link ECSTaskTemplate} instances.
 */
@Extension
public class TaskTemplateMap {
    private static final Logger LOGGER = Logger.getLogger(TaskTemplateMap.class.getName());

    public static TaskTemplateMap get() {
        // TODO Replace with lookupSingleton post 2.87
        return ExtensionList.lookup(TaskTemplateMap.class).get(TaskTemplateMap.class);
    }

    /**
     * List of Task Templates indexed by cloud name
     */
    private Map<String, List<ECSTaskTemplate>> map = new CopyOnWriteMap.Hash<>();

    /**
     * Returns a read-only view of the templates available for the corresponding cloud instance.
     * @param cloud The ECS cloud instance for which templates are needed
     * @return a read-only view of the templates available for the corresponding cloud instance.
     */
    @Nonnull
    public List<ECSTaskTemplate> getTemplates(@Nonnull ECSCloud cloud) {
        return Collections.unmodifiableList(getOrCreateTemplateList(cloud));
    }

    private List<ECSTaskTemplate> getOrCreateTemplateList(@Nonnull ECSCloud cloud) {
        List<ECSTaskTemplate> TaskTemplates = map.get(cloud.name);
        return TaskTemplates == null ? new CopyOnWriteArrayList<>() : TaskTemplates;
    }

    /**
     * Adds a template for the corresponding cloud instance.
     * @param cloud The cloud instance.
     * @param TaskTemplate The pod template to add.
     */
    public void addTemplate(@Nonnull ECSCloud cloud, @Nonnull ECSTaskTemplate TaskTemplate) {
        List<ECSTaskTemplate> list = getOrCreateTemplateList(cloud);
        list.add(TaskTemplate);
        map.put(cloud.name, list);
    }

    public void removeTemplate(@Nonnull ECSCloud cloud, @Nonnull ECSTaskTemplate TaskTemplate) {
        getOrCreateTemplateList(cloud).remove(TaskTemplate);
    }
}
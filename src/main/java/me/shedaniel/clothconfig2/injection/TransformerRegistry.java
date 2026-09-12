package me.shedaniel.clothconfig2.injection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton registry for managing {@link ITransformer} instances.
 *
 * Provides named registration, priority-based ordering, and
 * automatic synchronization with {@link AgentMain}'s master
 * transformer pipeline.
 *
 * All public methods are thread-safe.
 */
public class TransformerRegistry {

    private static final TransformerRegistry INSTANCE = new TransformerRegistry();

    private final Map<String, ITransformer> transformers = new ConcurrentHashMap<>();
    private final List<ITransformer> sortedTransformers = new ArrayList<>();
    private volatile boolean dirty = true;

    /**
     * Returns the singleton instance.
     */
    public static TransformerRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a transformer using its own {@link ITransformer#getName()} as the key.
     * Also adds it to the agent's master pipeline.
     */
    public void register(ITransformer transformer) {
        transformers.put(transformer.getName(), transformer);
        AgentMain.registerTransformer(transformer);
        dirty = true;
    }

    /**
     * Registers a transformer under a custom name.
     */
    public void register(String name, ITransformer transformer) {
        transformers.put(name, transformer);
        AgentMain.registerTransformer(transformer);
        dirty = true;
    }

    /**
     * Unregisters a transformer by name and removes it from the master pipeline.
     */
    public void unregister(String name) {
        ITransformer removed = transformers.remove(name);
        if (removed != null) {
            AgentMain.unregisterTransformer(removed);
            dirty = true;
        }
    }

    /**
     * Unregisters a transformer by reference.
     */
    public void unregister(ITransformer transformer) {
        transformers.values().removeIf(t -> t == transformer);
        AgentMain.unregisterTransformer(transformer);
        dirty = true;
    }

    /**
     * Retrieves a transformer by name.
     */
    public ITransformer get(String name) {
        return transformers.get(name);
    }

    /**
     * Returns all registered transformers, sorted by priority (highest first).
     * The returned list is a defensive copy.
     */
    public List<ITransformer> getAll() {
        if (dirty) {
            synchronized (sortedTransformers) {
                sortedTransformers.clear();
                sortedTransformers.addAll(transformers.values());
                sortedTransformers.sort(Comparator.comparingInt(ITransformer::getPriority).reversed());
                dirty = false;
            }
        }
        return new ArrayList<>(sortedTransformers);
    }

    /**
     * Unregisters all transformers and clears the registry.
     */
    public void clear() {
        for (ITransformer transformer : transformers.values()) {
            AgentMain.unregisterTransformer(transformer);
        }
        transformers.clear();
        sortedTransformers.clear();
        dirty = true;
    }

    /**
     * Returns the number of registered transformers.
     */
    public int size() {
        return transformers.size();
    }

    /**
     * Checks whether a transformer with the given name is registered.
     */
    public boolean contains(String name) {
        return transformers.containsKey(name);
    }
}

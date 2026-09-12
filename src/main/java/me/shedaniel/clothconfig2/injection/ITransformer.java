package me.shedaniel.clothconfig2.injection;

/**
 * Interface for class bytecode transformers that can be registered
 * with the agent's master transformer pipeline.
 *
 * Implementations define which classes they target and how to
 * modify the raw class bytes before the JVM loads them.
 */
public interface ITransformer {

    /**
     * Determines whether this transformer should process the given class.
     *
     * @param className the internal name of the class (e.g. "net/minecraft/client/MinecraftClient")
     * @return true if {@link #transform} should be called for this class
     */
    boolean shouldTransform(String className);

    /**
     * Transforms the raw bytecode of a class.
     *
     * @param className  the internal name of the class being transformed
     * @param classBytes the original (or previously-transformed) bytecode
     * @return the modified bytecode, or null to leave unchanged
     */
    byte[] transform(String className, byte[] classBytes);

    /**
     * Priority for ordering transformers. Higher priority runs first.
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Human-readable name for this transformer, used as a registry key.
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}

package me.shedaniel.clothconfig2.impl.builders;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FriendListFieldBuilder extends AbstractFieldBuilder {
    private final Supplier<List<String>> namesSupplier;
    private final Consumer<String> onRemove;

    public FriendListFieldBuilder(String name, Supplier<List<String>> namesSupplier, Consumer<String> onRemove) {
        super(name, "");
        this.namesSupplier = namesSupplier;
        this.onRemove = onRemove;
    }

    public List<String> getNames() {
        if (namesSupplier == null) {
            return Collections.emptyList();
        }
        List<String> names = namesSupplier.get();
        return names != null ? names : Collections.emptyList();
    }

    public void remove(String name) {
        if (onRemove != null && name != null) {
            onRemove.accept(name);
        }
    }
}

package me.shedaniel.clothconfig2.impl.builders;

public class BooleanToggleBuilder extends AbstractFieldBuilder {
    private boolean value;

    public BooleanToggleBuilder(String name, String description, boolean defaultValue) {
        super(name, description);
        this.value = defaultValue;
    }

    public boolean get() { return value; }
    public void set(boolean v) { this.value = v; }
    public void toggle() { this.value = !this.value; }
}

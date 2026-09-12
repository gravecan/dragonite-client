package me.shedaniel.clothconfig2.impl.builders;

public abstract class AbstractFieldBuilder {
    private String name;
    private String description;
    private java.util.function.BooleanSupplier visibleWhen;

    protected AbstractFieldBuilder(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public void setVisibleWhen(java.util.function.BooleanSupplier pred) { this.visibleWhen = pred; }
    public boolean isVisible() {
        return visibleWhen == null || visibleWhen.getAsBoolean();
    }
    public void clearStrings() { name = null; description = null; }
}

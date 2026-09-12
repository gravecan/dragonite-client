package me.shedaniel.clothconfig2.impl.builders;

public class StringFieldBuilder extends AbstractFieldBuilder {
    private String value;
    private final String defaultValue;
    private final int maxLength;

    public StringFieldBuilder(String name, String description, String defaultValue, int maxLength) {
        super(name, description);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
        this.maxLength = maxLength;
    }

    public String get() { return value; }
    public void set(String val) { 
        if (val != null && val.length() <= maxLength) {
            this.value = val; 
        }
    }
    public String getDefault() { return defaultValue; }
    public int getMaxLength() { return maxLength; }
}

package me.shedaniel.clothconfig2.impl.builders;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class EnumSelectorBuilder extends AbstractFieldBuilder {
    private String value;
    private List<String> modes;

    public EnumSelectorBuilder(String name, String description, String def, String... modes) {
        super(name, description);
        this.modes = Arrays.asList(modes);
        this.value = (def != null && modes.length > 0) ? def : (modes.length > 0 ? modes[0] : "");
    }

    @Override
    public void clearStrings() {
        super.clearStrings();
        value = null;
        modes = Collections.emptyList();
    }

    public String get() { return value; }
    public void set(String v) { if (modes.contains(v)) value = v; }
    public List<String> getModes() { return modes; }
    public void cycle() {
        if (modes.isEmpty()) return;
        int i = modes.indexOf(value);
        value = modes.get((i + 1) % modes.size());
    }
}

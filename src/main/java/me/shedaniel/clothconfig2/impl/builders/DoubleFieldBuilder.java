package me.shedaniel.clothconfig2.impl.builders;

public class DoubleFieldBuilder extends AbstractFieldBuilder {
    private double value;
    private final double min, max, increment;
    private String suffix = "";

    public DoubleFieldBuilder(String name, String description, double def, double min, double max, double inc) {
        super(name, description);
        this.value = def;
        this.min = min;
        this.max = max;
        this.increment = inc;
    }

    public double get() { return value; }
    public void set(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) { value = min; return; }
        value = Math.max(min, Math.min(max, v));
        if (increment >= 1.0) {
            value = Math.round(value);
        } else if (increment > 0) {
            value = Math.round(value / increment) * increment;
            int scale = increment >= 1.0 ? 0 : (increment >= 0.1 ? 1 : 2);
            double mul = Math.pow(10, scale);
            value = Math.round(value * mul) / mul;
        }
    }
    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getIncrement() { return increment; }
    public void setSuffix(String s) { this.suffix = s; }
    public String getSuffix() { return suffix; }
}

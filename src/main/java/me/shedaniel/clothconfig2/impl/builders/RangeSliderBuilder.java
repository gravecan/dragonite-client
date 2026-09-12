package me.shedaniel.clothconfig2.impl.builders;


public class RangeSliderBuilder extends AbstractFieldBuilder {
    private double minVal;
    private double maxVal;
    private final double min, max, increment;
    private String suffix = "ms";

    public RangeSliderBuilder(String name, String description, double minDefault, double maxDefault, double min, double max, double inc) {
        super(name, description);
        this.minVal = minDefault;
        this.maxVal = maxDefault;
        this.min = min;
        this.max = max;
        this.increment = inc;
    }

    public double getMinVal() { return minVal; }
    public double getMaxVal() { return maxVal; }
    
    public void setMinVal(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) { minVal = min; return; }
        minVal = Math.max(min, Math.min(maxVal, v)); 
        if (increment == 1.0) minVal = Math.round(minVal);
    }
    
    public void setMaxVal(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) { maxVal = max; return; }
        maxVal = Math.max(minVal, Math.min(max, v)); 
        if (increment == 1.0) maxVal = Math.round(maxVal);
    }
    
    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getIncrement() { return increment; }
    public String getSuffix() { return suffix; }
    public void setSuffix(String s) { this.suffix = s; }
}

package dev.utilclient.module;

/** Left click = +step, right click = -step. */
public final class NumberSetting extends Setting {
    private double value;
    private final double min, max, step;

    public NumberSetting(String name, double def, double min, double max, double step, Runnable onChange) {
        super(name, onChange);
        this.value = def;
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public double get() { return value; }
    public int getInt() { return (int) Math.round(value); }

    @Override
    public void click(boolean left) {
        value += left ? step : -step;
        value = Math.round(value / step) * step;
        value = Math.max(min, Math.min(max, value));
        changed();
    }

    @Override
    public String valueText() {
        return step >= 1 ? Integer.toString(getInt()) : String.format("%.1f", value);
    }
}

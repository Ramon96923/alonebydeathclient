package dev.utilclient.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;
import java.util.function.BooleanSupplier;

/** Slider. Left click = +step, right click = -step, dragging in the GUI sets it directly. */
public final class NumberSetting extends Setting {
    private final double def;
    private double value;
    public final double min, max, step;
    private String unit = "";

    public NumberSetting(String name, double def, double min, double max, double step, Runnable onChange) {
        super(name, onChange);
        this.min = min;
        this.max = max;
        this.step = step;
        this.def = clampSnap(def);
        this.value = this.def;
    }

    public NumberSetting desc(String d) { description = d; return this; }

    public NumberSetting visible(BooleanSupplier v) { setVisible(v); return this; }

    public NumberSetting unit(String u) { unit = u; return this; }

    public double get() { return value; }
    public float getFloat() { return (float) value; }
    public int getInt() { return (int) Math.round(value); }

    private double clampSnap(double v) {
        if (step > 0) v = Math.round(v / step) * step;
        return Math.max(min, Math.min(max, v));
    }

    public void set(double v) {
        v = clampSnap(v);
        if (v == value) return;
        value = v;
        changed();
    }

    public double fraction() { return max == min ? 0 : (value - min) / (max - min); }

    public void setFraction(double f) { set(min + Math.max(0.0, Math.min(1.0, f)) * (max - min)); }

    @Override public void click(boolean left) { set(value + (left ? step : -step)); }

    @Override
    public String valueText() {
        String s;
        if (step >= 1) s = Integer.toString(getInt());
        else if (step >= 0.1) s = String.format(Locale.ROOT, "%.1f", value);
        else s = String.format(Locale.ROOT, "%.2f", value);
        return s + unit;
    }

    @Override public JsonElement save() { return new JsonPrimitive(value); }

    @Override
    public void load(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) value = clampSnap(json.getAsDouble());
    }

    @Override public void reset() { set(def); }

    @Override
    public boolean parse(String text) {
        try {
            set(Double.parseDouble(text));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

package dev.utilclient.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.function.BooleanSupplier;

public final class BoolSetting extends Setting {
    private final boolean def;
    private boolean value;

    public BoolSetting(String name, boolean def, Runnable onChange) {
        super(name, onChange);
        this.def = def;
        this.value = def;
    }

    public BoolSetting desc(String d) { description = d; return this; }

    public BoolSetting visible(BooleanSupplier v) { setVisible(v); return this; }

    public boolean get() { return value; }

    public void set(boolean v) {
        if (v == value) return;
        value = v;
        changed();
    }

    public void toggle() { set(!value); }

    @Override public void click(boolean left) { toggle(); }

    @Override public String valueText() { return value ? "On" : "Off"; }

    @Override public JsonElement save() { return new JsonPrimitive(value); }

    @Override
    public void load(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) value = json.getAsBoolean();
    }

    @Override public void reset() { set(def); }

    @Override
    public boolean parse(String text) {
        switch (text.toLowerCase()) {
            case "on", "true", "yes", "1" -> set(true);
            case "off", "false", "no", "0" -> set(false);
            case "toggle" -> toggle();
            default -> { return false; }
        }
        return true;
    }
}

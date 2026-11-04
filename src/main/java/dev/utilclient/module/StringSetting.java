package dev.utilclient.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.function.BooleanSupplier;

/** A line of text, e.g. a command to run. Click it in the GUI to type; Enter saves, Esc cancels. */
public final class StringSetting extends Setting {
    private final String def;
    private String value;

    public StringSetting(String name, String def, Runnable onChange) {
        super(name, onChange);
        this.def = def;
        this.value = def;
    }

    public StringSetting desc(String d) { description = d; return this; }

    public StringSetting visible(BooleanSupplier v) { setVisible(v); return this; }

    public String get() { return value; }

    public void set(String v) {
        v = v == null ? "" : v.strip();
        if (v.length() > 128) v = v.substring(0, 128);
        if (v.equals(value)) return;
        value = v;
        changed();
    }

    @Override public void click(boolean left) { }   // editing is handled by the click GUI

    @Override public String valueText() { return value.isEmpty() ? "(empty)" : value; }

    @Override public JsonElement save() { return new JsonPrimitive(value); }

    @Override
    public void load(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) value = json.getAsString();
    }

    @Override public void reset() { set(def); }

    @Override public boolean parse(String text) { set(text); return true; }
}

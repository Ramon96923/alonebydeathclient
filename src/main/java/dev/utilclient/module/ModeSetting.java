package dev.utilclient.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.function.BooleanSupplier;

/** One of several named choices. Left click = next, right click = previous. */
public final class ModeSetting extends Setting {
    public final List<String> modes;
    private final int def;
    private int index;

    public ModeSetting(String name, String def, Runnable onChange, String... modes) {
        super(name, onChange);
        this.modes = List.of(modes);
        int d = this.modes.indexOf(def);
        this.def = Math.max(0, d);
        this.index = this.def;
    }

    public ModeSetting desc(String d) { description = d; return this; }

    public ModeSetting visible(BooleanSupplier v) { setVisible(v); return this; }

    public String get() { return modes.get(index); }

    public boolean is(String mode) { return get().equalsIgnoreCase(mode); }

    public int index() { return index; }

    public void setIndex(int i) {
        i = Math.floorMod(i, modes.size());
        if (i == index) return;
        index = i;
        changed();
    }

    public boolean set(String mode) {
        for (int i = 0; i < modes.size(); i++) {
            if (modes.get(i).equalsIgnoreCase(mode)) { setIndex(i); return true; }
        }
        return false;
    }

    @Override public void click(boolean left) { setIndex(index + (left ? 1 : -1)); }

    @Override public String valueText() { return get(); }

    @Override public JsonElement save() { return new JsonPrimitive(get()); }

    @Override
    public void load(JsonElement json) {
        if (json == null || !json.isJsonPrimitive()) return;
        String m = json.getAsString();
        for (int i = 0; i < modes.size(); i++) if (modes.get(i).equalsIgnoreCase(m)) index = i;
    }

    @Override public void reset() { setIndex(def); }

    @Override public boolean parse(String text) { return set(text); }
}

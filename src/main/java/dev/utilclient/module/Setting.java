package dev.utilclient.module;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;

/**
 * Base class for every option shown under a module in the click GUI.
 * Settings know how to save themselves to JSON, reset, and whether they are currently visible
 * (e.g. "Smooth" only shows while the aimbot uses smoothing).
 */
public abstract class Setting {
    public final String name;
    public String description = "";
    private BooleanSupplier visible = () -> true;
    private final Runnable onChange;

    protected Setting(String name, Runnable onChange) {
        this.name = name;
        this.onChange = onChange;
    }

    protected void changed() {
        if (onChange != null) onChange.run();
        ModuleManager.markDirty();
    }

    public boolean isVisible() {
        try {
            return visible.getAsBoolean();
        } catch (RuntimeException e) {
            return true;
        }
    }

    protected void setVisible(BooleanSupplier v) { this.visible = v == null ? () -> true : v; }

    /** Left click = true, right click = false. Used for quick cycling. */
    public abstract void click(boolean left);

    public abstract String valueText();

    public abstract JsonElement save();

    public abstract void load(JsonElement json);

    public abstract void reset();

    /** Sets the value from a command argument; returns false if it could not be parsed. */
    public abstract boolean parse(String text);
}

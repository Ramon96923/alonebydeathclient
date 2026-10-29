package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    public final String name;
    public final String description;
    public final Category category;
    public final List<Setting> settings = new ArrayList<>();
    private boolean enabled;

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    protected BoolSetting add(String name, boolean def) { return add(name, def, null); }

    protected BoolSetting add(String name, boolean def, Runnable onChange) {
        BoolSetting s = new BoolSetting(name, def, onChange);
        settings.add(s);
        return s;
    }

    protected NumberSetting num(String name, double def, double min, double max, double step) {
        NumberSetting s = new NumberSetting(name, def, min, max, step, null);
        settings.add(s);
        return s;
    }

    protected ColorSetting color(String name, int presetIndex) {
        ColorSetting s = new ColorSetting(name, presetIndex, null);
        settings.add(s);
        return s;
    }

    public boolean isEnabled() { return enabled; }

    public void toggle() { setEnabled(!enabled); }

    public void setEnabled(boolean e) {
        if (e == enabled) return;
        enabled = e;
        if (e) onEnable(); else onDisable();
    }

    protected void onEnable() {}
    protected void onDisable() {}

    /** Called every client tick while enabled and a world is loaded. */
    public void onTick(Minecraft mc) {}

    /** Called every frame from the HUD layer while enabled (2D overlay drawing). */
    public void renderHud(GuiGraphicsExtractor g, float partialTick) {}
}

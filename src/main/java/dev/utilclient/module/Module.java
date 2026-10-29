package dev.utilclient.module;

import com.google.gson.JsonObject;
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
    /** Key code from InputConstants, or -1 when unbound. */
    public int key = -1;
    /** Enabled the first time the client runs (before any config exists). */
    protected boolean enabledByDefault;
    /** Settings holders (ClickGUI) are always "on" and can't be toggled. */
    protected boolean alwaysOn;
    /** "Toggle" or "Hold" for a fresh install. */
    protected String defaultBindMode = "Toggle";

    // added by ModuleManager after construction so they appear last in the GUI
    public BoolSetting drawn;
    public ModeSetting bindMode;

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    void addMeta() {
        drawn = add(new BoolSetting("Show in list", !alwaysOn, null)
                .desc("Show this module in the HUD module list"));
        bindMode = add(new ModeSetting("Bind mode", defaultBindMode, null, "Toggle", "Hold")
                .desc("Toggle: key turns it on/off. Hold: on only while the key is held"));
    }

    // ---- setting helpers -------------------------------------------------------------------

    protected <S extends Setting> S add(S s) {
        settings.add(s);
        return s;
    }

    protected BoolSetting bool(String name, boolean def) { return add(new BoolSetting(name, def, null)); }

    protected BoolSetting bool(String name, boolean def, Runnable onChange) {
        return add(new BoolSetting(name, def, onChange));
    }

    protected NumberSetting num(String name, double def, double min, double max, double step) {
        return add(new NumberSetting(name, def, min, max, step, null));
    }

    protected ModeSetting mode(String name, String def, String... modes) {
        return add(new ModeSetting(name, def, null, modes));
    }

    protected ColorSetting color(String name, int argb) { return add(new ColorSetting(name, argb, null)); }

    public Setting setting(String name) {
        String n = name.replace(" ", "").toLowerCase();
        for (Setting s : settings) if (s.name.replace(" ", "").toLowerCase().equals(n)) return s;
        return null;
    }

    // ---- state ------------------------------------------------------------------------------

    public boolean isEnabled() { return enabled || alwaysOn; }

    public boolean isAlwaysOn() { return alwaysOn; }

    public boolean isEnabledByDefault() { return enabledByDefault; }

    public void toggle() { setEnabled(!enabled); }

    public void setEnabled(boolean e) { setEnabled(e, true); }

    public void setEnabled(boolean e, boolean notify) {
        if (alwaysOn || e == enabled) return;
        enabled = e;
        try {
            if (e) onEnable(); else onDisable();
        } catch (Throwable t) {
            ModuleManager.report(this, t);
        }
        ModuleManager.markDirty();
        if (notify) ModuleManager.notifyToggle(this);
    }

    /** Text after the name in the HUD module list (e.g. the current mode). */
    public String info() { return null; }

    protected void onEnable() {}
    protected void onDisable() {}

    /** Called every client tick while enabled and a world is loaded. */
    public void onTick(Minecraft mc) {}

    /** Called every frame on the HUD layer while enabled. */
    public void renderHud(GuiGraphicsExtractor g, float partialTick) {}

    /** Extra state to persist beyond settings (HUD positions etc.). */
    public JsonObject saveExtra() { return null; }

    public void loadExtra(JsonObject json) {}
}

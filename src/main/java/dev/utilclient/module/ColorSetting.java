package dev.utilclient.module;

/** Cycles through preset colours. Left click = next, right click = previous. */
public final class ColorSetting extends Setting {
    public record Preset(String name, int rgb) {}

    public static final Preset[] PRESETS = {
        new Preset("Red",    0xFF4D4D),
        new Preset("Orange", 0xFF9F43),
        new Preset("Yellow", 0xFFD84D),
        new Preset("Green",  0x57F287),
        new Preset("Cyan",   0x3DDCFF),
        new Preset("Blue",   0x4DA3FF),
        new Preset("Purple", 0x9B6BFF),
        new Preset("Pink",   0xFF6BD6),
        new Preset("White",  0xFFFFFF),
    };

    private int index;

    public ColorSetting(String name, int presetIndex, Runnable onChange) {
        super(name, onChange);
        this.index = Math.floorMod(presetIndex, PRESETS.length);
    }

    public int rgb() { return PRESETS[index].rgb(); }

    @Override
    public void click(int button) {
        if (button == 0) index = (index + 1) % PRESETS.length;
        else if (button == 1) index = Math.floorMod(index - 1, PRESETS.length);
        else return;
        changed();
    }

    @Override public String valueText() { return PRESETS[index].name(); }
}

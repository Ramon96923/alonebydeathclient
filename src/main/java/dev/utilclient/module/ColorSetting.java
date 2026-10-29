package dev.utilclient.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.utilclient.util.Draw;

import java.util.function.BooleanSupplier;

/** Full HSV + alpha colour with an optional rainbow cycle. Edited with the picker in the click GUI. */
public final class ColorSetting extends Setting {
    private final int def;
    private float hue, sat, bri, alpha;
    private boolean rainbow;

    public ColorSetting(String name, int argb, Runnable onChange) {
        super(name, onChange);
        if ((argb >>> 24) == 0) argb |= 0xFF000000;
        this.def = argb;
        setArgbSilently(argb);
    }

    public ColorSetting desc(String d) { description = d; return this; }

    public ColorSetting visible(BooleanSupplier v) { setVisible(v); return this; }

    private void setArgbSilently(int argb) {
        float[] hsv = Draw.rgbToHsv(argb);
        hue = hsv[0];
        sat = hsv[1];
        bri = hsv[2];
        alpha = ((argb >>> 24) & 0xFF) / 255f;
    }

    public float hue() { return hue; }
    public float sat() { return sat; }
    public float bri() { return bri; }
    public float alpha() { return alpha; }
    public boolean rainbow() { return rainbow; }

    public void setHsv(float h, float s, float v) {
        hue = clamp01(h);
        sat = clamp01(s);
        bri = clamp01(v);
        changed();
    }

    public void setAlpha(float a) { alpha = clamp01(a); changed(); }

    public void setRainbow(boolean r) { rainbow = r; changed(); }

    private static float clamp01(float f) { return Math.max(0f, Math.min(1f, f)); }

    /** ARGB with rainbow applied. {@code offset} shifts the rainbow phase (for gradients). */
    public int get(float offset) {
        float h = hue;
        if (rainbow) h = (float) ((System.currentTimeMillis() % 6000L) / 6000.0) + offset;
        int rgb = Draw.hsv(h, sat, bri) & 0xFFFFFF;
        return ((int) (alpha * 255f) << 24) | rgb;
    }

    public int get() { return get(0f); }

    /** Opaque 0xRRGGBB. */
    public int rgb() { return get() & 0xFFFFFF; }

    @Override public void click(boolean left) { setRainbow(!rainbow); }

    @Override public String valueText() { return rainbow ? "Rainbow" : String.format("#%06X", rgb()); }

    @Override
    public JsonElement save() {
        JsonObject o = new JsonObject();
        o.addProperty("h", hue);
        o.addProperty("s", sat);
        o.addProperty("v", bri);
        o.addProperty("a", alpha);
        o.addProperty("rainbow", rainbow);
        return o;
    }

    @Override
    public void load(JsonElement json) {
        if (json == null || !json.isJsonObject()) return;
        JsonObject o = json.getAsJsonObject();
        if (o.has("h")) hue = o.get("h").getAsFloat();
        if (o.has("s")) sat = o.get("s").getAsFloat();
        if (o.has("v")) bri = o.get("v").getAsFloat();
        if (o.has("a")) alpha = o.get("a").getAsFloat();
        if (o.has("rainbow")) rainbow = o.get("rainbow").getAsBoolean();
    }

    @Override
    public void reset() {
        setArgbSilently(def);
        rainbow = false;
        changed();
    }

    @Override
    public boolean parse(String text) {
        if (text.equalsIgnoreCase("rainbow")) { setRainbow(true); return true; }
        String t = text.startsWith("#") ? text.substring(1) : text;
        try {
            long v = Long.parseLong(t, 16);
            int argb = t.length() > 6 ? (int) v : (0xFF000000 | (int) v);
            setArgbSilently(argb);
            rainbow = false;
            changed();
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

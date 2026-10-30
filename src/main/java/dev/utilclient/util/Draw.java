package dev.utilclient.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

/** 2D drawing helpers built only on fill() and the pose matrix. */
public final class Draw {
    private Draw() {}

    public static void rect(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        if (x1 > x0 && y1 > y0) g.fill(x0, y0, x1, y1, color);
    }

    /** Rectangle with its four corner pixels removed: reads as softly rounded at GUI scale. */
    public static void soft(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        if (x1 - x0 < 3 || y1 - y0 < 3) { rect(g, x0, y0, x1, y1, color); return; }
        g.fill(x0 + 1, y0, x1 - 1, y1, color);
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
    }

    /** Rounded rectangle. {@code top}/{@code bottom} choose which corners get rounded. */
    public static void round(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int r, int color, boolean top, boolean bottom) {
        r = Math.min(r, Math.min((x1 - x0) / 2, (y1 - y0) / 2));
        if (r <= 0) { rect(g, x0, y0, x1, y1, color); return; }
        int midTop = y0 + (top ? r : 0), midBottom = y1 - (bottom ? r : 0);
        if (midBottom > midTop) g.fill(x0, midTop, x1, midBottom, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            if (top) g.fill(x0 + inset, y0 + i, x1 - inset, y0 + i + 1, color);
            if (bottom) g.fill(x0 + inset, y1 - 1 - i, x1 - inset, y1 - i, color);
        }
    }

    public static void round(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int r, int color) {
        round(g, x0, y0, x1, y1, r, color, true, true);
    }

    /** Fully rounded ends (switches, buttons). */
    public static void pill(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        round(g, x0, y0, x1, y1, (y1 - y0) / 2, color, true, true);
    }

    public static void outline(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
    }

    /** One rotated quad per line, so long tracers stay cheap. */
    public static void line(GuiGraphicsExtractor g, double x0, double y0, double x1, double y1, float width, int color) {
        double dx = x1 - x0, dy = y1 - y0;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5) return;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate((float) x0, (float) y0);
        pose.rotate((float) Math.atan2(dy, dx));
        pose.scale(1f, width);
        g.fill(0, 0, (int) Math.ceil(len), 1, color);
        pose.popMatrix();
    }

    public static void line(GuiGraphicsExtractor g, double x0, double y0, double x1, double y1, int color) {
        line(g, x0, y0, x1, y1, 1f, color);
    }

    /** Horizontal gradient drawn in 2px columns. */
    public static void gradientH(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int left, int right) {
        int w = x1 - x0;
        if (w <= 0) return;
        for (int x = 0; x < w; x += 2) {
            g.fill(x0 + x, y0, Math.min(x1, x0 + x + 2), y1, mix(left, right, x / (float) w));
        }
    }

    /** Vertical gradient drawn in 1px rows. */
    public static void gradientV(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int top, int bottom) {
        int h = y1 - y0;
        if (h <= 0) return;
        for (int y = 0; y < h; y++) g.fill(x0, y0 + y, x1, y0 + y + 1, mix(top, bottom, y / (float) h));
    }

    public static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    public static int withAlpha(int color, float alpha) {
        int a = (int) (Math.max(0f, Math.min(1f, alpha)) * ((color >>> 24) & 0xFF));
        return (a << 24) | (color & 0xFFFFFF);
    }

    public static int alpha(int rgb, int a) { return (a << 24) | (rgb & 0xFFFFFF); }

    public static int mix(int c0, int c1, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((c0 >>> 24) & 0xFF) + ((((c1 >>> 24) & 0xFF) - ((c0 >>> 24) & 0xFF)) * t));
        int r = (int) (((c0 >> 16) & 0xFF) + ((((c1 >> 16) & 0xFF) - ((c0 >> 16) & 0xFF)) * t));
        int gg = (int) (((c0 >> 8) & 0xFF) + ((((c1 >> 8) & 0xFF) - ((c0 >> 8) & 0xFF)) * t));
        int b = (int) ((c0 & 0xFF) + (((c1 & 0xFF) - (c0 & 0xFF)) * t));
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    public static int hsv(float h, float s, float v) {
        float r, g, b;
        float hh = (h - (float) Math.floor(h)) * 6f;
        int i = (int) hh;
        float f = hh - i, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    public static float[] rgbToHsv(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min, h = 0f;
        if (d > 0) {
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h /= 6f;
            if (h < 0) h += 1f;
        }
        return new float[] {h, max == 0 ? 0 : d / max, max};
    }

    /** Red (low) to green (high) for health bars. */
    public static int healthColor(float fraction) {
        return hsv(Math.max(0f, Math.min(1f, fraction)) * 0.33f, 0.8f, 0.95f);
    }
}

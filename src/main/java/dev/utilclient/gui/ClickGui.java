package dev.utilclient.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.utilclient.UtilClient;
import dev.utilclient.core.Config;
import dev.utilclient.core.Keys;
import dev.utilclient.module.BoolSetting;
import dev.utilclient.module.Category;
import dev.utilclient.module.ClickGuiModule;
import dev.utilclient.module.ColorSetting;
import dev.utilclient.module.HudModule;
import dev.utilclient.module.ModeSetting;
import dev.utilclient.module.Module;
import dev.utilclient.module.ModuleManager;
import dev.utilclient.module.NumberSetting;
import dev.utilclient.module.Setting;
import dev.utilclient.util.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Kami / Lambda style click GUI.
 * <ul>
 *   <li>Drag a panel header to move it, right-click it to fold it, scroll inside a panel.</li>
 *   <li>Left-click a module to toggle it, right-click to open its settings, middle-click to bind it.</li>
 *   <li>Type anywhere to search. Esc clears the search, then closes.</li>
 *   <li>"HUD Editor" (top right) lets you drag HUD elements around.</li>
 * </ul>
 */
public class ClickGui extends Screen {
    // ---- layout constants ----------------------------------------------------------------------
    private static final int HEADER_H = 15, MODULE_H = 13, ROW_H = 12, SLIDER_H = 16, PICKER_H = 62;

    // ---- colours -------------------------------------------------------------------------------
    private static final int PANEL_BG = 0x0E0E14, TEXT = 0xFFEDEDF4, DIM = 0xFF8C8CA0, MUTED = 0xFF5C5C70;
    private static final int TRACK = 0xFF2B2B38, HOVER = 0x16FFFFFF;

    // ---- persistent state (survives closing the GUI, saved to config) ------------------------------
    private static final List<Panel> PANELS = new ArrayList<>();
    private static final Set<String> EXPANDED = new HashSet<>();
    private static final Set<ColorSetting> PICKERS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean hudEditor;

    private static final class Panel {
        final Category cat;
        float x, y, scroll, scrollTarget, openAnim = 1f;
        boolean open = true, placed;
        int bx0, by0, bx1, by1;          // last drawn bounds
        int contentH;

        Panel(Category cat) { this.cat = cat; }
    }

    private enum Type { HEADER, BODY, MODULE, SETTING, SLIDER, BIND, PICK_SV, PICK_HUE, PICK_ALPHA, PICK_RAINBOW,
        HUD_BUTTON, SEARCH, ELEMENT, ELEMENT_TOGGLE, HUD_ENABLE }

    private record Hit(int x0, int y0, int x1, int y1, Type type, Object a, Panel panel) {
        boolean contains(double mx, double my) { return mx >= x0 && mx < x1 && my >= y0 && my < y1; }
    }

    static {
        for (Category c : Category.values()) PANELS.add(new Panel(c));
    }

    // ---- per-screen state ----------------------------------------------------------------------
    private String search = "";
    private final Map<Object, Float> anims = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private long lastNanos;
    private float dt, openAnim;
    private Panel hoverPanel;
    private String hoverDescription;

    private Panel dragPanel;
    private float dragDX, dragDY;
    private NumberSetting dragSlider;
    private int sliderX, sliderW;
    private ColorSetting dragColor;
    private Type dragColorPart;
    private int pickX, pickY, pickW, pickH;
    private HudModule.Element dragElement;
    private int elemDX, elemDY;
    private Module listening;

    private int clipTop, clipBottom;
    private int panelIndex;

    private final Screen parent;
    /** GUI scale and the screen size in scaled units. */
    private float s = 1f;
    private int vw, vh;

    public ClickGui() { this(null); }

    /** @param parent screen to return to (e.g. the title screen); null to close into the game */
    public ClickGui(Screen parent) {
        super(Component.literal("AloneByDeath"));
        this.parent = parent;
    }

    private boolean inWorld() { return minecraft != null && minecraft.level != null; }

    private void updateScale() {
        s = hudEditor ? 1f : (float) theme().scale.get();
        vw = (int) (width / s);
        vh = (int) (height / s);
    }

    private int r() { return theme().radius.getInt(); }

    private static ClickGuiModule theme() { return ModuleManager.CLICK_GUI; }

    @Override
    public boolean isPauseScreen() { return theme().pauseGame.get(); }

    @Override
    protected void init() {
        super.init();
        Fonts.verify();
        if (!inWorld()) hudEditor = false;
        updateScale();
        int x = 10, y = 26, rowH = 0;
        int pw = theme().panelWidth.getInt();
        for (Panel p : PANELS) {
            if (!p.placed) {
                if (x + pw > vw - 4) { x = 10; y += Math.max(150, rowH); }
                p.x = x;
                p.y = y;
                p.placed = true;
                x += pw + 8;
            }
            p.x = Math.max(0, Math.min(p.x, vw - 20));
            p.y = Math.max(0, Math.min(p.y, vh - HEADER_H));
        }
    }

    // ---- saving --------------------------------------------------------------------------------

    public static JsonObject saveState() {
        JsonObject o = new JsonObject();
        JsonArray panels = new JsonArray();
        for (Panel p : PANELS) {
            JsonObject j = new JsonObject();
            j.addProperty("cat", p.cat.name());
            j.addProperty("x", p.x);
            j.addProperty("y", p.y);
            j.addProperty("open", p.open);
            j.addProperty("placed", p.placed);
            panels.add(j);
        }
        o.add("panels", panels);
        JsonArray exp = new JsonArray();
        for (String s : EXPANDED) exp.add(s);
        o.add("expanded", exp);
        return o;
    }

    public static void loadState(JsonObject o) {
        if (o.has("panels")) {
            for (JsonElement el : o.getAsJsonArray("panels")) {
                JsonObject j = el.getAsJsonObject();
                for (Panel p : PANELS) {
                    if (!p.cat.name().equals(j.get("cat").getAsString())) continue;
                    p.x = j.get("x").getAsFloat();
                    p.y = j.get("y").getAsFloat();
                    p.open = j.get("open").getAsBoolean();
                    p.openAnim = p.open ? 1f : 0f;
                    p.placed = !j.has("placed") || j.get("placed").getAsBoolean();
                }
            }
        }
        if (o.has("expanded")) {
            EXPANDED.clear();
            for (JsonElement el : o.getAsJsonArray("expanded")) EXPANDED.add(el.getAsString());
        }
    }

    // ---- animation helpers -----------------------------------------------------------------------

    private float speed() { return (float) theme().animSpeed.get(); }

    private float approach(Object key, float target, float rate) {
        float v = anims.getOrDefault(key, target);
        if (speed() <= 0f) v = target;
        else {
            float step = dt * rate * speed();
            v = v < target ? Math.min(target, v + step) : Math.max(target, v - step);
        }
        anims.put(key, v);
        return v;
    }

    private static float ease(float t) { return 1f - (1f - t) * (1f - t) * (1f - t); }

    private int accent(float offset) { return theme().accent(offset); }

    private int panelBg() { return Draw.alpha(PANEL_BG, theme().panelAlpha()); }

    // ---- rendering -------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (!inWorld()) {
            this.extractPanorama(g, delta);
            g.fill(0, 0, width, height, 0x66000000);
            return;
        }
        switch (theme().background.get()) {
            case "Blur" -> super.extractBackground(g, mouseX, mouseY, delta);
            case "Dim" -> g.fill(0, 0, width, height, Draw.alpha(0x05050A, (int) (110 * openAnim)));
            default -> { }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int rawX, int rawY, float delta) {
        super.extractRenderState(g, rawX, rawY, delta);
        updateScale();
        int mx = (int) (rawX / s), my = (int) (rawY / s);
        g.pose().pushMatrix();
        g.pose().scale(s, s);
        try {
            renderScaled(g, mx, my);
        } finally {
            g.pose().popMatrix();
        }
    }

    private void renderScaled(GuiGraphicsExtractor g, int mx, int my) {
        long now = System.nanoTime();
        dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        openAnim = speed() <= 0 ? 1f : Math.min(1f, openAnim + dt * 5f * speed());

        updateDrags(mx, my);
        hits.clear();
        hoverDescription = null;
        ModuleManager.HUD.editing = hudEditor;

        if (hudEditor) {
            renderHudEditor(g, mx, my);
        } else {
            hoverPanel = null;
            for (int i = PANELS.size() - 1; i >= 0; i--) {
                Panel p = PANELS.get(i);
                if (mx >= p.bx0 && mx < p.bx1 && my >= p.by0 && my < p.by1) { hoverPanel = p; break; }
            }
            panelIndex = 0;
            for (Panel p : PANELS) {
                g.nextStratum();
                renderPanel(g, p, mx, my);
                panelIndex++;
            }
        }

        g.nextStratum();
        renderTopBar(g, mx, my);
        if (theme().descriptions.get() && hoverDescription != null && !hoverDescription.isEmpty()) {
            renderDescription(g, hoverDescription);
        }
    }

    private void updateDrags(int mx, int my) {
        if (dragPanel != null) {
            dragPanel.x = Math.max(0, Math.min(mx - dragDX, vw - theme().panelWidth.getInt()));
            dragPanel.y = Math.max(0, Math.min(my - dragDY, vh - HEADER_H));
        }
        if (dragSlider != null) dragSlider.setFraction((mx - sliderX) / (double) Math.max(1, sliderW));
        if (dragColor != null) {
            float fx = Math.max(0f, Math.min(1f, (mx - pickX) / (float) Math.max(1, pickW)));
            float fy = Math.max(0f, Math.min(1f, (my - pickY) / (float) Math.max(1, pickH)));
            switch (dragColorPart) {
                case PICK_SV -> dragColor.setHsv(dragColor.hue(), fx, 1f - fy);
                case PICK_HUE -> dragColor.setHsv(fy, dragColor.sat(), dragColor.bri());
                case PICK_ALPHA -> dragColor.setAlpha(fx);
                default -> { }
            }
        }
        if (dragElement != null) dragElement.moveTo(mx - elemDX, my - elemDY, vw, vh);
    }

    private List<Module> modulesFor(Category c) {
        List<Module> out = new ArrayList<>();
        String q = search.toLowerCase(Locale.ROOT);
        for (Module m : ModuleManager.byCategory(c)) {
            if (q.isEmpty() || m.name.toLowerCase(Locale.ROOT).contains(q) || m.description.toLowerCase(Locale.ROOT).contains(q)) out.add(m);
        }
        return out;
    }

    private void renderPanel(GuiGraphicsExtractor g, Panel p, int mx, int my) {
        int pw = theme().panelWidth.getInt();
        int slideIn = (int) ((1f - ease(openAnim)) * -14f);
        int x = Math.round(p.x), y = Math.round(p.y) + slideIn;
        List<Module> mods = modulesFor(p.cat);
        boolean canHover = hoverPanel == p && dragPanel == null;
        int acc = accent(panelIndex * 0.08f);

        // header
        int enabled = 0;
        for (Module m : mods) if (m.isEnabled() && !m.isAlwaysOn()) enabled++;
        String title = p.cat.label;
        String count = p.cat == Category.CLIENT ? "" : enabled + "/" + mods.size();
        boolean folded = p.openAnim <= 0.001f || p.contentH <= 0;
        switch (theme().headerStyle.get()) {
            case "Filled" -> {
                Draw.round(g, x, y, x + pw, y + HEADER_H, r(), acc, true, folded);
                Fonts.text(g, title, x + 6, y + 3, 0xFFFFFFFF, true);
                Fonts.text(g, count, x + pw - 6 - Fonts.width(count), y + 3, 0xC8FFFFFF);
            }
            case "Underline" -> {
                Draw.round(g, x, y, x + pw, y + HEADER_H, r(), Draw.alpha(0x16161F, Math.min(255, theme().panelAlpha() + 20)), true, folded);
                int inset = folded ? r() : 0;
                g.fill(x + inset, y + HEADER_H - 1, x + pw - inset, y + HEADER_H, acc);
                Fonts.text(g, title, x + 6, y + 3, 0xFFFFFFFF, true);
                Fonts.text(g, count, x + pw - 6 - Fonts.width(count), y + 3, DIM);
            }
            default -> {
                Draw.round(g, x, y, x + pw, y + HEADER_H, r(), Draw.alpha(0x16161F, Math.min(255, theme().panelAlpha() + 20)), true, folded);
                Fonts.text(g, title, x + 6, y + 3, acc, true);
                Fonts.text(g, count, x + pw - 6 - Fonts.width(count), y + 3, DIM);
            }
        }
        hits.add(new Hit(x, y, x + pw, y + HEADER_H, Type.HEADER, null, p));

        // body
        float target = p.open ? 1f : 0f;
        p.openAnim = speed() <= 0 ? target : (p.openAnim < target
                ? Math.min(target, p.openAnim + dt * 6f * speed())
                : Math.max(target, p.openAnim - dt * 6f * speed()));
        int maxH = theme().maxHeight.getInt();
        int bodyH = (int) (Math.min(p.contentH, maxH) * ease(p.openAnim));
        int top = y + HEADER_H;
        int bottom = top + bodyH;
        int pad = Math.max(2, r() - 2);   // room for the rounded corners below the last row
        p.bx0 = x;
        p.by0 = y;
        p.bx1 = x + pw;
        p.by1 = bottom + pad;

        if (bodyH > 0) {
            Draw.round(g, x, top, x + pw, bottom + pad, r(), panelBg(), false, true);
            hits.add(new Hit(x, top, x + pw, bottom, Type.BODY, null, p));

            float maxScroll = Math.max(0, p.contentH - maxH);
            p.scrollTarget = Math.max(0, Math.min(p.scrollTarget, maxScroll));
            p.scroll += (p.scrollTarget - p.scroll) * Math.min(1f, dt * 14f);
            if (speed() <= 0f) p.scroll = p.scrollTarget;

            g.enableScissor(x, top, x + pw, bottom);
            clipTop = top;
            clipBottom = bottom;
            int cy = top - Math.round(p.scroll);
            int start = cy;
            if (mods.isEmpty()) {
                Fonts.text(g, "No matches", x + 6, cy + 3, MUTED);
                cy += MODULE_H;
            }
            for (int i = 0; i < mods.size(); i++) {
                Module m = mods.get(i);
                cy = renderModule(g, m, x, cy, pw, mx, my, canHover, i);
            }
            g.disableScissor();
            p.contentH = cy - start;

            if (p.contentH > maxH) {   // scrollbar
                float frac = maxH / (float) p.contentH;
                int barH = Math.max(12, (int) (bodyH * frac));
                int barY = top + (int) ((bodyH - barH) * (p.scroll / Math.max(1f, p.contentH - maxH)));
                g.fill(x + pw - 2, barY, x + pw - 1, barY + barH, Draw.withAlpha(acc, 0.7f));
            }
        } else {
            // keep measuring while folded so unfolding animates to the right height
            int h = 0;
            for (Module m : mods) h += MODULE_H + (EXPANDED.contains(m.name) ? settingsHeight(m) : 0);
            p.contentH = Math.max(MODULE_H, h);
        }
    }

    private boolean inClip(int y0, int y1) { return y1 > clipTop && y0 < clipBottom; }

    private int renderModule(GuiGraphicsExtractor g, Module m, int x, int y, int pw, int mx, int my, boolean canHover, int index) {
        int acc = accent(panelIndex * 0.08f + index * 0.03f);
        float on = approach(m, m.isEnabled() ? 1f : 0f, 7f);
        boolean hover = canHover && mx >= x && mx < x + pw && my >= y && my < y + MODULE_H && inClip(y, y + MODULE_H);

        if (on > 0f) {
            g.fill(x, y, x + (int) (pw * ease(on)), y + MODULE_H, Draw.withAlpha(acc, 0.22f * on + 0.06f));
            g.fill(x, y, x + 2, y + MODULE_H, Draw.withAlpha(acc, on));
        }
        if (hover) {
            g.fill(x, y, x + pw, y + MODULE_H, HOVER);
            hoverDescription = m.description;
        }
        int textColor = Draw.mix(DIM, 0xFFFFFFFF, on);
        Fonts.text(g, m.name, x + 7, y + 2, textColor);

        boolean expanded = EXPANDED.contains(m.name);
        String right = expanded ? "-" : "+";
        if (listening == m) right = "...";
        else if (theme().showBinds.get() && m.key >= 0) right = Keys.name(m.key) + "  " + right;
        Fonts.text(g, right, x + pw - 6 - Fonts.width(right), y + 2, listening == m ? acc : MUTED);

        if (inClip(y, y + MODULE_H)) hits.add(new Hit(x, y, x + pw, y + MODULE_H, Type.MODULE, m, null));
        y += MODULE_H;

        float exp = approach("exp:" + m.name, expanded ? 1f : 0f, 6f);
        if (exp > 0f) {
            int full = settingsHeight(m);
            int shown = (int) (full * ease(exp));
            int sTop = Math.max(y, clipTop), sBottom = Math.min(y + shown, clipBottom);
            if (shown > 0 && sBottom > sTop) {
                g.enableScissor(x, sTop, x + pw, sBottom);
                int oldBottom = clipBottom;
                clipBottom = Math.min(clipBottom, y + shown);
                g.fill(x + 3, y, x + 4, y + full - 2, Draw.withAlpha(acc, 0.6f));
                renderSettings(g, m, x, y, pw, mx, my, canHover);
                clipBottom = oldBottom;
                g.disableScissor();
            }
            y += shown;
        }
        return y;
    }

    private int settingHeight(Setting s) {
        if (s instanceof NumberSetting) return SLIDER_H;
        if (s instanceof ColorSetting c) {
            float a = anims.getOrDefault("pick:" + System.identityHashCode(c), PICKERS.contains(c) ? 1f : 0f);
            return ROW_H + (int) (PICKER_H * ease(a));
        }
        return ROW_H;
    }

    private int settingsHeight(Module m) {
        int h = ROW_H + 2;   // bind row + gap
        for (Setting s : m.settings) if (s.isVisible()) h += settingHeight(s);
        return h;
    }

    private void renderSettings(GuiGraphicsExtractor g, Module m, int x, int y, int pw, int mx, int my, boolean canHover) {
        int ix = x + 8;
        int iw = pw - 14;
        int acc = accent(panelIndex * 0.08f);
        for (Setting s : m.settings) {
            if (!s.isVisible()) continue;
            if (s instanceof ColorSetting c) {
                approach("pick:" + System.identityHashCode(c), PICKERS.contains(c) ? 1f : 0f, 6f);
            }
            int h = settingHeight(s);
            boolean hover = canHover && inClip(y, y + h) && mx >= x && mx < x + pw && my >= y && my < y + Math.min(h, s instanceof ColorSetting ? ROW_H : h);
            if (hover) {
                g.fill(x + 4, y, x + pw, y + Math.min(h, ROW_H + (s instanceof NumberSetting ? 5 : 0)), HOVER);
                if (!s.description.isEmpty()) hoverDescription = s.description;
            }
            if (s instanceof BoolSetting b) {
                float t = approach(b, b.get() ? 1f : 0f, 8f);
                Fonts.text(g, Fonts.fit(s.name, iw - 22), ix, y + 2, Draw.mix(DIM, TEXT, t));
                int sx = x + pw - 22, sy = y + 2;
                Draw.pill(g, sx, sy, sx + 16, sy + 8, Draw.mix(TRACK, acc, t));
                int kx = sx + 1 + (int) (8 * ease(t));
                Draw.pill(g, kx, sy + 1, kx + 6, sy + 7, 0xFFFFFFFF);
                if (inClip(y, y + h)) hits.add(new Hit(x, y, x + pw, y + h, Type.SETTING, s, null));
            } else if (s instanceof NumberSetting n) {
                String v = n.valueText();
                Fonts.text(g, Fonts.fit(s.name, iw - Fonts.width(v) - 6), ix, y + 2, TEXT);
                Fonts.text(g, v, x + pw - 6 - Fonts.width(v), y + 2, acc);
                int bx = ix, bw = x + pw - 6 - ix, by = y + 12;
                Draw.pill(g, bx, by, bx + bw, by + 2, TRACK);
                int fw = (int) (bw * n.fraction());
                if (fw > 1) Draw.pill(g, bx, by, bx + fw, by + 2, acc);
                Draw.pill(g, bx + fw - 2, by - 1, bx + fw + 2, by + 3, 0xFFFFFFFF);
                if (inClip(y, y + h)) hits.add(new Hit(x, y, x + pw, y + h, Type.SLIDER, new int[] {bx, bw}, null));
                if (inClip(y, y + h)) hits.add(new Hit(x, y, x + pw, y + h, Type.SETTING, s, null));
            } else if (s instanceof ModeSetting md) {
                String v = md.get();
                Fonts.text(g, Fonts.fit(s.name, iw - Fonts.width(v) - 6), ix, y + 2, TEXT);
                Fonts.text(g, v, x + pw - 6 - Fonts.width(v), y + 2, acc);
                if (inClip(y, y + h)) hits.add(new Hit(x, y, x + pw, y + ROW_H, Type.SETTING, s, null));
            } else if (s instanceof ColorSetting c) {
                Fonts.text(g, Fonts.fit(s.name, iw - 24), ix, y + 2, TEXT);
                int sx = x + pw - 18;
                Draw.round(g, sx - 1, y + 1, sx + 12, y + 11, 3, 0x80FFFFFF);
                Draw.round(g, sx, y + 2, sx + 11, y + 10, 3, c.get() | 0xFF000000);
                if (inClip(y, y + ROW_H)) hits.add(new Hit(x, y, x + pw, y + ROW_H, Type.SETTING, s, null));
                if (h > ROW_H) renderPicker(g, c, ix, y + ROW_H, x + pw - 6 - ix, h - ROW_H);
            }
            y += h;
        }
        // bind row
        boolean hover = canHover && inClip(y, y + ROW_H) && mx >= x && mx < x + pw && my >= y && my < y + ROW_H;
        if (hover) {
            g.fill(x + 4, y, x + pw, y + ROW_H, HOVER);
            hoverDescription = "Left-click, then press a key to bind. Right-click to unbind.";
        }
        String key = listening == m ? "Press a key..." : Keys.name(m.key);
        Fonts.text(g, "Bind", ix, y + 2, TEXT);
        Fonts.text(g, key, x + pw - 6 - Fonts.width(key), y + 2, listening == m ? acc : DIM);
        if (inClip(y, y + ROW_H)) hits.add(new Hit(x, y, x + pw, y + ROW_H, Type.BIND, m, null));
    }

    private void renderPicker(GuiGraphicsExtractor g, ColorSetting c, int x, int y, int w, int availH) {
        if (availH < 8) return;
        int svW = w - 12, svH = 44;
        int top = y + 2;
        // saturation / value square: hue base, white->clear horizontally, clear->black vertically
        g.fill(x, top, x + svW, top + svH, Draw.hsv(c.hue(), 1f, 1f));
        for (int i = 0; i < svW; i += 2) {
            int a = (int) (255 * (1f - i / (float) svW));
            g.fill(x + i, top, Math.min(x + svW, x + i + 2), top + svH, Draw.alpha(0xFFFFFF, a));
        }
        for (int j = 0; j < svH; j++) {
            int a = (int) (255 * (j / (float) svH));
            g.fill(x, top + j, x + svW, top + j + 1, Draw.alpha(0x000000, a));
        }
        int cx = x + (int) (c.sat() * svW), cy = top + (int) ((1f - c.bri()) * svH);
        Draw.outline(g, cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);
        if (inClip(top, top + svH)) hits.add(new Hit(x, top, x + svW, top + svH, Type.PICK_SV, c, null));

        // hue bar
        int hx = x + svW + 4;
        for (int j = 0; j < svH; j++) g.fill(hx, top + j, hx + 8, top + j + 1, Draw.hsv(j / (float) svH, 1f, 1f));
        int hy = top + (int) (c.hue() * svH);
        g.fill(hx - 1, hy, hx + 9, hy + 1, 0xFFFFFFFF);
        if (inClip(top, top + svH)) hits.add(new Hit(hx, top, hx + 8, top + svH, Type.PICK_HUE, c, null));

        // alpha bar with checkerboard
        int ay = top + svH + 3, aw = svW;
        for (int i = 0; i < aw; i += 3) {
            g.fill(x + i, ay, Math.min(x + aw, x + i + 3), ay + 3, ((i / 3) & 1) == 0 ? 0xFF9A9A9A : 0xFF5A5A5A);
            g.fill(x + i, ay + 3, Math.min(x + aw, x + i + 3), ay + 6, ((i / 3) & 1) == 0 ? 0xFF5A5A5A : 0xFF9A9A9A);
        }
        int rgb = c.get() & 0xFFFFFF;
        for (int i = 0; i < aw; i += 2) g.fill(x + i, ay, Math.min(x + aw, x + i + 2), ay + 6, Draw.alpha(rgb, (int) (255 * (i / (float) aw))));
        int axp = x + (int) (c.alpha() * aw);
        g.fill(axp, ay - 1, axp + 1, ay + 7, 0xFFFFFFFF);
        if (inClip(ay, ay + 6)) hits.add(new Hit(x, ay, x + aw, ay + 6, Type.PICK_ALPHA, c, null));

        // rainbow toggle
        int ry = ay + 8;
        String label = "Rainbow: " + (c.rainbow() ? "On" : "Off");
        Fonts.text(g, label, x, ry, c.rainbow() ? accent(0f) : DIM);
        if (inClip(ry, ry + 9)) hits.add(new Hit(x, ry, x + Fonts.width(label) + 4, ry + 9, Type.PICK_RAINBOW, c, null));
    }

    private void renderTopBar(GuiGraphicsExtractor g, int mx, int my) {
        int acc = accent(0f);
        // search box
        int sw = 170, sx = vw / 2 - sw / 2, sy = 5, sh = 15;
        Draw.round(g, sx, sy, sx + sw, sy + sh, r(), Draw.alpha(0x0E0E14, 225));
        Draw.round(g, sx + 3, sy + sh - 1, sx + sw - 3, sy + sh, 0, search.isEmpty() ? Draw.withAlpha(acc, 0.35f) : acc);
        if (search.isEmpty()) {
            Fonts.text(g, hudEditor ? "HUD editor: drag elements" : "Type to search...", sx + 6, sy + 4, MUTED);
        } else {
            boolean caret = (System.currentTimeMillis() / 500L) % 2 == 0;
            Fonts.text(g, search + (caret ? "_" : ""), sx + 6, sy + 4, TEXT);
            Fonts.text(g, "x", sx + sw - 10, sy + 4, DIM);
            hits.add(new Hit(sx + sw - 14, sy, sx + sw, sy + sh, Type.SEARCH, null, null));
        }

        // HUD editor button (needs a world to show the HUD)
        if (!inWorld()) return;
        String label = hudEditor ? "Back to modules" : "HUD Editor";
        int bw = Fonts.width(label) + 14, bx = vw - bw - 6, by = 5;
        boolean hover = mx >= bx && mx < bx + bw && my >= by && my < by + sh;
        Draw.round(g, bx, by, bx + bw, by + sh, r(), hudEditor ? acc : Draw.alpha(0x0E0E14, hover ? 245 : 215));
        Fonts.text(g, label, bx + 7, by + 4, 0xFFFFFFFF);
        hits.add(new Hit(bx, by, bx + bw, by + sh, Type.HUD_BUTTON, null, null));
        if (hover) hoverDescription = hudEditor ? "Return to the module panels" : "Move HUD elements around the screen";
    }

    private void renderDescription(GuiGraphicsExtractor g, String text) {
        String t = Fonts.fit(text, vw - 40);
        int w = Fonts.width(t) + 14, h = 15;
        int x = vw / 2 - w / 2, y = vh - h - 6;
        Draw.round(g, x, y, x + w, y + h, r(), Draw.alpha(0x0E0E14, 235));
        Draw.round(g, x + 4, y + h - 1, x + w - 4, y + h, 0, accent(0f));
        Fonts.text(g, t, x + 7, y + 4, TEXT);
    }

    private void renderHudEditor(GuiGraphicsExtractor g, int mx, int my) {
        HudModule hud = ModuleManager.HUD;
        int acc = accent(0f);
        // centre guides
        g.fill(vw / 2, 0, vw / 2 + 1, vh, 0x22FFFFFF);
        g.fill(0, vh / 2, vw, vh / 2 + 1, 0x22FFFFFF);

        if (!hud.isEnabled()) {
            String msg = "The HUD module is off - click here to turn it on";
            int w = Fonts.width(msg) + 16, x = vw / 2 - w / 2, y = vh / 2 - 30;
            Draw.round(g, x, y, x + w, y + 18, r(), Draw.alpha(0x0E0E14, 235));
            Fonts.text(g, msg, x + 8, y + 5, TEXT);
            hits.add(new Hit(x, y, x + w, y + 18, Type.HUD_ENABLE, null, null));
        } else {
            for (HudModule.Element e : hud.elements) {
                if (!e.on.get() || e.w <= 0 || e.h <= 0) continue;
                boolean hover = mx >= e.x && mx < e.x + e.w && my >= e.y && my < e.y + e.h;
                int col = (hover || dragElement == e) ? acc : 0x70FFFFFF;
                g.fill(e.x, e.y, e.x + e.w, e.y + e.h, hover ? Draw.withAlpha(acc, 0.12f) : 0x10FFFFFF);
                Draw.outline(g, e.x - 1, e.y - 1, e.x + e.w + 1, e.y + e.h + 1, col);
                if (hover) Fonts.text(g, e.id, e.x, Math.max(0, e.y - 10), acc);
                hits.add(new Hit(e.x, e.y, e.x + e.w, e.y + e.h, Type.ELEMENT, e, null));
            }
        }

        // element toggles
        int pw = 120, x = 10, y = 26;
        Draw.round(g, x, y, x + pw, y + HEADER_H, r(), acc, true, false);
        Fonts.text(g, "HUD elements", x + 6, y + 3, 0xFFFFFFFF, true);
        int cy = y + HEADER_H;
        Draw.round(g, x, cy, x + pw, cy + hud.elements.size() * ROW_H + 4, r(), panelBg(), false, true);
        cy += 2;
        for (HudModule.Element e : hud.elements) {
            boolean hover = mx >= x && mx < x + pw && my >= cy && my < cy + ROW_H;
            if (hover) g.fill(x, cy, x + pw, cy + ROW_H, HOVER);
            float t = approach(e.on, e.on.get() ? 1f : 0f, 8f);
            Fonts.text(g, e.id, x + 6, cy + 2, Draw.mix(DIM, TEXT, t));
            int sx = x + pw - 22, sy = cy + 2;
            Draw.pill(g, sx, sy, sx + 16, sy + 8, Draw.mix(TRACK, acc, t));
            int kx = sx + 1 + (int) (8 * ease(t));
            Draw.pill(g, kx, sy + 1, kx + 6, sy + 7, 0xFFFFFFFF);
            hits.add(new Hit(x, cy, x + pw, cy + ROW_H, Type.ELEMENT_TOGGLE, e, null));
            cy += ROW_H;
        }
    }

    // ---- input ---------------------------------------------------------------------------------

    private Hit hitAt(double mx, double my, Type... types) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.contains(mx, my)) continue;
            if (types.length == 0) return h;
            for (Type t : types) if (h.type == t) return h;
        }
        return null;
    }

    private void bringToFront(Panel p) {
        PANELS.remove(p);
        PANELS.add(p);
    }

    private Panel panelOfModule(Module m) {
        for (Panel p : PANELS) if (p.cat == m.category) return p;
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x() / s, my = event.y() / s;
        int button = event.button();
        boolean left = button == InputConstants.MOUSE_BUTTON_LEFT;
        boolean right = button == InputConstants.MOUSE_BUTTON_RIGHT;
        boolean middle = button == InputConstants.MOUSE_BUTTON_MIDDLE;

        // the colour picker parts and sliders sit on top of their setting rows
        Hit h = hitAt(mx, my, Type.PICK_SV, Type.PICK_HUE, Type.PICK_ALPHA, Type.PICK_RAINBOW);
        if (h == null) h = hitAt(mx, my);
        if (h == null) {
            listening = null;
            return super.mouseClicked(event, doubleClick);
        }

        switch (h.type) {
            case HEADER -> {
                bringToFront(h.panel);
                if (left) {
                    dragPanel = h.panel;
                    dragDX = (float) mx - h.panel.x;
                    dragDY = (float) my - h.panel.y;
                } else if (right) {
                    h.panel.open = !h.panel.open;
                    ModuleManager.markDirty();
                }
            }
            case MODULE -> {
                Module m = (Module) h.a;
                Panel p = panelOfModule(m);
                if (p != null) bringToFront(p);
                if (middle) {
                    listening = m;
                } else if (left && !m.isAlwaysOn()) {
                    m.toggle();
                } else if (right || (left && m.isAlwaysOn())) {
                    if (!EXPANDED.remove(m.name)) EXPANDED.add(m.name);
                    ModuleManager.markDirty();
                }
            }
            case SETTING -> {
                Setting s = (Setting) h.a;
                if (s instanceof NumberSetting n) {
                    if (left) {
                        Hit slider = hitAt(mx, my, Type.SLIDER);
                        if (slider != null) {
                            int[] geo = (int[]) slider.a;
                            sliderX = geo[0];
                            sliderW = geo[1];
                            dragSlider = n;
                            n.setFraction((mx - sliderX) / Math.max(1, sliderW));
                        }
                    } else if (right) {
                        n.reset();
                    }
                } else if (s instanceof ColorSetting c) {
                    if (left) {
                        if (!PICKERS.remove(c)) PICKERS.add(c);
                    } else if (right) {
                        c.setRainbow(!c.rainbow());
                    }
                } else if (s instanceof ModeSetting || s instanceof BoolSetting) {
                    if (left || right) s.click(left);
                }
            }
            case SLIDER -> { /* handled via SETTING */ }
            case BIND -> {
                Module m = (Module) h.a;
                if (left) listening = m;
                else if (right) {
                    m.key = -1;
                    listening = null;
                    ModuleManager.markDirty();
                }
            }
            case PICK_SV, PICK_HUE, PICK_ALPHA -> {
                if (left) {
                    dragColor = (ColorSetting) h.a;
                    dragColorPart = h.type;
                    pickX = h.x0;
                    pickY = h.y0;
                    pickW = h.x1 - h.x0;
                    pickH = h.y1 - h.y0;
                    dragColor.setRainbow(false);
                    updateDrags((int) mx, (int) my);
                }
            }
            case PICK_RAINBOW -> {
                ColorSetting c = (ColorSetting) h.a;
                c.setRainbow(!c.rainbow());
            }
            case HUD_BUTTON -> {
                hudEditor = !hudEditor;
                ModuleManager.HUD.editing = hudEditor;
            }
            case SEARCH -> search = "";
            case ELEMENT -> {
                HudModule.Element e = (HudModule.Element) h.a;
                if (left) {
                    dragElement = e;
                    elemDX = (int) mx - e.x;
                    elemDY = (int) my - e.y;
                }
            }
            case ELEMENT_TOGGLE -> ((HudModule.Element) h.a).on.toggle();
            case HUD_ENABLE -> ModuleManager.HUD.setEnabled(true);
            case BODY -> { }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragPanel != null) ModuleManager.markDirty();
        dragPanel = null;
        dragSlider = null;
        dragColor = null;
        dragElement = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double rawX, double rawY, double scrollX, double scrollY) {
        double mx = rawX / s, my = rawY / s;
        if (!hudEditor) {
            for (int i = PANELS.size() - 1; i >= 0; i--) {
                Panel p = PANELS.get(i);
                if (mx >= p.bx0 && mx < p.bx1 && my >= p.by0 && my < p.by1) {
                    p.scrollTarget -= (float) (scrollY * 24.0);
                    return true;
                }
            }
        }
        return super.mouseScrolled(rawX, rawY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (listening != null) {
            if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) {
                listening.key = -1;
            } else {
                listening.key = key;
            }
            listening = null;
            ModuleManager.markDirty();
            return true;
        }
        if (key == InputConstants.KEY_BACKSPACE) {
            if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            if (!search.isEmpty()) { search = ""; return true; }
            if (hudEditor) { hudEditor = false; ModuleManager.HUD.editing = false; return true; }
            onClose();
            return true;
        }
        if (key == UtilClient.guiKey() && search.isEmpty()) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (listening != null) return true;
        int cp = event.codepoint();
        if (cp >= 32 && !Character.isISOControl(cp) && search.length() < 24) {
            search += new String(Character.toChars(cp));
            for (Panel p : PANELS) p.scrollTarget = 0;
        }
        return true;
    }

    @Override
    public void onClose() {
        ModuleManager.HUD.editing = false;
        Config.save();
        if (parent != null) minecraft.gui.setScreen(parent);
        else super.onClose();
    }

    @Override
    public void removed() {
        ModuleManager.HUD.editing = false;
        super.removed();
    }
}

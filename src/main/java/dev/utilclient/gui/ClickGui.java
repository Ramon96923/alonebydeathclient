package dev.utilclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.utilclient.module.BoolSetting;
import dev.utilclient.module.ColorSetting;
import dev.utilclient.module.Module;
import dev.utilclient.module.ModuleManager;
import dev.utilclient.module.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Left-click a module to toggle it, right-click to expand its options.
 * On option rows: left/right click changes the value (+/- or next/previous). Scroll wheel scrolls the list.
 * RSHIFT / ESC closes.
 */
public class ClickGui extends Screen {
    private static final int PANEL_W = 200, ROW_H = 16, HEAD_H = 24, FOOT_H = 16;

    private static final int BG     = 0xF2121216;
    private static final int HEAD   = 0xFF1C1D28;
    private static final int ACCENT = 0xFF7C5CFF;
    private static final int HOVER  = 0x22FFFFFF;
    private static final int TEXT   = 0xFFE8E8EE;
    private static final int DIM    = 0xFF8A8A99;
    private static final int ON     = 0xFF4ADE80;
    private static final int OFF    = 0xFF3A3A48;
    private static final int KNOB   = 0xFFFFFFFF;

    private static final Set<Module> expanded = new HashSet<>();
    private static int scroll = 0;

    private record Row(Module module, Setting setting) {}
    private record Placed(Row row, int x, int y) {
        boolean hit(double mx, double my) { return mx >= x && mx < x + PANEL_W && my >= y && my < y + ROW_H; }
    }

    public ClickGui() { super(Component.literal("Util Client")); }

    @Override public boolean isPauseScreen() { return false; } // world keeps running

    private List<Row> allRows() {
        List<Row> rows = new ArrayList<>();
        for (Module m : ModuleManager.ALL) {
            rows.add(new Row(m, null));
            if (expanded.contains(m)) for (Setting s : m.settings) rows.add(new Row(m, s));
        }
        return rows;
    }

    private int maxVisible() { return Math.max(4, (height - 50 - HEAD_H - FOOT_H) / ROW_H); }

    private int totalHeight(int visible) { return HEAD_H + visible * ROW_H + FOOT_H; }

    private List<Placed> placed(List<Row> rows) {
        int visible = Math.min(rows.size(), maxVisible());
        scroll = Math.max(0, Math.min(scroll, rows.size() - visible));
        int x = (width - PANEL_W) / 2;
        int y = (height - totalHeight(visible)) / 2 + HEAD_H;
        List<Placed> out = new ArrayList<>();
        for (int i = 0; i < visible; i++) {
            out.add(new Placed(rows.get(scroll + i), x, y + i * ROW_H));
        }
        return out;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);

        List<Row> rows = allRows();
        List<Placed> placed = placed(rows);
        int visible = placed.size();
        int x = (width - PANEL_W) / 2;
        int top = (height - totalHeight(visible)) / 2;

        g.fill(x, top, x + PANEL_W, top + totalHeight(visible), BG);
        g.fill(x, top, x + PANEL_W, top + HEAD_H, HEAD);
        g.fill(x, top + HEAD_H - 2, x + PANEL_W, top + HEAD_H, ACCENT);
        g.text(font, "Util Client", x + 8, top + (HEAD_H - font.lineHeight) / 2 - 1, TEXT, true);

        for (Placed p : placed) {
            if (p.hit(mx, my)) g.fill(p.x, p.y, p.x + PANEL_W, p.y + ROW_H, HOVER);
            int ty = p.y + (ROW_H - font.lineHeight) / 2;
            Module m = p.row.module();
            Setting s = p.row.setting();

            if (s == null) {
                g.text(font, m.name, p.x + 8, ty, TEXT, false);
                if (!m.settings.isEmpty()) {
                    g.text(font, expanded.contains(m) ? "-" : "+", p.x + PANEL_W - 44, ty, DIM, false);
                }
                drawSwitch(g, p.x + PANEL_W - 28, p.y + 3, m.isEnabled());
            } else if (s instanceof BoolSetting b) {
                g.text(font, s.name, p.x + 18, ty, b.get() ? TEXT : DIM, false);
                drawCheck(g, p.x + PANEL_W - 20, p.y + 4, b.get());
            } else {
                g.text(font, s.name, p.x + 18, ty, TEXT, false);
                String v = s.valueText();
                int vx = p.x + PANEL_W - 10 - font.width(v);
                g.text(font, v, vx, ty, ACCENT, false);
                if (s instanceof ColorSetting c) {
                    g.fill(vx - 12, p.y + 4, vx - 4, p.y + 12, 0xFF000000 | c.rgb());
                }
            }
        }

        String hint = rows.size() > visible ? "Scroll for more | right-click = options"
                                            : "Right-click a module for options";
        g.text(font, hint, x + 8, top + totalHeight(visible) - FOOT_H + (FOOT_H - font.lineHeight) / 2, DIM, false);
    }

    private static void drawSwitch(GuiGraphicsExtractor g, int x, int y, boolean on) {
        g.fill(x, y, x + 20, y + 10, on ? ON : OFF);
        int kx = on ? x + 11 : x + 1;
        g.fill(kx, y + 1, kx + 8, y + 9, KNOB);
    }

    private static void drawCheck(GuiGraphicsExtractor g, int x, int y, boolean on) {
        g.fill(x, y, x + 8, y + 8, OFF);
        if (on) g.fill(x + 2, y + 2, x + 6, y + 6, ACCENT);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (Placed p : placed(allRows())) {
            if (!p.hit(event.x(), event.y())) continue;
            Module m = p.row.module();
            Setting s = p.row.setting();
            boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
            boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
            if (s != null) {
                if (left || right) s.click(left);
            } else if (left) {
                m.toggle();
            } else if (right && !m.settings.isEmpty()) {
                if (!expanded.remove(m)) expanded.add(m);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        scroll -= (int) Math.signum(scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RSHIFT) { onClose(); return true; }
        return super.keyPressed(event);
    }
}

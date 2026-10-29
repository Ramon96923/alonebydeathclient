package dev.utilclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.utilclient.module.BoolSetting;
import dev.utilclient.module.Category;
import dev.utilclient.module.ColorSetting;
import dev.utilclient.module.Module;
import dev.utilclient.module.ModuleManager;
import dev.utilclient.module.NumberSetting;
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
 * Category panels you can drag around.
 *  - header: drag to move, right-click to collapse, scroll over a panel to scroll it
 *  - module: left-click toggles, right-click opens its options
 *  - options: click checkboxes / colours, drag sliders (left click = next colour, right click = previous)
 * Right Shift or Esc closes.
 */
public class ClickGui extends Screen {
    private static final int PW = 140, HEAD_H = 16, ROW_H = 14, SET_H = 14;

    private static final int BG         = 0xE60E0E13;
    private static final int HEAD       = 0xFF181824;
    private static final int ACCENT     = 0xFF8B5CF6;
    private static final int ACCENT_DIM = 0x558B5CF6;
    private static final int HOVER      = 0x22FFFFFF;
    private static final int TEXT       = 0xFFE8E8EE;
    private static final int DIM        = 0xFF8A8A99;
    private static final int TRACK      = 0xFF2A2A38;

    private static final class PanelState {
        final Category cat;
        int x, y, scroll;
        boolean collapsed;
        PanelState(Category cat, int x, int y) { this.cat = cat; this.x = x; this.y = y; }
    }

    private record Item(Module module, Setting setting) {}
    private record Placed(Item item, int x, int y, int w, int h) {
        boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private record Layout(List<Placed> rows, int bottom) {}

    private static final List<PanelState> panels = new ArrayList<>();   // last = drawn on top
    private static final Set<Module> expanded = new HashSet<>();

    private PanelState dragging;
    private int dragDX, dragDY;
    private NumberSetting slider;
    private int sliderX, sliderW;

    public ClickGui() {
        super(Component.literal("AloneByDeath Client"));
        if (panels.isEmpty()) {
            int i = 0;
            for (Category c : Category.values()) panels.add(new PanelState(c, 14 + i++ * (PW + 10), 18));
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    private Layout layout(PanelState ps) {
        List<Placed> out = new ArrayList<>();
        int y = ps.y + HEAD_H;
        if (!ps.collapsed) {
            List<Item> items = new ArrayList<>();
            for (Module m : ModuleManager.byCategory(ps.cat)) {
                items.add(new Item(m, null));
                if (expanded.contains(m)) for (Setting s : m.settings) items.add(new Item(m, s));
            }
            ps.scroll = Math.max(0, Math.min(ps.scroll, Math.max(0, items.size() - 1)));
            for (int i = ps.scroll; i < items.size(); i++) {
                Item it = items.get(i);
                int h = it.setting() == null ? ROW_H : SET_H;
                if (y + h > height - 4 && !out.isEmpty()) break;
                out.add(new Placed(it, ps.x, y, PW, h));
                y += h;
            }
        }
        return new Layout(out, y + 2);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);

        if (dragging != null) {
            dragging.x = Math.max(0, Math.min(mx - dragDX, Math.max(0, width - PW)));
            dragging.y = Math.max(0, Math.min(my - dragDY, Math.max(0, height - HEAD_H)));
        }
        if (slider != null) slider.setFraction((mx - sliderX) / (double) sliderW);

        Module hovered = null;
        for (PanelState ps : panels) {
            Layout lay = layout(ps);
            g.fill(ps.x, ps.y, ps.x + PW, lay.bottom, BG);
            g.fill(ps.x, ps.y, ps.x + PW, ps.y + HEAD_H, HEAD);
            g.fill(ps.x, ps.y + HEAD_H - 1, ps.x + PW, ps.y + HEAD_H, ACCENT);
            g.text(font, ps.cat.label, ps.x + 6, ps.y + (HEAD_H - font.lineHeight) / 2, TEXT, true);
            g.text(font, ps.collapsed ? "+" : "-", ps.x + PW - 11, ps.y + (HEAD_H - font.lineHeight) / 2, DIM, false);

            for (Placed p : lay.rows) {
                boolean hov = p.hit(mx, my);
                Module m = p.item.module();
                Setting s = p.item.setting();
                int ty = p.y + (p.h - font.lineHeight) / 2;

                if (s == null) {
                    if (m.isEnabled()) g.fill(p.x, p.y, p.x + p.w, p.y + p.h, ACCENT_DIM);
                    if (hov) { g.fill(p.x, p.y, p.x + p.w, p.y + p.h, HOVER); hovered = m; }
                    g.text(font, m.name, p.x + 6, ty, m.isEnabled() ? 0xFFFFFFFF : DIM, false);
                    if (!m.settings.isEmpty()) {
                        g.text(font, expanded.contains(m) ? "v" : ">", p.x + p.w - 11, ty, DIM, false);
                    }
                } else {
                    if (hov) g.fill(p.x, p.y, p.x + p.w, p.y + p.h, HOVER);
                    if (s instanceof BoolSetting b) {
                        g.text(font, s.name, p.x + 10, ty, b.get() ? TEXT : DIM, false);
                        g.fill(p.x + p.w - 16, p.y + 3, p.x + p.w - 6, p.y + 11, TRACK);
                        if (b.get()) g.fill(p.x + p.w - 14, p.y + 5, p.x + p.w - 8, p.y + 9, ACCENT);
                    } else if (s instanceof NumberSetting n) {
                        g.text(font, s.name, p.x + 10, p.y + 1, TEXT, false);
                        String v = n.valueText();
                        g.text(font, v, p.x + p.w - 8 - font.width(v), p.y + 1, ACCENT, false);
                        int bw = p.w - 20;
                        g.fill(p.x + 10, p.y + p.h - 3, p.x + 10 + bw, p.y + p.h - 1, TRACK);
                        g.fill(p.x + 10, p.y + p.h - 3, p.x + 10 + (int) (bw * n.fraction()), p.y + p.h - 1, ACCENT);
                    } else if (s instanceof ColorSetting c) {
                        g.text(font, s.name, p.x + 10, ty, TEXT, false);
                        String v = c.valueText();
                        int vx = p.x + p.w - 8 - font.width(v);
                        g.text(font, v, vx, ty, DIM, false);
                        g.fill(vx - 12, p.y + 3, vx - 4, p.y + 11, 0xFF000000 | c.rgb());
                    }
                }
            }
        }

        if (hovered != null && dragging == null && slider == null) {
            String d = hovered.description;
            int w = font.width(d) + 8;
            int tx = Math.min(mx + 8, width - w - 2);
            int tyy = Math.min(my + 10, height - 14);
            g.fill(tx, tyy, tx + w, tyy + 12, 0xF0101018);
            g.fill(tx, tyy, tx + 1, tyy + 12, ACCENT);
            g.text(font, d, tx + 4, tyy + 2, TEXT, false);
        }
    }

    private void bringToFront(PanelState ps) {
        panels.remove(ps);
        panels.add(ps);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
        boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        if (!left && !right) return super.mouseClicked(event, doubleClick);

        for (int i = panels.size() - 1; i >= 0; i--) {
            PanelState ps = panels.get(i);
            if (mx >= ps.x && mx < ps.x + PW && my >= ps.y && my < ps.y + HEAD_H) {
                bringToFront(ps);
                if (left) { dragging = ps; dragDX = (int) mx - ps.x; dragDY = (int) my - ps.y; }
                else ps.collapsed = !ps.collapsed;
                return true;
            }
            for (Placed p : layout(ps).rows) {
                if (!p.hit(mx, my)) continue;
                bringToFront(ps);
                handleRow(p, left);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void handleRow(Placed p, boolean left) {
        Module m = p.item.module();
        Setting s = p.item.setting();
        if (s == null) {
            if (left) m.toggle();
            else if (!m.settings.isEmpty() && !expanded.remove(m)) expanded.add(m);
        } else if (s instanceof NumberSetting n) {
            if (left) {
                slider = n;
                sliderX = p.x + 10;
                sliderW = p.w - 20;
            }
        } else {
            s.click(left);
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        slider = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        for (int i = panels.size() - 1; i >= 0; i--) {
            PanelState ps = panels.get(i);
            if (mx >= ps.x && mx < ps.x + PW && my >= ps.y && my < layout(ps).bottom) {
                ps.scroll -= (int) Math.signum(scrollY);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RSHIFT) { onClose(); return true; }
        return super.keyPressed(event);
    }
}

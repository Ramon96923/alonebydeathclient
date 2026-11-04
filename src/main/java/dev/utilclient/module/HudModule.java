package dev.utilclient.module;

import com.google.gson.JsonObject;
import dev.utilclient.gui.Fonts;
import dev.utilclient.util.CombatState;
import dev.utilclient.util.Draw;
import dev.utilclient.util.Inv;
import dev.utilclient.util.RenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** On-screen information. Every element can be switched off here and dragged in the HUD editor. */
public final class HudModule extends Module {
    private final ModeSetting colorMode = mode("Colours", "Theme", "Theme", "Rainbow", "White")
            .desc("Theme follows the ClickGUI accent");
    private final BoolSetting backgrounds = bool("Backgrounds", true);
    private final NumberSetting bgOpacity = num("Background opacity", 140, 20, 255, 5).visible(backgrounds::get);
    private final BoolSetting rounded = bool("Rounded", true).desc("Rounded corners on HUD boxes");
    private final BoolSetting watermarkInfo = bool("Watermark info", true).desc("FPS and ping next to the name");
    private final BoolSetting lowercase = bool("Lowercase", false);
    private final BoolSetting listInfo = bool("List mode info", true).desc("Show a module's mode next to its name");
    private final ModeSetting listSort = mode("List order", "Length", "Length", "Alphabetical");

    public final List<Element> elements = new ArrayList<>();
    /** Set by the click GUI while the HUD editor is open: shows placeholder content. */
    public boolean editing;

    private final Map<Module, Float> listAnim = new HashMap<>();
    private long lastFrame;
    private double speed;

    public HudModule() {
        super("HUD", "Watermark, module list, coordinates and more", Category.CLIENT);
        enabledByDefault = true;
        elements.add(new Element("Watermark", true, false, false, 4, 4, this::watermark));
        elements.add(new Element("Module list", true, true, false, 2, 2, this::moduleList));
        elements.add(new Element("Coordinates", true, false, true, 4, 4, this::coords));
        elements.add(new Element("Info", true, true, true, 4, 4, this::info));
        elements.add(new Element("Armor", true, false, true, 0, 58, this::armor).centered());
        elements.add(new Element("Totems", true, false, true, 0, 58, this::totems).centered().shift(78));
        elements.add(new Element("Target HUD", true, false, false, 0, 0, this::targetHud).centered().shift(14).mid());
        elements.add(new Element("Potions", false, true, true, 4, 40, this::potions));
        elements.add(new Element("Keystrokes", false, false, true, 4, 60, this::keystrokes));
    }

    // ---- element model -------------------------------------------------------------------------

    @FunctionalInterface
    interface Painter {
        /** Draws (unless dryRun) and returns {width, height}. */
        int[] paint(GuiGraphicsExtractor g, int x, int y, boolean alignRight, boolean dryRun);
    }

    public final class Element {
        public final String id;
        public final BoolSetting on;
        private final Painter painter;
        boolean right, bottom;
        int offX, offY;
        boolean placed;
        private boolean centered, mid;
        private int shift;
        public int x, y, w, h;

        Element(String id, boolean def, boolean right, boolean bottom, int offX, int offY, Painter painter) {
            this.id = id;
            this.on = bool(id, def);
            this.right = right;
            this.bottom = bottom;
            this.offX = offX;
            this.offY = offY;
            this.painter = painter;
        }

        Element centered() { centered = true; return this; }
        Element mid() { mid = true; return this; }
        Element shift(int s) { shift = s; return this; }

        /** Default placement relative to the screen centre (hotbar / crosshair elements). */
        void placeDefault(int sw, int sh) {
            if (placed || !centered) return;
            placed = true;
            right = false;
            offX = sw / 2 - 40 + shift;
            if (mid) { bottom = false; offY = sh / 2 + 14; }
        }

        int[] measure(GuiGraphicsExtractor g, boolean alignRight) { return painter.paint(g, 0, 0, alignRight, true); }

        public void moveTo(int nx, int ny, int sw, int sh) {
            placed = true;
            nx = Draw.clamp(nx, 0, Math.max(0, sw - w));
            ny = Draw.clamp(ny, 0, Math.max(0, sh - h));
            right = nx + w / 2 > sw / 2;
            bottom = ny + h / 2 > sh / 2;
            offX = right ? sw - (nx + w) : nx;
            offY = bottom ? sh - (ny + h) : ny;
            ModuleManager.markDirty();
        }
    }

    @Override
    public JsonObject saveExtra() {
        JsonObject o = new JsonObject();
        for (Element e : elements) {
            JsonObject p = new JsonObject();
            p.addProperty("right", e.right);
            p.addProperty("bottom", e.bottom);
            p.addProperty("x", e.offX);
            p.addProperty("y", e.offY);
            p.addProperty("placed", e.placed);
            o.add(e.id, p);
        }
        return o;
    }

    @Override
    public void loadExtra(JsonObject json) {
        for (Element e : elements) {
            if (!json.has(e.id)) continue;
            JsonObject p = json.getAsJsonObject(e.id);
            e.right = p.get("right").getAsBoolean();
            e.bottom = p.get("bottom").getAsBoolean();
            e.offX = p.get("x").getAsInt();
            e.offY = p.get("y").getAsInt();
            e.placed = !p.has("placed") || p.get("placed").getAsBoolean();
        }
    }

    // ---- frame ----------------------------------------------------------------------------------

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        double dx = p.getX() - p.xOld, dz = p.getZ() - p.zOld;
        speed = Math.sqrt(dx * dx + dz * dz) * 20.0;
    }

    @Override
    public void renderHud(GuiGraphicsExtractor g, float partialTick) {
        int sw = g.guiWidth(), sh = g.guiHeight();
        for (Element e : elements) {
            if (!e.on.get()) continue;
            e.placeDefault(sw, sh);
            int[] size = e.measure(g, e.right);
            e.w = size[0];
            e.h = size[1];
            if (e.w <= 0 || e.h <= 0) continue;
            e.x = e.right ? sw - e.offX - e.w : e.offX;
            e.y = e.bottom ? sh - e.offY - e.h : e.offY;
            e.x = Draw.clamp(e.x, 0, Math.max(0, sw - e.w));
            e.y = Draw.clamp(e.y, 0, Math.max(0, sh - e.h));
            e.painter.paint(g, e.x, e.y, e.right, false);
        }
    }

    // ---- helpers -------------------------------------------------------------------------------

    private String txt(String s) { return lowercase.get() ? s.toLowerCase(Locale.ROOT) : s; }

    private int color(float offset) {
        return switch (colorMode.get()) {
            case "Rainbow" -> Draw.hsv((System.currentTimeMillis() % 5000L) / 5000f + offset, 0.6f, 1f);
            case "White" -> 0xFFFFFFFF;
            default -> ModuleManager.CLICK_GUI.accent(offset);
        };
    }

    private void bg(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) { bg(g, x0, y0, x1, y1, 4); }

    private void bg(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int radius) {
        if (!backgrounds.get()) return;
        int c = Draw.alpha(0x0B0B10, bgOpacity.getInt());
        if (rounded.get()) Draw.round(g, x0, y0, x1, y1, radius, c);
        else g.fill(x0, y0, x1, y1, c);
    }

    private static int ping() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) return -1;
        var pi = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return pi == null ? -1 : pi.getLatency();
    }

    private static int[] size(int w, int h) { return new int[] {w, h}; }

    private static String prettyId(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    // ---- elements -------------------------------------------------------------------------------

    private int[] watermark(GuiGraphicsExtractor g, int x, int y, boolean right, boolean dry) {
        String a = txt("DonutWaffen"), b = " " + dev.utilclient.UtilClient.version();
        String extra = "";
        if (watermarkInfo.get()) {
            int pg = ping();
            extra = "  |  " + RenderState.fps() + " fps" + (pg >= 0 ? "  |  " + pg + " ms" : "");
        }
        int w = Fonts.width(a, true) + Fonts.width(b) + Fonts.width(extra) + 12, h = 15;
        if (dry) return size(w, h);
        bg(g, x, y, x + w, y + h, 5);
        // accent strip + the name in a per-letter gradient
        Draw.round(g, x + 5, y + h - 2, x + w - 5, y + h - 1, 0, Draw.withAlpha(color(0f), 0.8f));
        int cx = x + 6;
        for (int i = 0; i < a.length(); i++) {
            String ch = String.valueOf(a.charAt(i));
            Fonts.text(g, ch, cx, y + 3, color(i * 0.05f), true);
            cx += Fonts.width(ch, true);
        }
        Fonts.text(g, b, cx, y + 3, 0xFFB0B0C0);
        cx += Fonts.width(b);
        if (!extra.isEmpty()) Fonts.text(g, extra, cx, y + 3, 0xFF8A8A9A);
        return size(w, h);
    }

    private String listLabel(Module m) {
        String info = listInfo.get() ? m.info() : null;
        return txt(m.name) + (info != null && !info.isEmpty() ? " " + info : "");
    }

    private int[] moduleList(GuiGraphicsExtractor g, int x, int y, boolean alignRight, boolean dry) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1e9f);
        if (!dry) lastFrame = now;

        List<Module> shown = new ArrayList<>();
        for (Module m : ModuleManager.ALL) {
            if (m.isAlwaysOn() || m.category == Category.CLIENT || m.drawn == null || !m.drawn.get()) continue;
            boolean on = m.isEnabled();
            float a = listAnim.getOrDefault(m, 0f);
            if (!dry) {
                a = on ? Math.min(1f, a + dt * 6f) : Math.max(0f, a - dt * 6f);
                listAnim.put(m, a);
            }
            if (on || a > 0f) shown.add(m);
        }
        if (editing && shown.isEmpty()) shown.add(this);
        if (listSort.is("Alphabetical")) shown.sort((p, q) -> p.name.compareToIgnoreCase(q.name));
        else shown.sort((p, q) -> Fonts.width(listLabel(q)) - Fonts.width(listLabel(p)));

        int maxW = 0;
        for (Module m : shown) maxW = Math.max(maxW, Fonts.width(listLabel(m)) + 8);
        int rowH = 11;
        int h = 0;
        for (Module m : shown) h += (int) Math.ceil(rowH * Math.max(listAnim.getOrDefault(m, 1f), m == this ? 1f : 0f));
        if (dry) return size(maxW, Math.max(rowH, h));

        int cy = y;
        int i = 0;
        for (Module m : shown) {
            float a = m == this ? 1f : listAnim.getOrDefault(m, 0f);
            float e = 1f - (1f - a) * (1f - a);
            String label = listLabel(m);
            String name = txt(m.name);
            int w = Fonts.width(label) + 8;
            int slide = (int) ((1f - e) * (w + 4));
            int rx = alignRight ? x + maxW - w + slide : x - slide;
            int col = color(i * 0.06f);
            bg(g, rx, cy, rx + w, cy + rowH, 2);
            if (alignRight) g.fill(rx + w - 1, cy, rx + w, cy + rowH, col);
            else g.fill(rx, cy, rx + 1, cy + rowH, col);
            Fonts.text(g, name, rx + 4, cy + 1, col);
            if (label.length() > name.length()) {
                Fonts.text(g, label.substring(name.length()), rx + 4 + Fonts.width(name), cy + 1, 0xFF9A9AAA);
            }
            cy += (int) Math.ceil(rowH * a);
            i++;
        }
        return size(maxW, h);
    }

    private int[] coords(GuiGraphicsExtractor g, int x, int y, boolean right, boolean dry) {
        Minecraft mc = Minecraft.getInstance();
        if (ModuleManager.STREAMER_MODE.hideCoords() && !editing) return size(0, 0);
        LocalPlayer p = mc.player;
        boolean nether = mc.level.dimension() == Level.NETHER;
        double scale = nether ? 8.0 : 0.125;
        String main = String.format(Locale.ROOT, "XYZ %.1f %.1f %.1f", p.getX(), p.getY(), p.getZ());
        String other = String.format(Locale.ROOT, "%s %.1f %.1f", nether ? "Overworld" : "Nether", p.getX() * scale, p.getZ() * scale);
        String facing = "Facing " + facing(p.getYRot()) + axisHint(p.getYRot());
        String[] lines = {txt(facing), txt(other), txt(main)};
        int w = 0;
        for (String s : lines) w = Math.max(w, Fonts.width(s));
        w += 8;
        int h = lines.length * 10 + 2;
        if (dry) return size(w, h);
        bg(g, x, y, x + w, y + h);
        for (int i = 0; i < lines.length; i++) {
            Fonts.text(g, lines[i], right ? x + w - 4 - Fonts.width(lines[i]) : x + 4, y + 2 + i * 10,
                    i == lines.length - 1 ? 0xFFFFFFFF : 0xFFA8A8B8);
        }
        return size(w, h);
    }

    private static String facing(float yaw) {
        float y = ((yaw % 360f) + 360f) % 360f;
        if (y >= 315 || y < 45) return "South";
        if (y < 135) return "West";
        if (y < 225) return "North";
        return "East";
    }

    private static String axisHint(float yaw) {
        float y = ((yaw % 360f) + 360f) % 360f;
        if (y >= 315 || y < 45) return " (+Z)";
        if (y < 135) return " (-X)";
        if (y < 225) return " (-Z)";
        return " (+X)";
    }

    private int[] info(GuiGraphicsExtractor g, int x, int y, boolean right, boolean dry) {
        Minecraft mc = Minecraft.getInstance();
        int ping = 0;
        try {
            PlayerInfo pi = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (pi != null) ping = pi.getLatency();
        } catch (Throwable ignored) {
            // singleplayer edge cases
        }
        String[] lines = {
                txt(String.format(Locale.ROOT, "Speed %.1f b/s", speed)),
                txt("Ping " + ping + "ms"),
                txt("FPS " + RenderState.fps())
        };
        int w = 0;
        for (String s : lines) w = Math.max(w, Fonts.width(s));
        w += 8;
        int h = lines.length * 10 + 2;
        if (dry) return size(w, h);
        bg(g, x, y, x + w, y + h);
        for (int i = 0; i < lines.length; i++) {
            int tx = right ? x + w - 4 - Fonts.width(lines[i]) : x + 4;
            Fonts.text(g, lines[i], tx, y + 2 + i * 10, color(i * 0.1f));
        }
        return size(w, h);
    }

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private int[] armor(GuiGraphicsExtractor g, int x, int y, boolean right, boolean dry) {
        Minecraft mc = Minecraft.getInstance();
        int w = 4 * 18, h = 26;
        if (dry) return size(w, h);
        for (int i = 0; i < 4; i++) {
            ItemStack s = mc.player.getItemBySlot(ARMOR[i]);
            int ix = x + i * 18;
            if (s.isEmpty()) {
                if (editing) Draw.outline(g, ix, y, ix + 16, y + 16, 0x60FFFFFF);
                continue;
            }
            g.item(s, ix, y);
            g.itemDecorations(mc.font, s, ix, y);
            if (s.isDamageableItem()) {
                float frac = 1f - s.getDamageValue() / (float) Math.max(1, s.getMaxDamage());
                String pct = Math.round(frac * 100) + "%";
                Fonts.text(g, pct, ix + 8 - Fonts.width(pct) / 2, y + 17, Draw.healthColor(frac));
            }
        }
        return size(w, h);
    }

    private int[] totems(GuiGraphicsExtractor g, int x, int y, boolean right, boolean dry) {
        Minecraft mc = Minecraft.getInstance();
        int n = Inv.count(mc.player, Items.TOTEM_OF_UNDYING);
        if (n == 0 && !editing) return size(0, 0);
        int w = 18 + Fonts.width(Integer.toString(n)) + 4, h = 16;
        if (dry) return size(w, h);
        g.item(new ItemStack(Items.TOTEM_OF_UNDYING), x, y);
        Fonts.text(g, Integer.toString(n), x + 18, y + 4, n > 1 ? 0xFFFFFFFF : 0xFFF87171, true);
        return size(w, h);
    }

    private int[] targetHud(GuiGraphicsExtractor g, int x, int y, boolean right, boolean dry) {
        Minecraft mc = Minecraft.getInstance();
        LivingEntity t = CombatState.current();
        if (t == null && editing) t = mc.player;
        if (t == null) return size(0, 0);
        int w = 130, h = 34;
        if (dry) return size(w, h);
        Draw.soft(g, x, y, x + w, y + h, 0xC0101016);
        g.fill(x, y, x + 2, y + h, color(0f));
        String name = Fonts.fit(t.getName().getString(), w - 50);
        Fonts.text(g, name, x + 7, y + 4, 0xFFFFFFFF, true);
        float hp = t.getHealth() + t.getAbsorptionAmount();
        float frac = Math.min(1f, t.getHealth() / Math.max(1f, t.getMaxHealth()));
        String hpText = String.format(Locale.ROOT, "%.1f", hp);
        Fonts.text(g, hpText, x + w - 6 - Fonts.width(hpText), y + 4, Draw.healthColor(frac), true);
        String dist = String.format(Locale.ROOT, "%.1fm", mc.player.distanceTo(t));
        Fonts.text(g, dist, x + 7, y + 15, 0xFFA0A0B0);
        int barX = x + 7, barW = w - 14;
        g.fill(barX, y + 26, barX + barW, y + 29, 0xFF2A2A36);
        g.fill(barX, y + 26, barX + (int) (barW * frac), y + 29, Draw.healthColor(frac));
        if (t.getAbsorptionAmount() > 0) {
            int aw = (int) (barW * Math.min(1f, t.getAbsorptionAmount() / Math.max(1f, t.getMaxHealth())));
            g.fill(barX, y + 25, barX + aw, y + 26, 0xFFFACC15);
        }
        return size(w, h);
    }

    private int[] potions(GuiGraphicsExtractor g, int x, int y, boolean right, boolean dry) {
        Minecraft mc = Minecraft.getInstance();
        List<String> lines = new ArrayList<>();
        for (MobEffectInstance e : mc.player.getActiveEffects()) {
            String name = prettyId(BuiltInRegistries.MOB_EFFECT.getKey(e.getEffect().value()).getPath());
            if (e.getAmplifier() > 0) name += " " + (e.getAmplifier() + 1);
            int d = e.getDuration();
            String time = d < 0 || d > 20 * 60 * 60 ? "**:**" : String.format(Locale.ROOT, "%d:%02d", d / 1200, (d / 20) % 60);
            lines.add(txt(name + " " + time));
        }
        if (lines.isEmpty() && editing) lines.add("Potions");
        if (lines.isEmpty()) return size(0, 0);
        int w = 0;
        for (String s : lines) w = Math.max(w, Fonts.width(s));
        w += 8;
        int h = lines.size() * 10 + 2;
        if (dry) return size(w, h);
        bg(g, x, y, x + w, y + h);
        for (int i = 0; i < lines.size(); i++) {
            Fonts.text(g, lines.get(i), right ? x + w - 4 - Fonts.width(lines.get(i)) : x + 4, y + 2 + i * 10, 0xFFE0E0EA);
        }
        return size(w, h);
    }

    // ---- keystrokes -----------------------------------------------------------------------------

    private static final java.util.ArrayDeque<Long> LEFT = new java.util.ArrayDeque<>(), RIGHT = new java.util.ArrayDeque<>();

    /** From MouseHandlerMixin on every mouse press: feeds the CPS counters. */
    public static void onClick(int button) {
        long now = System.currentTimeMillis();
        if (button == 0) LEFT.addLast(now);
        else if (button == 1) RIGHT.addLast(now);
    }

    private static int cps(java.util.ArrayDeque<Long> q) {
        long cutoff = System.currentTimeMillis() - 1000;
        while (!q.isEmpty() && q.peekFirst() < cutoff) q.pollFirst();
        return q.size();
    }

    private void key(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean down) {
        float t = 0f;
        if (down) t = 1f;
        int fill = down ? Draw.withAlpha(color(0f), 0.85f) : Draw.alpha(0x0B0B10, bgOpacity.getInt());
        if (rounded.get()) Draw.round(g, x, y, x + w, y + h, 3, fill);
        else g.fill(x, y, x + w, y + h, fill);
        Fonts.centered(g, label, x + w / 2, y + h / 2 - 4, down ? 0xFF101014 : 0xFFE6E6EE, t > 0.5f);
    }

    private int[] keystrokes(GuiGraphicsExtractor g, int x, int y, boolean right, boolean dry) {
        int k = 18, gap = 2, w = k * 3 + gap * 2, h = k * 3 + gap * 2 + 10;
        if (dry) return size(w, h);
        Minecraft mc = Minecraft.getInstance();
        var o = mc.options;
        key(g, x + k + gap, y, k, k, "W", o.keyUp.isDown());
        key(g, x, y + k + gap, k, k, "A", o.keyLeft.isDown());
        key(g, x + k + gap, y + k + gap, k, k, "S", o.keyDown.isDown());
        key(g, x + (k + gap) * 2, y + k + gap, k, k, "D", o.keyRight.isDown());
        int half = (w - gap) / 2;
        key(g, x, y + (k + gap) * 2, half, k, cps(LEFT) + " CPS", o.keyAttack.isDown());
        key(g, x + half + gap, y + (k + gap) * 2, half, k, cps(RIGHT) + " CPS", o.keyUse.isDown());
        key(g, x, y + (k + gap) * 3, w, 8, "", o.keyJump.isDown());
        return size(w, h);
    }
}

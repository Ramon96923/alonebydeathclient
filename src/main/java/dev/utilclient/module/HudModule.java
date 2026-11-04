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
    private final ModeSetting shape = mode("Shape", "Round", "Round", "Rounded", "Square")
            .desc("Round: pill-shaped boxes and circular keys. Rounded: corners set by Corner radius");
    private final BoolSetting rounded = bool("Rounded", true).visible(() -> false);   // kept for old configs
    private final NumberSetting radius = num("Corner radius", 6, 1, 10, 1).visible(() -> shape.is("Rounded"));
    private final BoolSetting lowercase = bool("Lowercase", false);

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
        elements.add(new Element("Potions", false, true, true, 4, 124, this::potions));
        elements.add(new Element("Keystrokes", false, false, true, 4, 60, this::keystrokes));
        elements.add(new Element("Staff list", true, false, false, 4, 130, this::staff));
        elements.add(new Element("Spotify", true, true, true, 4, 40, this::spotify));
        elements.add(new Element("Music player", true, true, true, 4, 78, this::music));

        // per-element options (right-click an element in the HUD editor)
        wmInfo = el("Watermark").bool("Info", true, "FPS and ping next to the name");
        wmVersion = el("Watermark").bool("Version", true, null);
        listInfo = el("Module list").bool("Mode info", true, "Show a module's mode next to its name");
        listSort = el("Module list").mode("Order", "Length", "Length", "Alphabetical");
        listBars = el("Module list").bool("Side bar", true, "Accent line on each row");
        coOther = el("Coordinates").bool("Other dimension", true, "Nether / Overworld coordinates");
        coFacing = el("Coordinates").bool("Facing", true, null);
        coDecimals = el("Coordinates").num("Decimals", 1, 0, 2, 1);
        infSpeed = el("Info").bool("Speed", true, null);
        infPing = el("Info").bool("Ping", true, null);
        infFps = el("Info").bool("FPS", true, null);
        arDurability = el("Armor").mode("Durability", "Percent", "Percent", "Bar", "Off");
        arVertical = el("Armor").bool("Vertical", false, "Stack the pieces top to bottom");
        arHands = el("Armor").bool("Hand items", false, "Also show main hand and offhand");
        toHideZero = el("Totems").bool("Hide at 0", true, null);
        tgDistance = el("Target HUD").bool("Distance", true, null);
        tgBar = el("Target HUD").bool("Health bar", true, null);
        poTime = el("Potions").bool("Time", true, null);
        ksMouse = el("Keystrokes").bool("Mouse buttons", true, null);
        ksCps = el("Keystrokes").bool("CPS", true, null);
        ksSpace = el("Keystrokes").bool("Space bar", true, null);
        stRows = el("Staff list").num("Max rows", 12, 1, 30, 1);
        spElapsed = el("Spotify").bool("Elapsed time", true, null);
        muBar = el("Music player").bool("Progress bar", true, null);
    }

    private BoolSetting wmInfo, wmVersion, listInfo, listBars, coOther, coFacing, infSpeed, infPing, infFps,
            arVertical, arHands, toHideZero, tgDistance, tgBar, poTime, ksMouse, ksCps, ksSpace, spElapsed, muBar;
    private ModeSetting listSort, arDurability;
    private NumberSetting coDecimals, stRows;

    private Element el(String id) {
        for (Element e : elements) if (e.id.equals(id)) return e;
        throw new IllegalArgumentException(id);
    }

    /** Lets other modules (e.g. RegionMap) add their own HUD element. */
    public Element addElement(String id, boolean def, boolean right, boolean bottom, int offX, int offY, Painter painter) {
        Element e = new Element(id, def, right, bottom, offX, offY, painter);
        elements.add(e);
        return e;
    }

    // ---- element model -------------------------------------------------------------------------

    @FunctionalInterface
    public interface Painter {
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
            this.on = HudModule.this.bool(id, def);
            this.right = right;
            this.bottom = bottom;
            this.offX = offX;
            this.offY = offY;
            this.painter = painter;
        }

        /** Options for just this element, edited in the HUD editor. */
        public final List<Setting> opts = new ArrayList<>();

        public BoolSetting bool(String label, boolean def, String desc) {
            BoolSetting b = HudModule.this.bool(id + "/" + label, def);
            if (desc != null) b.desc(desc);
            return opt(b);
        }

        public NumberSetting num(String label, double def, double min, double max, double step) {
            return opt(HudModule.this.num(id + "/" + label, def, min, max, step));
        }

        public ModeSetting mode(String label, String def, String... modes) {
            return opt(HudModule.this.mode(id + "/" + label, def, modes));
        }

        private <S extends Setting> S opt(S s) {
            s.group = id;
            opts.add(s);
            return s;
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

    private static final String LAYOUT_KEY = "_layout";
    private static final java.util.Set<String> MOVED_IN_V2 = java.util.Set.of("Region map", "Staff list", "Spotify", "Music player", "Potions");

    @Override
    public JsonObject saveExtra() {
        JsonObject o = new JsonObject();
        o.addProperty(LAYOUT_KEY, 2);
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
        int version = json.has(LAYOUT_KEY) ? json.get(LAYOUT_KEY).getAsInt() : 1;
        for (Element e : elements) {
            if (!json.has(e.id) || !json.get(e.id).isJsonObject()) continue;
            // 3.3.1 moved these to new default spots (the region map overlapped the module list)
            if (version < 2 && MOVED_IN_V2.contains(e.id)) continue;
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
        for (Element e : elements) if (e.id.equals("Spotify")) {
            if (e.on.get()) dev.utilclient.music.Spotify.ensureStarted(); else dev.utilclient.music.Spotify.stop();
        }
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

    private void bg(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) { bg(g, x0, y0, x1, y1, cornerRadius()); }

    /**
     * Round: as round as the box allows (pills, circles) but capped at 9 on tall boxes so text never
     * spills over the curve. Rounded: the Corner radius slider. Square: 0.
     */
    public int cornerRadius() {
        return switch (shape.get()) {
            case "Round" -> 9;
            case "Square" -> 0;
            default -> radius.getInt();
        };
    }

    public boolean isRound() { return shape.is("Round"); }

    public int bgColor() { return Draw.alpha(0x0B0B10, backgrounds.get() ? bgOpacity.getInt() : 0); }

    public int accent(float offset) { return color(offset); }

    private void bg(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int radius) {
        if (!backgrounds.get()) return;
        int c = Draw.alpha(0x0B0B10, bgOpacity.getInt());
        if (shape.is("Square")) g.fill(x0, y0, x1, y1, c);
        else Draw.round(g, x0, y0, x1, y1, shape.is("Round") ? (y1 - y0 <= 16 ? 99 : 9) : radius, c);
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
        String a = txt("DonutWaffen"), b = wmVersion.get() ? " " + dev.utilclient.UtilClient.version() : "";
        String extra = "";
        if (wmInfo.get()) {
            int pg = ping();
            extra = "  |  " + RenderState.fps() + " fps" + (pg >= 0 ? "  |  " + pg + " ms" : "");
        }
        int w = Fonts.width(a, true) + Fonts.width(b) + Fonts.width(extra) + 12, h = 15;
        if (dry) return size(w, h);
        bg(g, x, y, x + w, y + h);
        // accent strip + the name in a per-letter gradient
        int inset = shape.is("Round") ? 9 : 5;
        Draw.round(g, x + inset, y + h - 2, x + w - inset, y + h - 1, 0, Draw.withAlpha(color(0f), 0.8f));
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
        for (Module m : shown) maxW = Math.max(maxW, Fonts.width(listLabel(m)) + 10);
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
            int w = Fonts.width(label) + 10;
            int slide = (int) ((1f - e) * (w + 4));
            int rx = alignRight ? x + maxW - w + slide : x - slide;
            int col = color(i * 0.06f);
            bg(g, rx, cy, rx + w, cy + rowH, shape.is("Round") ? 99 : Math.max(2, radius.getInt() / 2));
            if (listBars.get()) {
                // a little accent dot/bar on the outside edge (a dot when rows are pills)
                if (shape.is("Round")) {
                    int dx = alignRight ? rx + w - 4 : rx + 2;
                    Draw.round(g, dx, cy + 4, dx + 2, cy + 7, 1, col);
                } else if (alignRight) g.fill(rx + w - 1, cy, rx + w, cy + rowH, col);
                else g.fill(rx, cy, rx + 1, cy + rowH, col);
            }
            int tx = rx + (shape.is("Round") && listBars.get() && !alignRight ? 6 : 4);
            Fonts.text(g, name, tx, cy + 1, col);
            if (label.length() > name.length()) {
                Fonts.text(g, label.substring(name.length()), tx + Fonts.width(name), cy + 1, 0xFF9A9AAA);
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
        String f = "%." + coDecimals.getInt() + "f";
        String main = String.format(Locale.ROOT, "XYZ " + f + " " + f + " " + f, p.getX(), p.getY(), p.getZ());
        String other = String.format(Locale.ROOT, "%s " + f + " " + f, nether ? "Overworld" : "Nether", p.getX() * scale, p.getZ() * scale);
        String facing = "Facing " + facing(p.getYRot()) + axisHint(p.getYRot());
        List<String> ls = new ArrayList<>();
        if (coFacing.get()) ls.add(txt(facing));
        if (coOther.get() && mc.level.dimension() != Level.END) ls.add(txt(other));
        ls.add(txt(main));
        String[] lines = ls.toArray(new String[0]);
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
        List<String> ls = new ArrayList<>();
        if (infSpeed.get()) ls.add(txt(String.format(Locale.ROOT, "Speed %.1f b/s", speed)));
        if (infPing.get()) ls.add(txt("Ping " + ping + "ms"));
        if (infFps.get()) ls.add(txt("FPS " + RenderState.fps()));
        if (ls.isEmpty()) return size(0, 0);
        String[] lines = ls.toArray(new String[0]);
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
        List<ItemStack> items = new ArrayList<>();
        for (EquipmentSlot sl : ARMOR) items.add(mc.player.getItemBySlot(sl));
        if (arHands.get()) {
            items.add(mc.player.getMainHandItem());
            items.add(mc.player.getOffhandItem());
        }
        boolean vert = arVertical.get();
        boolean pct = arDurability.is("Percent"), bar = arDurability.is("Bar");
        // horizontal percentages need room: space the pieces by the widest label so they never overlap
        int widest = 0;
        if (pct) for (ItemStack it : items) if (it.isDamageableItem()) widest = Math.max(widest, Fonts.width(pctText(it)));
        int cell = vert ? 17 : Math.max(18, pct ? widest + 4 : 18);
        int extra = pct ? (vert ? widest + 4 : 10) : bar ? 3 : 0;
        int w = vert ? 16 + extra : items.size() * cell, h = vert ? items.size() * cell : 16 + extra;
        if (dry) return size(w, h);
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            int ix = vert ? x : x + i * cell + (cell - 16) / 2, iy = vert ? y + i * cell : y;
            if (s.isEmpty()) {
                if (editing) Draw.round(g, ix + 1, iy + 1, ix + 15, iy + 15, 99, 0x30FFFFFF);
                continue;
            }
            g.item(s, ix, iy);
            g.itemDecorations(mc.font, s, ix, iy);
            if (!s.isDamageableItem()) continue;
            float frac = 1f - s.getDamageValue() / (float) Math.max(1, s.getMaxDamage());
            int col = Draw.healthColor(frac);
            if (pct) {
                String t = pctText(s);
                if (vert) Fonts.text(g, t, ix + 18, iy + 4, col);
                else Fonts.text(g, t, ix + 8 - Fonts.width(t) / 2, iy + 17, col);
            } else if (bar) {
                int by = vert ? iy + 15 : iy + 17;
                Draw.pill(g, ix + 1, by, ix + 15, by + 2, 0x60000000);
                Draw.pill(g, ix + 1, by, ix + 1 + Math.max(2, (int) (14 * frac)), by + 2, col);
            }
        }
        return size(w, h);
    }

    private static String pctText(ItemStack s) {
        float frac = 1f - s.getDamageValue() / (float) Math.max(1, s.getMaxDamage());
        return Math.round(frac * 100) + "%";
    }

    private int[] totems(GuiGraphicsExtractor g, int x, int y, boolean right, boolean dry) {
        Minecraft mc = Minecraft.getInstance();
        int n = Inv.count(mc.player, Items.TOTEM_OF_UNDYING);
        if (n == 0 && !editing && toHideZero.get()) return size(0, 0);
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
        bg(g, x, y, x + w, y + h);
        Draw.round(g, x + 3, y + 8, x + 5, y + h - 8, 1, color(0f));
        String name = Fonts.fit(t.getName().getString(), w - 50);
        Fonts.text(g, name, x + 9, y + 4, 0xFFFFFFFF, true);
        float hp = t.getHealth() + t.getAbsorptionAmount();
        float frac = Math.min(1f, t.getHealth() / Math.max(1f, t.getMaxHealth()));
        String hpText = String.format(Locale.ROOT, "%.1f", hp);
        Fonts.text(g, hpText, x + w - 9 - Fonts.width(hpText), y + 4, Draw.healthColor(frac), true);
        String dist = String.format(Locale.ROOT, "%.1fm", mc.player.distanceTo(t));
        if (tgDistance.get()) Fonts.text(g, dist, x + 9, y + 15, 0xFFA0A0B0);
        if (tgBar.get()) {
            int barX = x + 9, barW = w - 18;
            Draw.pill(g, barX, y + 25, barX + barW, y + 29, 0xFF2A2A36);
            int fw = (int) (barW * frac);
            if (fw > 3) Draw.pill(g, barX, y + 25, barX + fw, y + 29, Draw.healthColor(frac));
            if (t.getAbsorptionAmount() > 0) {
                int aw = (int) (barW * Math.min(1f, t.getAbsorptionAmount() / Math.max(1f, t.getMaxHealth())));
                if (aw > 3) Draw.pill(g, barX, y + 23, barX + aw, y + 25, 0xFFFACC15);
            }
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
            lines.add(txt(poTime.get() ? name + " " + time : name));
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

    // ---- staff / music / spotify ---------------------------------------------------------------

    private int[] staff(GuiGraphicsExtractor g, int x, int y, boolean right, boolean dry) {
        StaffAlertModule sa = ModuleManager.STAFF_ALERT;
        List<StaffAlertModule.Staff> list = sa.isEnabled() ? sa.online() : List.of();
        if (list.isEmpty() && editing) list = List.of(new StaffAlertModule.Staff("ExampleAdmin", "Admin"), new StaffAlertModule.Staff("SomeMod", "Mod"));
        if (list.isEmpty()) return size(0, 0);
        String head = txt("Staff online");
        String count = String.valueOf(list.size());
        int rankW = 0, nameW = 0;
        for (var st : list) { rankW = Math.max(rankW, Fonts.width(txt(st.rank()))); nameW = Math.max(nameW, Fonts.width(st.name())); }
        int shown = Math.min(list.size(), stRows.getInt());
        int w = Math.max(Fonts.width(head, true) + Fonts.width(count) + 26, rankW + nameW + 22), h = 17 + shown * 10 + 3;
        if (dry) return size(w, h);
        bg(g, x, y, x + w, y + h);
        // pulsing red dot = live
        float pulse = 0.55f + 0.45f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 400.0));
        Draw.round(g, x + 6, y + 6, x + 10, y + 10, 2, Draw.withAlpha(0xFFFF4D5E, pulse));
        Fonts.text(g, head, x + 14, y + 4, 0xFFFFFFFF, true);
        int pw = Fonts.width(count) + 8;
        Draw.round(g, x + w - pw - 4, y + 3, x + w - 4, y + 13, 4, Draw.withAlpha(color(0f), 0.85f));
        Fonts.text(g, count, x + w - pw, y + 4, 0xFFFFFFFF);
        g.fill(x + 6, y + 15, x + w - 6, y + 16, 0x22FFFFFF);
        for (int i = 0; i < shown; i++) {
            var st = list.get(i);
            int ry = y + 18 + i * 10;
            Fonts.text(g, txt(st.rank()), x + 7, ry, rankColor(st.rank()));
            Fonts.text(g, st.name(), x + 13 + rankW, ry, 0xFFE0E0EA);
        }
        return size(w, h);
    }

    private static int rankColor(String rank) {
        String r = rank.toLowerCase(Locale.ROOT);
        if (r.contains("owner") || r.contains("admin") || r.contains("manager")) return 0xFFFF5A5A;
        if (r.contains("mod")) return 0xFF5AC8FF;
        if (r.contains("dev")) return 0xFFB57BFF;
        return 0xFF7BE38A;
    }

    private static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** Shared now-playing card: cover tile with equalizer, title, subtitle and an optional progress bar. */
    private int[] nowPlaying(GuiGraphicsExtractor g, int x, int y, boolean dry, String title, String sub, int tile,
                             boolean playing, float progress, String left, String rightTxt) {
        int textW = Math.max(110, Math.min(170, Math.max(Fonts.width(title, true), Fonts.width(sub)) + 4));
        int w = 8 + 26 + 7 + textW + 8, h = progress >= 0 ? 42 : 34;
        if (dry) return size(w, h);
        bg(g, x, y, x + w, y + h);
        int r = isRound() ? 99 : Math.max(3, cornerRadius() - 1);
        // cover tile
        int tx = x + 4, ty = y + 4;
        Draw.round(g, tx, ty, tx + 26, ty + 26, r, tile);
        Draw.gradientV(g, tx + 2, ty + 13, tx + 24, ty + 24, 0x00000000, 0x40000000);
        double t = System.currentTimeMillis() / 160.0;
        for (int i = 0; i < 4; i++) {
            int bh = playing ? 3 + (int) Math.round(9 * Math.abs(Math.sin(t + i * 1.4))) : 3;
            g.fill(tx + 6 + i * 4, ty + 20 - bh, tx + 8 + i * 4, ty + 20, 0xF0FFFFFF);
        }
        int lx = tx + 26 + 7;
        Fonts.text(g, Fonts.fit(title, textW - 8), lx, y + 6, 0xFFFFFFFF, true);
        Fonts.text(g, Fonts.fit(sub, textW), lx, y + 18, 0xFF9A9AB0);
        if (progress >= 0) {
            int by = y + 34, bx1 = x + w - 6;
            int bx0 = x + 6;
            Draw.round(g, bx0, by, bx1, by + 3, 1, 0x30FFFFFF);
            int fx = bx0 + (int) ((bx1 - bx0) * Math.max(0f, Math.min(1f, progress)));
            if (fx > bx0 + 1) Draw.round(g, bx0, by, fx, by + 3, 1, color(0f));
            if (left != null) {
                int tw = Fonts.width(rightTxt);
                Fonts.text(g, rightTxt, x + w - 6 - tw, y + 22, 0xFF7A7A90);
            }
        }
        return size(w, h);
    }

    private int[] spotify(GuiGraphicsExtractor g, int x, int y, boolean right, boolean dry) {
        String title, sub;
        boolean playing = dev.utilclient.music.Spotify.playing();
        if (playing) {
            title = dev.utilclient.music.Spotify.title();
            sub = dev.utilclient.music.Spotify.artist();
            if (sub.isEmpty()) sub = "Spotify";
        } else if (dev.utilclient.music.Spotify.running()) {
            title = "Paused";
            sub = "Spotify";
        } else if (editing) {
            title = "Song title";
            sub = "AloneByDeath";
        } else {
            return size(0, 0);
        }
        int[] r = nowPlaying(g, x, y, dry, title, sub, 0xFF1DB954, playing, -1, null, null);
        if (!dry && playing && spElapsed.get()) {
            String tag = playing ? time(System.currentTimeMillis() - dev.utilclient.music.Spotify.trackSince()) : "paused";
            Fonts.text(g, tag, x + r[0] - 6 - Fonts.width(tag), y + 18, 0xFF1DB954);
        }
        return r;
    }

    private int[] music(GuiGraphicsExtractor g, int x, int y, boolean right, boolean dry) {
        ModuleManager.MUSIC.sync();
        String now = dev.utilclient.music.MusicPlayer.nowPlaying();
        if (now.isEmpty() && !editing) return size(0, 0);
        boolean playing = dev.utilclient.music.MusicPlayer.playing();
        String title = now.isEmpty() ? "Song title" : now;
        String sub = (playing ? "AloneByDeath Player" : "Paused");
        long pos = dev.utilclient.music.MusicPlayer.positionMs(), len = dev.utilclient.music.MusicPlayer.lengthMs();
        float prog = len > 0 ? pos / (float) len : 0f;
        int tile = Draw.mix(color(0f), 0xFF000000, 0.15f);
        return nowPlaying(g, x, y, dry, title, sub, tile, playing, muBar.get() ? prog : -1, time(pos), time(pos) + " / " + time(len));
    }

    // ---- keystrokes -----------------------------------------------------------------------------

    private static final java.util.ArrayDeque<Long> LEFT = new java.util.ArrayDeque<>(), RIGHT = new java.util.ArrayDeque<>();

    /** From MouseHandlerMixin on every mouse press: feeds the CPS counters. */
    public static void onClick(int button) {
        long now = System.currentTimeMillis();
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) LEFT.addLast(now);
        else if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) RIGHT.addLast(now);
    }

    private static int cps(java.util.ArrayDeque<Long> q) {
        long cutoff = System.currentTimeMillis() - 1000;
        while (!q.isEmpty() && q.peekFirst() < cutoff) q.pollFirst();
        return q.size();
    }

    private void mouseKey(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int cps, boolean down) {
        key(g, x, y, w, h, "", down);
        int fg = down ? readableOn(color(0f)) : 0xFFFFFFFF;
        if (!ksCps.get()) { Fonts.centered(g, label, x + w / 2, y + h / 2 - 4, fg, true); return; }
        Fonts.centered(g, label, x + w / 2, y + 3, fg, true);
        Fonts.centered(g, cps + " CPS", x + w / 2, y + 13, down ? Draw.withAlpha(fg, 0.85f) : 0xFF9A9AB0, false);
    }

    private void key(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean down) {
        float t = 0f;
        if (down) t = 1f;
        int fill = down ? Draw.withAlpha(color(0f), 0.85f) : Draw.alpha(0x0B0B10, bgOpacity.getInt());
        if (!shape.is("Square")) Draw.round(g, x, y, x + w, y + h, shape.is("Round") ? 99 : Math.max(3, radius.getInt() - 1), fill);
        else g.fill(x, y, x + w, y + h, fill);
        Fonts.centered(g, label, x + w / 2, y + h / 2 - 4, down ? readableOn(fill) : 0xFFFFFFFF, false);
    }

    /** White text, unless the background is very light (e.g. the White colour mode). */
    static int readableOn(int bg) {
        int r = (bg >> 16) & 0xFF, gg = (bg >> 8) & 0xFF, b = bg & 0xFF;
        return (0.299 * r + 0.587 * gg + 0.114 * b) > 200 ? 0xFF16161C : 0xFFFFFFFF;
    }

    private int[] keystrokes(GuiGraphicsExtractor g, int x, int y, boolean right, boolean dry) {
        int k = 18, mk = ksCps.get() ? 24 : 18, gap = 2, w = k * 3 + gap * 2;
        int h = k * 2 + gap + (ksMouse.get() ? mk + gap : 0) + (ksSpace.get() ? 8 + gap : 0);
        if (dry) return size(w, h);
        Minecraft mc = Minecraft.getInstance();
        var o = mc.options;
        key(g, x + k + gap, y, k, k, "W", o.keyUp.isDown());
        key(g, x, y + k + gap, k, k, "A", o.keyLeft.isDown());
        key(g, x + k + gap, y + k + gap, k, k, "S", o.keyDown.isDown());
        key(g, x + (k + gap) * 2, y + k + gap, k, k, "D", o.keyRight.isDown());
        int cy = y + (k + gap) * 2;
        if (ksMouse.get()) {
            // left half = attack (LMB), right half = use (RMB), each with its own CPS
            int half = (w - gap) / 2;
            mouseKey(g, x, cy, half, mk, "LMB", cps(LEFT), o.keyAttack.isDown());
            mouseKey(g, x + half + gap, cy, w - half - gap, mk, "RMB", cps(RIGHT), o.keyUse.isDown());
            cy += mk + gap;
        }
        if (ksSpace.get()) key(g, x, cy, w, 8, "", o.keyJump.isDown());
        return size(w, h);
    }

}

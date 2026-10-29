package dev.utilclient.module;

import com.mojang.blaze3d.platform.InputConstants;
import dev.utilclient.core.Chat;
import dev.utilclient.core.Keys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ModuleManager {
    private static final Logger LOG = Logger.getLogger("alonebydeathclient");

    // client
    public static final ClickGuiModule CLICK_GUI = new ClickGuiModule();
    public static final HudModule HUD = new HudModule();
    public static final NotificationsModule NOTIFICATIONS = new NotificationsModule();
    // combat
    public static final AutoHitModule AUTO_HIT = new AutoHitModule();
    public static final TriggerbotModule TRIGGERBOT = new TriggerbotModule();
    public static final AimbotModule AIMBOT = new AimbotModule();
    public static final CriticalsModule CRITICALS = new CriticalsModule();
    public static final AutoTotemModule AUTO_TOTEM = new AutoTotemModule();
    public static final AutoCrystalModule AUTO_CRYSTAL = new AutoCrystalModule();
    public static final AutoAnchorModule AUTO_ANCHOR = new AutoAnchorModule();
    public static final SurroundModule SURROUND = new SurroundModule();
    public static final AutoArmorModule AUTO_ARMOR = new AutoArmorModule();
    public static final AutoWeaponModule AUTO_WEAPON = new AutoWeaponModule();
    // movement
    public static final SprintModule SPRINT = new SprintModule();
    public static final AutoWalkModule AUTO_WALK = new AutoWalkModule();
    public static final StepModule STEP = new StepModule();
    public static final NoFallModule NO_FALL = new NoFallModule();
    public static final VelocityModule VELOCITY = new VelocityModule();
    // render
    public static final EspModule ESP = new EspModule();
    public static final NametagsModule NAMETAGS = new NametagsModule();
    public static final TracersModule TRACERS = new TracersModule();
    public static final BlockEspModule BLOCK_ESP = new BlockEspModule();
    public static final HoleEspModule HOLE_ESP = new HoleEspModule();
    public static final TrackerModule TRACKER = new TrackerModule();
    public static final XrayModule XRAY = new XrayModule();
    public static final FullbrightModule FULLBRIGHT = new FullbrightModule();
    public static final ZoomModule ZOOM = new ZoomModule();
    public static final NoRenderModule NO_RENDER = new NoRenderModule();
    // player
    public static final AutoMineModule AUTO_MINE = new AutoMineModule();
    public static final AutoPlaceModule AUTO_PLACE = new AutoPlaceModule();
    public static final AutoToolModule AUTO_TOOL = new AutoToolModule();
    public static final AutoEatModule AUTO_EAT = new AutoEatModule();
    public static final FastPlaceModule FAST_PLACE = new FastPlaceModule();
    public static final AutoRespawnModule AUTO_RESPAWN = new AutoRespawnModule();
    // misc
    public static final FakePlayerModule FAKE_PLAYER = new FakePlayerModule();
    public static final MiddleClickFriendModule MIDDLE_CLICK_FRIEND = new MiddleClickFriendModule();
    public static final AntiAfkModule ANTI_AFK = new AntiAfkModule();

    public static final List<Module> ALL = List.of(
            CLICK_GUI, HUD, NOTIFICATIONS,
            AUTO_HIT, TRIGGERBOT, AIMBOT, CRITICALS, AUTO_TOTEM, AUTO_CRYSTAL, AUTO_ANCHOR,
            SURROUND, AUTO_ARMOR, AUTO_WEAPON,
            SPRINT, AUTO_WALK, STEP, NO_FALL, VELOCITY,
            ESP, NAMETAGS, TRACERS, BLOCK_ESP, HOLE_ESP, TRACKER, XRAY, FULLBRIGHT, ZOOM, NO_RENDER,
            AUTO_MINE, AUTO_PLACE, AUTO_TOOL, AUTO_EAT, FAST_PLACE, AUTO_RESPAWN,
            FAKE_PLAYER, MIDDLE_CLICK_FRIEND, ANTI_AFK);

    static {
        for (Module m : ALL) m.addMeta();
    }

    private static boolean dirty;
    private static Module bindTarget;
    private static final java.util.Map<Module, Long> LAST_REPORT = new java.util.HashMap<>();

    private ModuleManager() {}

    // ---- lookup -----------------------------------------------------------------------------

    public static List<Module> byCategory(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : ALL) if (m.category == c) out.add(m);
        return out;
    }

    public static Module find(String name) {
        String n = name.replace(" ", "").toLowerCase(Locale.ROOT);
        for (Module m : ALL) if (m.name.replace(" ", "").toLowerCase(Locale.ROOT).equals(n)) return m;
        return null;
    }

    public static void applyDefaults() {
        for (Module m : ALL) if (m.enabledByDefault) m.setEnabled(true, false);
    }

    // ---- dirty flag for autosave ---------------------------------------------------------------

    public static void markDirty() { dirty = true; }

    public static boolean consumeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    // ---- ticking & rendering ---------------------------------------------------------------------

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        for (Module m : ALL) {
            if (!m.isEnabled()) continue;
            try {
                m.onTick(mc);
            } catch (Throwable t) {
                report(m, t);
            }
        }
    }

    public static void renderHud(GuiGraphicsExtractor g, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        for (Module m : ALL) {
            if (m == HUD || !m.isEnabled()) continue;
            try {
                m.renderHud(g, partialTick);
            } catch (Throwable t) {
                report(m, t);
            }
        }
        // HUD text last so world overlays (ESP boxes, tracers) never cover it
        if (HUD.isEnabled()) {
            try {
                HUD.renderHud(g, partialTick);
            } catch (Throwable t) {
                report(HUD, t);
            }
        }
    }

    /** A module threw: log it, switch it off and tell the player instead of crashing the game. */
    public static void report(Module m, Throwable t) {
        long now = System.currentTimeMillis();
        Long last = LAST_REPORT.get(m);
        if (last != null && now - last < 10_000L) return;   // never spam chat from a per-frame error
        LAST_REPORT.put(m, now);
        LOG.log(Level.WARNING, "Module " + m.name + " failed", t);
        if (!m.isAlwaysOn()) m.setEnabled(false, false);
        String msg = t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "");
        Chat.error(m.name + " was turned off after an error (" + msg + "). Details are in latest.log.");
    }

    public static void notifyToggle(Module m) {
        if (NOTIFICATIONS.isEnabled()) NOTIFICATIONS.onToggle(m);
    }

    // ---- key binds ------------------------------------------------------------------------------

    public static void startBinding(Module m) {
        bindTarget = m;
        Chat.info("Press a key to bind " + m.name + " (Esc / Backspace = unbind).");
    }

    public static boolean isBinding() { return bindTarget != null; }

    /** Called from the keyboard mixin for every key event. */
    public static void onKey(int key, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (key == InputConstants.UNKNOWN.getValue()) return;

        if (bindTarget != null && action == InputConstants.PRESS) {
            Module m = bindTarget;
            bindTarget = null;
            if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) {
                m.key = -1;
                Chat.info(m.name + " unbound.");
            } else {
                m.key = key;
                Chat.info(m.name + " bound to " + Keys.name(key) + ".");
            }
            markDirty();
            return;
        }

        if (mc.gui == null || mc.gui.screen() != null || mc.player == null) return;
        for (Module m : ALL) {
            if (m.key != key || m.isAlwaysOn()) continue;
            boolean hold = m.bindMode != null && m.bindMode.is("Hold");
            if (action == InputConstants.PRESS) {
                if (hold) m.setEnabled(true);
                else m.toggle();
            } else if (action == InputConstants.RELEASE && hold) {
                m.setEnabled(false);
            }
        }
    }

    /** Turn off every module except the client settings ones. */
    public static int panic() {
        int n = 0;
        for (Module m : ALL) {
            if (m.category == Category.CLIENT || !m.isEnabled() || m.isAlwaysOn()) continue;
            m.setEnabled(false, false);
            n++;
        }
        return n;
    }
}

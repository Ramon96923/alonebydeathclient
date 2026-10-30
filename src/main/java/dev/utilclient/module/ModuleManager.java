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
    public static final AutoClickerModule AUTO_CLICKER = new AutoClickerModule();
    public static final AimbotModule AIMBOT = new AimbotModule();
    public static final CriticalsModule CRITICALS = new CriticalsModule();
    public static final HitboxModule HITBOX = new HitboxModule();
    public static final OldPvpModule OLD_PVP = new OldPvpModule();
    public static final MaceModule MACE = new MaceModule();
    public static final SpearModule SPEAR = new SpearModule();
    public static final AutoTotemModule AUTO_TOTEM = new AutoTotemModule();
    public static final AutoCrystalModule AUTO_CRYSTAL = new AutoCrystalModule();
    public static final AutoAnchorModule AUTO_ANCHOR = new AutoAnchorModule();
    public static final SurroundModule SURROUND = new SurroundModule();
    public static final AutoArmorModule AUTO_ARMOR = new AutoArmorModule();
    public static final AutoWeaponModule AUTO_WEAPON = new AutoWeaponModule();
    public static final AutoTrapModule AUTO_TRAP = new AutoTrapModule();
    public static final SelfTrapModule SELF_TRAP = new SelfTrapModule();
    public static final HoleFillerModule HOLE_FILLER = new HoleFillerModule();
    public static final AutoLogModule AUTO_LOG = new AutoLogModule();
    public static final ShieldBreakerModule SHIELD_BREAKER = new ShieldBreakerModule();
    public static final AutoPotModule AUTO_POT = new AutoPotModule();
    public static final AntiBotModule ANTI_BOT = new AntiBotModule();
    // movement
    public static final SprintModule SPRINT = new SprintModule();
    public static final AutoWalkModule AUTO_WALK = new AutoWalkModule();
    public static final StepModule STEP = new StepModule();
    public static final NoFallModule NO_FALL = new NoFallModule();
    public static final VelocityModule VELOCITY = new VelocityModule();
    public static final ElytraFlyModule ELYTRA_FLY = new ElytraFlyModule();
    public static final FlightModule FLIGHT = new FlightModule();
    public static final SpeedModule SPEED = new SpeedModule();
    public static final JesusModule JESUS = new JesusModule();
    public static final ScaffoldModule SCAFFOLD = new ScaffoldModule();
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
    public static final LogoutSpotsModule LOGOUT_SPOTS = new LogoutSpotsModule();
    public static final BreadcrumbsModule BREADCRUMBS = new BreadcrumbsModule();
    public static final FreecamModule FREECAM = new FreecamModule();
    public static final StorageEspModule STORAGE_ESP = new StorageEspModule();
    public static final GlintColorModule GLINT_COLOR = new GlintColorModule();
    // player
    public static final AutoMineModule AUTO_MINE = new AutoMineModule();
    public static final AutoPlaceModule AUTO_PLACE = new AutoPlaceModule();
    public static final AutoToolModule AUTO_TOOL = new AutoToolModule();
    public static final AutoEatModule AUTO_EAT = new AutoEatModule();
    public static final FastPlaceModule FAST_PLACE = new FastPlaceModule();
    public static final AutoRespawnModule AUTO_RESPAWN = new AutoRespawnModule();
    public static final AutoMendModule AUTO_MEND = new AutoMendModule();
    public static final ChestStealerModule CHEST_STEALER = new ChestStealerModule();
    // misc
    public static final FakePlayerModule FAKE_PLAYER = new FakePlayerModule();
    public static final MiddleClickFriendModule MIDDLE_CLICK_FRIEND = new MiddleClickFriendModule();
    public static final AntiAfkModule ANTI_AFK = new AntiAfkModule();
    public static final AutoReconnectModule AUTO_RECONNECT = new AutoReconnectModule();
    public static final VisualRangeModule VISUAL_RANGE = new VisualRangeModule();
    public static final PopCounterModule POP_COUNTER = new PopCounterModule();
    public static final MacrosModule MACROS = new MacrosModule();

    public static final List<Module> ALL = List.of(
            CLICK_GUI, HUD, NOTIFICATIONS,
            AUTO_HIT, TRIGGERBOT, AUTO_CLICKER, AIMBOT, CRITICALS, HITBOX, OLD_PVP, MACE, SPEAR,
            AUTO_TOTEM, AUTO_CRYSTAL, AUTO_ANCHOR, SURROUND, AUTO_ARMOR, AUTO_WEAPON,
            AUTO_TRAP, SELF_TRAP, HOLE_FILLER, AUTO_LOG, SHIELD_BREAKER, AUTO_POT, ANTI_BOT,
            SPRINT, AUTO_WALK, STEP, NO_FALL, VELOCITY, ELYTRA_FLY, FLIGHT, SPEED, JESUS, SCAFFOLD,
            ESP, NAMETAGS, TRACERS, BLOCK_ESP, HOLE_ESP, TRACKER, XRAY, FULLBRIGHT, ZOOM, NO_RENDER,
            LOGOUT_SPOTS, BREADCRUMBS, FREECAM, STORAGE_ESP, GLINT_COLOR,
            AUTO_MINE, AUTO_PLACE, AUTO_TOOL, AUTO_EAT, FAST_PLACE, AUTO_RESPAWN, AUTO_MEND, CHEST_STEALER,
            FAKE_PLAYER, MIDDLE_CLICK_FRIEND, ANTI_AFK, AUTO_RECONNECT, VISUAL_RANGE, POP_COUNTER, MACROS);

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

    public static void renderWorld(dev.utilclient.render.WorldRender.Ctx ctx) {
        for (Module m : ALL) {
            if (!m.isEnabled()) continue;
            try {
                m.renderWorld(ctx);
            } catch (Throwable t) {
                report(m, t);
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

        if (MACROS.capture(key, action)) return;   // ".macro add": this key press binds a macro

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
        if (action == InputConstants.PRESS) MACROS.onKeyPress(key);
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

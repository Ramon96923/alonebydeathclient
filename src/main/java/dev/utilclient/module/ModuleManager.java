package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

public final class ModuleManager {
    // combat
    public static final AutoHitModule AUTO_HIT = new AutoHitModule();
    public static final TriggerbotModule TRIGGERBOT = new TriggerbotModule();
    public static final AimbotModule AIMBOT = new AimbotModule();
    public static final AutoTotemModule AUTO_TOTEM = new AutoTotemModule();
    public static final AutoCrystalModule AUTO_CRYSTAL = new AutoCrystalModule();
    public static final AutoAnchorModule AUTO_ANCHOR = new AutoAnchorModule();
    // render
    public static final EspModule ESP = new EspModule();
    public static final BlockEspModule BLOCK_ESP = new BlockEspModule();
    public static final TracersModule TRACERS = new TracersModule();
    public static final TrackerModule TRACKER = new TrackerModule();
    public static final XrayModule XRAY = new XrayModule();
    public static final FullbrightModule FULLBRIGHT = new FullbrightModule();
    public static final HudModule HUD = new HudModule();
    // player
    public static final AutoMineModule AUTO_MINE = new AutoMineModule();
    public static final AutoPlaceModule AUTO_PLACE = new AutoPlaceModule();
    public static final SprintModule SPRINT = new SprintModule();
    public static final AutoWalkModule AUTO_WALK = new AutoWalkModule();
    public static final AutoRespawnModule AUTO_RESPAWN = new AutoRespawnModule();

    public static final List<Module> ALL = List.of(
            AUTO_HIT, TRIGGERBOT, AIMBOT, AUTO_TOTEM, AUTO_CRYSTAL, AUTO_ANCHOR,
            ESP, BLOCK_ESP, TRACERS, TRACKER, XRAY, FULLBRIGHT, HUD,
            AUTO_MINE, AUTO_PLACE, SPRINT, AUTO_WALK, AUTO_RESPAWN);

    private ModuleManager() {}

    public static List<Module> byCategory(Category c) {
        return ALL.stream().filter(m -> m.category == c).toList();
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        for (Module m : ALL) if (m.isEnabled()) m.onTick(mc);
    }

    public static void renderHud(GuiGraphicsExtractor g, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        for (Module m : ALL) if (m.isEnabled()) m.renderHud(g, partialTick);
    }
}

package dev.utilclient.module;

import net.minecraft.client.Minecraft;

import java.util.List;

public final class ModuleManager {
    public static final XrayModule XRAY = new XrayModule();
    public static final EspModule ESP = new EspModule();
    public static final TrackerModule TRACKER = new TrackerModule();
    public static final AutoHitModule AUTO_HIT = new AutoHitModule();
    public static final AutoMineModule AUTO_MINE = new AutoMineModule();
    public static final AutoPlaceModule AUTO_PLACE = new AutoPlaceModule();
    public static final FullbrightModule FULLBRIGHT = new FullbrightModule();

    public static final List<Module> ALL =
            List.of(XRAY, ESP, TRACKER, AUTO_HIT, AUTO_MINE, AUTO_PLACE, FULLBRIGHT);

    private ModuleManager() {}

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        for (Module m : ALL) if (m.isEnabled()) m.onTick(mc);
    }
}

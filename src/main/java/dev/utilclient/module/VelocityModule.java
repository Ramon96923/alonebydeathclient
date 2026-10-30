package dev.utilclient.module;

import net.minecraft.world.phys.Vec3;

/** Cuts knockback from hits and explosions down to a percentage. Applied by the packet mixins. */
public final class VelocityModule extends Module {
    private final NumberSetting horizontal = num("Horizontal", 85, 0, 100, 5).unit("%").desc("Knockback you still take. 0 is easy for anticheats to spot; 80-90 is safer");
    private final NumberSetting vertical = num("Vertical", 100, 0, 100, 5).unit("%");
    private final BoolSetting explosions = bool("Explosions", true).desc("Also reduce crystal/TNT knockback");

    private Vec3 before;

    public VelocityModule() { super("Velocity", "Take less knockback", Category.MOVEMENT); }

    @Override public String info() { return "H" + horizontal.getInt() + "% V" + vertical.getInt() + "%"; }

    public void remember(Vec3 motion) { before = isEnabled() ? motion : null; }

    public void applyAfterPacket(boolean explosion) {
        Vec3 b = before;
        before = null;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (b == null || mc.player == null || !isEnabled() || !dev.utilclient.UtilClient.isMainThread()) return;
        if (explosion && !explosions.get()) return;
        mc.player.setDeltaMovement(scale(b, mc.player.getDeltaMovement()));
    }

    private Vec3 scale(Vec3 before, Vec3 after) {
        double h = horizontal.get() / 100.0, v = vertical.get() / 100.0;
        return new Vec3(
                before.x + (after.x - before.x) * h,
                before.y + (after.y - before.y) * v,
                before.z + (after.z - before.z) * h);
    }
}

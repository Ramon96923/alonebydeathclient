package dev.utilclient.module;

import dev.utilclient.util.Inv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

/**
 * Maximises mace damage.
 * <ul>
 *   <li><b>Spoof height</b>: when you hit with a mace, tells the server you just fell a long way, so even a hit
 *       from the ground is a full smash attack. The packet sequence is adapted from Wurst Client's MaceDMG
 *       (GPL-3.0): four on-the-spot position packets raise the per-tick movement allowance, then one jump up
 *       and one back down. A successful smash cancels your own fall damage.</li>
 *   <li><b>Auto swap</b>: while you're really falling, holds a mace for the hit and swaps back afterwards.</li>
 * </ul>
 * Spoofing sends fake movement, so like Criticals' packet modes it can get you kicked on strict servers.
 */
public final class MaceModule extends Module {
    private final BoolSetting spoof = bool("Spoof height", true).desc("Every mace hit becomes a full-height smash");
    private final NumberSetting height = num("Height", 22, 2, 22, 1).unit("b")
            .desc("Pretend fall height (22 is the most the server allows)").visible(spoof::get);
    private final BoolSetting autoSwap = bool("Auto swap", true).desc("Hold a mace automatically while falling");
    private final NumberSetting minFall = num("Min fall", 1.5, 0.5, 10.0, 0.5).unit("b")
            .desc("Swap once you've fallen this far").visible(autoSwap::get);
    private final NumberSetting swapBack = num("Swap back", 2, 0, 20, 1).unit("t")
            .desc("Ticks before swapping back to your weapon (0 = stay)").visible(autoSwap::get);

    private int prevSlot = -1;
    private int backTimer;

    public MaceModule() { super("Mace", "Full mace smash damage, with auto-swap", Category.COMBAT); }

    @Override public String info() { return spoof.get() ? height.getInt() + "b" : null; }

    @Override protected void onDisable() { restore(Minecraft.getInstance().player); }

    private boolean falling(LocalPlayer p) {
        return p.fallDistance >= minFall.get() && !p.onGround() && !p.isFallFlying();
    }

    /** Called from the attack mixin HEAD, before the attack is sent. */
    public void beforeAttack(Entity target) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p.connection == null || !(target instanceof LivingEntity)) return;

        if (autoSwap.get() && falling(p) && !p.getMainHandItem().is(Items.MACE)) {
            int slot = Inv.hotbar(p, Items.MACE);
            if (slot >= 0) {
                if (prevSlot < 0) prevSlot = Inv.selected(p);
                Inv.select(p, slot);
            }
        }
        if (prevSlot >= 0) backTimer = swapBack.getInt();

        // Only spoof when the hit will certainly land: a rejected hit would leave the fake fall
        // on the server and you'd take the fall damage yourself.
        if (!spoof.get() || !p.getMainHandItem().is(Items.MACE) || p.isFallFlying()) return;
        if (p.distanceTo(target) > 3.5 || !p.hasLineOfSight(target)) return;
        double h = Math.min(height.get(), Math.sqrt(500) - 0.01);
        if (h > 9.5) for (int i = 0; i < 4; i++) sendY(p, 0);   // raise the server's movement allowance
        sendY(p, h);
        sendY(p, 0);
    }

    private static void sendY(LocalPlayer p, double up) {
        p.connection.send(new ServerboundMovePlayerPacket.Pos(
                p.getX(), p.getY() + up, p.getZ(), false, p.horizontalCollision));
    }

    @Override
    public void onTick(Minecraft mc) {
        if (prevSlot < 0 || swapBack.getInt() == 0) return;
        if (falling(mc.player)) return;   // still diving: keep the mace out
        if (backTimer-- <= 0) restore(mc.player);
    }

    private void restore(LocalPlayer p) {
        if (p != null && prevSlot >= 0 && prevSlot < 9) Inv.select(p, prevSlot);
        prevSlot = -1;
        backTimer = 0;
    }
}

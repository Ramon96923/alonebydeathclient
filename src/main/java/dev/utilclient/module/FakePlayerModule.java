package dev.utilclient.module;

import com.mojang.authlib.GameProfile;
import dev.utilclient.core.Chat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * Spawns a client-side copy of you to test ESP, Nametags, AutoHit, AutoCrystal placement and so on.
 * It only exists on your screen; the server doesn't know about it, so hits do no damage.
 */
public final class FakePlayerModule extends Module {
    private static final int FAKE_ID = -1337;
    private final NumberSetting health = num("Health", 20, 1, 36, 1);
    private final BoolSetting copyInventory = bool("Copy inventory", true).desc("Wear your armour and items");

    private RemotePlayer fake;

    public FakePlayerModule() { super("FakePlayer", "Spawn a test dummy (client side)", Category.MISC); }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { setEnabled(false, false); return; }
        fake = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "FakePlayer"));
        fake.copyPosition(p);
        fake.yHeadRot = p.yHeadRot;
        fake.yBodyRot = p.yBodyRot;
        fake.setId(FAKE_ID);
        float hp = health.getFloat();
        fake.setHealth(Math.min(20f, hp));
        if (hp > 20f) fake.setAbsorptionAmount(hp - 20f);
        if (copyInventory.get()) fake.getInventory().replaceWith(p.getInventory());
        mc.level.addEntity(fake);
        Chat.info("Fake player spawned. Turn the module off to remove it.");
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (fake != null && mc.level != null) {
            mc.level.removeEntity(fake.getId(), Entity.RemovalReason.DISCARDED);
        }
        fake = null;
    }

    @Override
    public void onTick(Minecraft mc) {
        // the dummy belongs to one world; if you change dimension or server, switch off cleanly
        if (fake != null && (fake.level() != mc.level || fake.isRemoved())) {
            fake = null;
            setEnabled(false);
        }
    }
}

package dev.utilclient.module;

import net.minecraft.client.Minecraft;

/**
 * Your own field of view, past the vanilla 30-110 range if you like, with or without the sprint/speed
 * "breathing" effect, and a separate FOV for your hand and held items.
 */
public final class CustomFovModule extends Module {
    private final NumberSetting fov = num("FOV", 100, 30, 170, 1).unit("°");
    private final ModeSetting dynamic = mode("Dynamic FOV", "Keep", "Keep", "Off", "Subtle")
            .desc("Keep: sprinting/speed still widen it. Off: never changes. Subtle: half the effect");
    private final NumberSetting hand = num("Hand FOV", 70, 0, 120, 1).unit("°")
            .desc("FOV for your hand and held item (0 = leave vanilla)");

    public CustomFovModule() { super("CustomFOV", "Field of view", Category.RENDER); }

    @Override public String info() { return fov.getInt() + "°"; }

    /** From Camera.calculateFov (before Zoom). */
    public float apply(float vanilla) {
        float base = Minecraft.getInstance().options.fov().get();
        float ratio = base <= 0 ? 1f : vanilla / base;   // the dynamic part vanilla added (sprint, speed, bow...)
        return switch (dynamic.get()) {
            case "Off" -> fov.getFloat();
            case "Subtle" -> fov.getFloat() * (1f + (ratio - 1f) * 0.5f);
            default -> fov.getFloat() * ratio;
        };
    }

    /** From Camera.calculateHudFov. */
    public float applyHand(float vanilla) { return hand.get() <= 0 ? vanilla : hand.getFloat(); }
}

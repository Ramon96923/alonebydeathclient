package dev.utilclient.module;

/** Optifine-style zoom. Bind it to a key: by default it zooms while the key is held. */
public final class ZoomModule extends Module {
    private final NumberSetting factor = num("Zoom", 4, 1.5, 15, 0.5).unit("x");
    private final BoolSetting smooth = bool("Smooth", true);

    private float current = 1f;
    private long last;

    public ZoomModule() {
        super("Zoom", "Zoom in like a spyglass", Category.RENDER);
        defaultBindMode = "Hold";
    }

    @Override protected void onEnable() { last = 0; }

    @Override protected void onDisable() { current = 1f; }

    /** Called from the camera FOV hook every frame. */
    public float apply(float fov) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0f : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        float target = factor.getFloat();
        current = smooth.get() ? current + (target - current) * Math.min(1f, dt * 12f) : target;
        return fov / Math.max(1f, current);
    }
}

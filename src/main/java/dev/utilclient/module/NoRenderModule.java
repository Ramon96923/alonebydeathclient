package dev.utilclient.module;

/** Hides distracting overlays and effects. */
public final class NoRenderModule extends Module {
    private final BoolSetting hurtCam = bool("Hurt camera", true).desc("No screen shake when hit");
    private final BoolSetting fire = bool("Fire overlay", true);
    private final BoolSetting water = bool("Water overlay", false);
    private final BoolSetting vignette = bool("Vignette", true);
    private final BoolSetting portal = bool("Portal overlay", true);
    private final BoolSetting potionIcons = bool("Potion icons", false);
    private final BoolSetting totem = bool("Totem animation", false).desc("Hide the big totem pop on screen");

    public NoRenderModule() { super("NoRender", "Hide overlays like fire and hurt cam", Category.RENDER); }

    public boolean noHurtCam() { return isEnabled() && hurtCam.get(); }
    public boolean noFire() { return isEnabled() && fire.get(); }
    public boolean noWaterOverlay() { return isEnabled() && water.get(); }
    public boolean noVignette() { return isEnabled() && vignette.get(); }
    public boolean noPortal() { return isEnabled() && portal.get(); }
    public boolean noPotionIcons() { return isEnabled() && potionIcons.get(); }
    public boolean noTotemAnimation() { return isEnabled() && totem.get(); }
}

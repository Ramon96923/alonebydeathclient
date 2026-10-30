package dev.utilclient.module;

import dev.utilclient.util.Draw;

/** Theme and behaviour of the click GUI. Always on; its settings are the customisation menu. */
public final class ClickGuiModule extends Module {
    public final ColorSetting accent = color("Accent", 0xFF8B5CF6).desc("Main theme colour");
    public final ModeSetting accentMode = mode("Accent mode", "Static", "Static", "Gradient", "Rainbow")
            .desc("Static colour, a gradient between two colours, or a rainbow");
    public final ColorSetting accent2 = color("Second colour", 0xFF22D3EE)
            .desc("Other end of the gradient").visible(() -> accentMode.is("Gradient"));
    public final ModeSetting headerStyle = mode("Header style", "Filled", "Filled", "Underline", "Minimal")
            .desc("Filled (Kami style) or dark with an accent underline (Lambda style)");
    public final NumberSetting scale = num("GUI scale", 0.85, 0.5, 1.3, 0.05).desc("Shrink or grow the whole menu");
    public final NumberSetting radius = num("Corner radius", 4, 0, 8, 1).desc("How rounded panels and buttons are");
    public final NumberSetting panelWidth = num("Panel width", 100, 80, 170, 2);
    public final NumberSetting maxHeight = num("Max panel height", 300, 120, 600, 10).desc("Taller panels scroll");
    public final NumberSetting opacity = num("Panel opacity", 225, 60, 255, 5);
    public final ModeSetting background = mode("Background", "Dim", "Dim", "Blur", "None").desc("What happens behind the GUI");
    public final BoolSetting customFont = bool("Custom font", true).desc("Use a bundled font instead of Minecraft's (also: .font off)");
    public final ModeSetting font = mode("Font", "Inter", "Inter", "Poppins", "Manrope", "JetBrains Mono", "Outfit")
            .desc("Which bundled font the menus use").visible(customFont::get);
    public final NumberSetting fontOffset = num("Font Y offset", 0, -2, 2, 0.5).desc("Nudge the custom font up/down")
            .visible(customFont::get);
    public final BoolSetting textShadow = bool("Text shadow", false);
    public final NumberSetting animSpeed = num("Animation speed", 1.0, 0, 3, 0.1).desc("0 turns animations off");
    public final BoolSetting descriptions = bool("Descriptions", true).desc("Show what the hovered option does");
    public final BoolSetting showBinds = bool("Show binds", true).desc("Show each module's key on its row");
    public final BoolSetting pauseGame = bool("Pause game", false).desc("Pause singleplayer while the GUI is open");
    public final BoolSetting mainMenu = bool("Main menu card", true).desc("AloneByDeath card with GUI and Spotify buttons on the title screen");

    public ClickGuiModule() {
        super("ClickGUI", "Look and feel of this menu", Category.CLIENT);
        alwaysOn = true;
    }

    /** Accent colour for a position (0-1) along a gradient/rainbow. */
    public int accent(float offset) {
        return switch (accentMode.get()) {
            case "Rainbow" -> Draw.hsv((System.currentTimeMillis() % 5000L) / 5000f + offset, 0.55f, 1f);
            case "Gradient" -> {
                float t = (float) (0.5 + 0.5 * Math.sin((System.currentTimeMillis() / 900.0) + offset * Math.PI * 2));
                yield Draw.mix(accent.get() | 0xFF000000, accent2.get() | 0xFF000000, t);
            }
            default -> accent.get() | 0xFF000000;
        };
    }

    public int panelAlpha() { return opacity.getInt(); }
}

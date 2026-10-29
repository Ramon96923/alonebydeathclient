package dev.utilclient.gui;

import dev.utilclient.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Text drawing for the client. Uses the bundled Outfit TrueType font (assets/alonebydeathclient/font)
 * through Minecraft's own font system, and falls back to the vanilla font if anything is off.
 */
public final class Fonts {
    private static final Logger LOG = Logger.getLogger("alonebydeathclient");

    private static Style regular, bold;
    private static boolean initDone, broken;
    private static int checks;

    private Fonts() {}

    private static Font font() { return Minecraft.getInstance().font; }

    /** True when text should use the custom font. */
    public static boolean custom() {
        if (broken || !ModuleManager.CLICK_GUI.customFont.get()) return false;
        if (!initDone) init();
        return regular != null;
    }

    private static void init() {
        initDone = true;
        try {
            regular = styleFor(Identifier.fromNamespaceAndPath("alonebydeathclient", "ui"));
            bold = styleFor(Identifier.fromNamespaceAndPath("alonebydeathclient", "ui_bold"));
        } catch (Throwable t) {
            LOG.warning("Custom font unavailable, using the vanilla font: " + t);
            regular = bold = null;
        }
    }

    /** Builds Style.withFont(...) for whichever font-description type this Minecraft version uses. */
    private static Style styleFor(Identifier id) throws ReflectiveOperationException {
        Method withFont = null;
        for (Method m : Style.class.getMethods()) {
            if (m.getName().equals("withFont") && m.getParameterCount() == 1) withFont = m;
        }
        if (withFont == null) throw new NoSuchMethodException("Style.withFont");
        Class<?> type = withFont.getParameterTypes()[0];
        Object arg = null;
        if (type.isInstance(id)) {
            arg = id;
        } else {
            List<Class<?>> candidates = new ArrayList<>(List.of(type.getDeclaredClasses()));
            Class<?>[] permitted = type.getPermittedSubclasses();
            if (permitted != null) candidates.addAll(List.of(permitted));
            for (Class<?> c : candidates) {
                if (!type.isAssignableFrom(c)) continue;
                try {
                    Constructor<?> k = c.getConstructor(Identifier.class);
                    arg = k.newInstance(id);
                    break;
                } catch (NoSuchMethodException ignored) {
                    // not the resource variant
                }
            }
        }
        if (arg == null) throw new NoSuchMethodException("no font description for " + type.getName());
        return (Style) withFont.invoke(Style.EMPTY, arg);
    }

    /**
     * If the font failed to load, every glyph is the same "missing" box. A proportional font can't
     * draw "iiiiiiii" as wide as "WWWWWWWW", so that tells us whether to fall back.
     */
    public static void verify() {
        if (broken || regular == null || checks > 20) return;
        checks++;
        try {
            int thin = font().width(Component.literal("iiiiiiii").setStyle(regular));
            int wide = font().width(Component.literal("WWWWWWWW").setStyle(regular));
            if (thin > 0 && thin == wide) {
                broken = true;
                LOG.warning("Custom font did not load (resource problem?), using the vanilla font");
            }
        } catch (Throwable t) {
            broken = true;
        }
    }

    private static Component styled(String s, boolean b) {
        return Component.literal(s).setStyle(b && bold != null ? bold : regular);
    }

    public static int width(String s) { return width(s, false); }

    public static int width(String s, boolean b) {
        if (s == null || s.isEmpty()) return 0;
        return custom() ? font().width(styled(s, b)) : font().width(s);
    }

    public static int height() { return font().lineHeight; }

    public static void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        text(g, s, x, y, color, false);
    }

    public static void text(GuiGraphicsExtractor g, String s, int x, int y, int color, boolean b) {
        if (s == null || s.isEmpty() || (color >>> 24) < 8) return;
        boolean shadow = ModuleManager.CLICK_GUI.textShadow.get();
        if (custom()) {
            float off = ModuleManager.CLICK_GUI.fontOffset.getFloat();
            if (off != 0f) {
                g.pose().pushMatrix();
                g.pose().translate(0f, off);
                g.text(font(), styled(s, b), x, y, color, shadow);
                g.pose().popMatrix();
            } else {
                g.text(font(), styled(s, b), x, y, color, shadow);
            }
        } else {
            g.text(font(), s, x, y, color, shadow);
        }
    }

    public static void centered(GuiGraphicsExtractor g, String s, int cx, int y, int color, boolean b) {
        text(g, s, cx - width(s, b) / 2, y, color, b);
    }

    /** Trims text with "..." to fit a width. */
    public static String fit(String s, int maxWidth) {
        if (width(s) <= maxWidth) return s;
        String dots = "...";
        int end = s.length();
        while (end > 0 && width(s.substring(0, end) + dots) > maxWidth) end--;
        return s.substring(0, end) + dots;
    }
}

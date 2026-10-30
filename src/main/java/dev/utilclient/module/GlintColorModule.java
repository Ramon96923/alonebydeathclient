package dev.utilclient.module;

import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * Recolours the enchantment glint on items and armour. Swaps the vanilla glint texture for one of the
 * client's own tinted versions (TextureManagerMixin), so it costs nothing per frame.
 */
public final class GlintColorModule extends Module {
    private final ModeSetting color = mode("Colour", "Red", "Red", "Orange", "Gold", "Lime", "Green", "Aqua",
            "Blue", "Purple", "Pink", "White", "Rainbow");
    private final BoolSetting armor = bool("Armour too", true).desc("Also recolour glint on worn armour");

    public GlintColorModule() { super("GlintColor", "Change enchantment glint colour", Category.RENDER); }

    @Override public String info() { return color.get(); }

    /** Replacement for a vanilla glint texture, or null to keep it. Called from TextureManagerMixin. */
    public Identifier replace(Identifier id) {
        if (!isEnabled() || !"minecraft".equals(id.getNamespace())) return null;
        String path = id.getPath();
        if (!path.contains("enchanted_glint")) return null;
        if (!armor.get() && path.contains("armor")) return null;
        return Identifier.fromNamespaceAndPath("alonebydeathclient",
                "textures/glint/" + color.get().toLowerCase(Locale.ROOT) + ".png");
    }
}

package dev.utilclient.module;

import dev.utilclient.core.Chat;
import dev.utilclient.core.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Changes how totems of undying look, for you only: in your hand, inventory, on the ground and in the
 * pop animation.
 * <ul>
 *   <li><b>Model</b>: a built-in 3D model (Donut, Waffle, Heart).</li>
 *   <li><b>Skin</b>: a player's head, by name (yours by default).</li>
 *   <li><b>Image</b>: any PNG/JPG from {@code config/donutwaffen/totems/}.</li>
 * </ul>
 */
public final class CustomTotemModule extends Module {
    private static final Logger LOG = Logger.getLogger("donutwaffen");
    private static final Identifier IMAGE_TEX = Identifier.fromNamespaceAndPath("donutwaffen", "textures/item/totem_image.png");

    private final ModeSetting mode = mode("Type", "Model", "Model", "Skin", "Image");
    private final ModeSetting model = mode("Model", "Donut", "Donut", "Waffle", "Heart").visible(() -> mode.is("Model"));
    private final StringSetting skin = text("Skin", "").desc("Player name whose head to use (empty = you)").visible(() -> mode.is("Skin"));
    private final StringSetting image = text("Image", "totem.png")
            .desc("File in config/donutwaffen/totems/ (PNG or JPG, square works best)").visible(() -> mode.is("Image"));

    private ItemStack cachedModel, cachedHead, cachedImage;
    private String cachedModelName, cachedHeadName;
    private volatile byte[] imageBytes;
    private String loadedImage;

    public CustomTotemModule() { super("CustomTotem", "Totem look", Category.RENDER); }

    @Override public String info() { return mode.is("Model") ? model.get() : mode.get(); }

    public static Path folder() { return Config.dir().resolve("totems"); }

    @Override
    protected void onEnable() {
        if (mode.is("Image")) refreshImage(true);
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mode.is("Image") && !image.get().equals(loadedImage)) refreshImage(true);
    }

    /** Swaps the stack the item renderer sees (from ItemModelResolverMixin). Never changes the real item. */
    public ItemStack swap(ItemStack stack) {
        if (stack == null || !stack.is(Items.TOTEM_OF_UNDYING)) return stack;
        try {
            switch (mode.get()) {
                case "Skin" -> {
                    String name = skin.get().isBlank() ? Minecraft.getInstance().getUser().getName() : skin.get().strip();
                    if (cachedHead == null || !name.equals(cachedHeadName)) {
                        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
                        head.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(name));
                        cachedHead = head;
                        cachedHeadName = name;
                    }
                    return cachedHead;
                }
                case "Image" -> {
                    if (cachedImage == null) cachedImage = withModel(stack, "totem_image");
                    return cachedImage;
                }
                default -> {
                    String m = model.get();
                    if (cachedModel == null || !m.equals(cachedModelName)) {
                        cachedModel = withModel(stack, "totem_" + m.toLowerCase(Locale.ROOT));
                        cachedModelName = m;
                    }
                    return cachedModel;
                }
            }
        } catch (Throwable t) {
            return stack;
        }
    }

    private static ItemStack withModel(ItemStack stack, String path) {
        ItemStack s = new ItemStack(Items.TOTEM_OF_UNDYING);
        s.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("donutwaffen", path));
        return s;
    }

    // ---- custom picture --------------------------------------------------------------------------

    /** Bytes for donutwaffen:textures/item/totem_image.png, or null for the bundled placeholder. */
    public byte[] imageFor(Identifier id) {
        return IMAGE_TEX.equals(id) ? imageBytes : null;
    }

    /** Reads the chosen picture, squares it to 128x128 and reloads resources so the new texture is used. */
    public void refreshImage(boolean reload) {
        loadedImage = image.get();
        try {
            Path dir = folder();
            Files.createDirectories(dir);
            Path readme = dir.resolve("README.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme, "Put a PNG or JPG here, then set CustomTotem > Type = Image and Image = <file name>.\n");
            }
            Path f = dir.resolve(image.get().strip());
            if (image.get().isBlank() || !Files.isRegularFile(f)) {
                if (isEnabled() && mode.is("Image")) Chat.info("CustomTotem: put your picture in " + dir + " (looking for " + image.get() + ")");
                imageBytes = null;
                return;
            }
            imageBytes = square(Files.readAllBytes(f));
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "Couldn't load totem image", t);
            imageBytes = null;
            return;
        }
        if (reload) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(mc::reloadResourcePacks);
            Chat.info("CustomTotem: loading " + image.get() + "...");
        }
    }

    private static byte[] square(byte[] raw) {
        try {
            java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(raw));
            if (src == null) return raw;
            int n = 128;
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(n, n, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = out.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double sc = Math.min(n / (double) src.getWidth(), n / (double) src.getHeight());
            int w = (int) Math.round(src.getWidth() * sc), h = (int) Math.round(src.getHeight() * sc);
            g.drawImage(src, (n - w) / 2, (n - h) / 2, w, h, null);
            g.dispose();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(out, "png", bos);
            return bos.toByteArray();
        } catch (Throwable t) {
            return raw;   // no AWT: hope it's already a PNG
        }
    }
}

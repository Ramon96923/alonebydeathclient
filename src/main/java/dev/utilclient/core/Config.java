package dev.utilclient.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.utilclient.gui.ClickGui;
import dev.utilclient.module.Module;
import dev.utilclient.module.ModuleManager;
import dev.utilclient.module.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Saves everything (module toggles, settings, binds, GUI/HUD layout, friends, prefix) to
 * .minecraft/config/alonebydeathclient/config.json. Profiles live in the profiles/ folder next to it.
 */
public final class Config {
    private static final Logger LOG = Logger.getLogger("alonebydeathclient");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int VERSION = 2;

    private static int autosaveTicks;

    private Config() {}

    public static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("alonebydeathclient"); }

    private static Path mainFile() { return dir().resolve("config.json"); }

    private static Path profileDir() { return dir().resolve("profiles"); }

    // ---- main config ---------------------------------------------------------------------------

    public static void load() {
        Path f = mainFile();
        if (!Files.exists(f)) {
            ModuleManager.applyDefaults();
            save();
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            apply(root, true);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not read config, using defaults", e);
            ModuleManager.applyDefaults();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(dir());
            write(mainFile(), build(true));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not save config", e);
        }
    }

    /** Called every client tick: writes the config a few seconds after something changed. */
    public static void tick() {
        if (++autosaveTicks < 100) return;
        autosaveTicks = 0;
        if (ModuleManager.consumeDirty()) save();
    }

    // ---- profiles ------------------------------------------------------------------------------

    public static boolean saveProfile(String name) {
        try {
            Files.createDirectories(profileDir());
            write(profileDir().resolve(clean(name) + ".json"), build(false));
            return true;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not save profile " + name, e);
            return false;
        }
    }

    public static boolean loadProfile(String name) {
        Path f = profileDir().resolve(clean(name) + ".json");
        if (!Files.exists(f)) return false;
        try {
            apply(JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject(), false);
            save();
            return true;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not load profile " + name, e);
            return false;
        }
    }

    public static List<String> profiles() {
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(profileDir())) return out;
        try (Stream<Path> s = Files.list(profileDir())) {
            s.forEach(p -> {
                String n = p.getFileName().toString();
                if (n.endsWith(".json")) out.add(n.substring(0, n.length() - 5));
            });
        } catch (IOException ignored) {
            // empty list
        }
        return out;
    }

    private static String clean(String name) { return name.replaceAll("[^A-Za-z0-9_\\-]", "_"); }

    // ---- (de)serialisation ---------------------------------------------------------------------

    private static void write(Path file, JsonObject json) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(json), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /** @param full false for profiles: modules and settings only (no friends / GUI layout). */
    private static JsonObject build(boolean full) {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);

        JsonObject modules = new JsonObject();
        for (Module m : ModuleManager.ALL) {
            JsonObject mo = new JsonObject();
            mo.addProperty("enabled", m.isEnabled());
            mo.addProperty("key", m.key);
            JsonObject settings = new JsonObject();
            for (Setting s : m.settings) settings.add(s.name, s.save());
            mo.add("settings", settings);
            JsonObject extra = m.saveExtra();
            if (extra != null) mo.add("extra", extra);
            modules.add(m.name, mo);
        }
        root.add("modules", modules);

        if (full) {
            root.addProperty("prefix", Commands.prefix);
            root.add("friends", Friends.save());
            root.add("gui", ClickGui.saveState());
        }
        return root;
    }

    private static void apply(JsonObject root, boolean full) {
        JsonObject modules = root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject();
        for (Module m : ModuleManager.ALL) {
            JsonElement el = modules.get(m.name);
            if (el == null || !el.isJsonObject()) {
                if (full) m.setEnabled(m.isEnabledByDefault(), false);
                continue;
            }
            JsonObject mo = el.getAsJsonObject();
            if (mo.has("settings")) {
                JsonObject settings = mo.getAsJsonObject("settings");
                for (Setting s : m.settings) {
                    try {
                        s.load(settings.get(s.name));
                    } catch (RuntimeException ignored) {
                        // keep default for a setting whose saved value no longer fits
                    }
                }
            }
            if (mo.has("key")) m.key = mo.get("key").getAsInt();
            if (mo.has("extra") && mo.get("extra").isJsonObject()) {
                try {
                    m.loadExtra(mo.getAsJsonObject("extra"));
                } catch (RuntimeException ignored) {
                    // layout data is optional
                }
            }
            boolean on = mo.has("enabled") && mo.get("enabled").getAsBoolean();
            m.setEnabled(on, false);
        }

        if (full) {
            if (root.has("prefix")) Commands.prefix = root.get("prefix").getAsString();
            Friends.load(root.get("friends"));
            if (root.has("gui") && root.get("gui").isJsonObject()) ClickGui.loadState(root.getAsJsonObject("gui"));
        }
    }
}

package dev.utilclient.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.TreeSet;

/** Players that combat modules never target. Add with .friend add NAME or middle-click. */
public final class Friends {
    private static final Set<String> NAMES = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    private Friends() {}

    public static boolean isFriend(Entity e) {
        return e instanceof Player p && NAMES.contains(p.getName().getString());
    }

    public static boolean isFriend(String name) { return NAMES.contains(name); }

    public static boolean add(String name) {
        boolean added = NAMES.add(name);
        if (added) dev.utilclient.module.ModuleManager.markDirty();
        return added;
    }

    public static boolean remove(String name) {
        boolean removed = NAMES.remove(name);
        if (removed) dev.utilclient.module.ModuleManager.markDirty();
        return removed;
    }

    /** @return true if the player is a friend after the call */
    public static boolean toggle(String name) {
        if (isFriend(name)) { remove(name); return false; }
        add(name);
        return true;
    }

    public static Set<String> all() { return NAMES; }

    public static void clear() {
        NAMES.clear();
        dev.utilclient.module.ModuleManager.markDirty();
    }

    public static JsonArray save() {
        JsonArray a = new JsonArray();
        for (String n : NAMES) a.add(n);
        return a;
    }

    public static void load(JsonElement json) {
        NAMES.clear();
        if (json == null || !json.isJsonArray()) return;
        for (JsonElement e : json.getAsJsonArray()) NAMES.add(e.getAsString());
    }
}

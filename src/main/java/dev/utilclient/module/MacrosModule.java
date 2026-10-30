package dev.utilclient.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.utilclient.core.Chat;
import dev.utilclient.core.Keys;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Key-bound chat/command macros for servers like DonutSMP. A macro can chain commands with "&&"
 * ("/sell && /ah"), sent a short moment apart so the server doesn't drop them.
 * Manage them with .macro add | list | del | clear | donut.
 */
public final class MacrosModule extends Module {
    public record Macro(int key, String text) {}

    /** Common DonutSMP commands offered by ".macro donut". */
    public static final String[] DONUT = {"/sell", "/ah", "/home", "/spawn", "/shop", "/rtp", "/orders", "/bal"};

    private final NumberSetting gap = num("Command gap", 10, 2, 40, 1).unit("t").desc("Ticks between chained commands");

    private final List<Macro> macros = new ArrayList<>();
    private final Deque<String> binding = new ArrayDeque<>();   // texts waiting for a key
    private final Deque<String> queue = new ArrayDeque<>();     // commands waiting to be sent
    private int wait;

    public MacrosModule() {
        super("Macros", "Bind chat commands to keys (.macro)", Category.MISC);
        enabledByDefault = true;
    }

    public List<Macro> macros() { return macros; }

    public void startBinding(String text) {
        binding.addLast(text);
        if (binding.size() == 1) promptNext();
    }

    private void promptNext() {
        if (!binding.isEmpty()) Chat.info("Press a key for §f" + binding.peekFirst() + "§7 (Esc skips).");
    }

    /** From ModuleManager.onKey. @return true if the key press was used to bind a macro. */
    public boolean capture(int key, int action) {
        if (binding.isEmpty() || action != InputConstants.PRESS) return false;
        String text = binding.pollFirst();
        if (key == InputConstants.KEY_ESCAPE) {
            Chat.info("Skipped " + text + ".");
        } else {
            macros.removeIf(m -> m.key == key);
            macros.add(new Macro(key, text));
            Chat.good(text + " bound to " + Keys.name(key) + ".");
            ModuleManager.markDirty();
        }
        promptNext();
        return true;
    }

    /** From ModuleManager.onKey while playing (no screen open). */
    public void onKeyPress(int key) {
        if (!isEnabled()) return;
        for (Macro m : macros) {
            if (m.key != key) continue;
            for (String part : m.text.split("&&")) if (!part.isBlank()) queue.addLast(part.trim());
        }
    }

    public void clear() { macros.clear(); ModuleManager.markDirty(); }

    public boolean remove(int index) {
        if (index < 0 || index >= macros.size()) return false;
        macros.remove(index);
        ModuleManager.markDirty();
        return true;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (wait > 0) { wait--; return; }
        String next = queue.pollFirst();
        if (next == null || mc.getConnection() == null) return;
        if (next.startsWith("/")) mc.getConnection().sendCommand(next.substring(1));
        else mc.getConnection().sendChat(next);
        wait = gap.getInt();
    }

    @Override
    public JsonObject saveExtra() {
        JsonObject o = new JsonObject();
        JsonArray arr = new JsonArray();
        for (Macro m : macros) {
            JsonObject j = new JsonObject();
            j.addProperty("key", m.key);
            j.addProperty("text", m.text);
            arr.add(j);
        }
        o.add("macros", arr);
        return o;
    }

    @Override
    public void loadExtra(JsonObject json) {
        macros.clear();
        if (!json.has("macros")) return;
        for (JsonElement el : json.getAsJsonArray("macros")) {
            JsonObject j = el.getAsJsonObject();
            macros.add(new Macro(j.get("key").getAsInt(), j.get("text").getAsString()));
        }
    }
}

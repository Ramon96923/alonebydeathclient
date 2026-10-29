package dev.utilclient.core;

import dev.utilclient.UtilClient;
import dev.utilclient.module.Category;
import dev.utilclient.module.Module;
import dev.utilclient.module.ModuleManager;
import dev.utilclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Chat commands that start with the prefix (default ".") are handled here and never sent to the server.
 * Type .help in chat for the list.
 */
public final class Commands {
    public static String prefix = ".";

    private Commands() {}

    /** @return true if the message was a client command (and must not be sent). */
    public static boolean handle(String message) {
        if (prefix.isEmpty() || !message.startsWith(prefix)) return false;
        String body = message.substring(prefix.length()).trim();
        if (body.isEmpty()) return true;
        try {
            Minecraft.getInstance().gui.hud.getChat().addRecentChat(message);
        } catch (Throwable ignored) {
            // history is a nicety
        }
        String[] args = body.split("\\s+");
        try {
            run(args[0].toLowerCase(Locale.ROOT), Arrays.copyOfRange(args, 1, args.length));
        } catch (RuntimeException e) {
            Chat.error("Command failed: " + e.getMessage());
        }
        return true;
    }

    private static void run(String cmd, String[] a) {
        switch (cmd) {
            case "help", "h", "?" -> help();
            case "toggle", "t" -> toggle(a);
            case "bind", "b" -> bind(a);
            case "binds" -> binds();
            case "set", "s" -> set(a);
            case "reset" -> reset(a);
            case "drawn" -> drawn(a);
            case "modules", "list" -> modules();
            case "friend", "friends", "f" -> friend(a);
            case "config", "cfg", "profile" -> config(a);
            case "prefix" -> prefix(a);
            case "panic" -> Chat.info("Turned off " + ModuleManager.panic() + " modules.");
            case "vclip" -> clip(a, true);
            case "hclip" -> clip(a, false);
            case "gui", "clickgui" -> UtilClient.openGuiNextTick();
            case "font" -> font(a);
            default -> Chat.error("Unknown command '" + cmd + "'. Type " + prefix + "help");
        }
    }

    private static void help() {
        String p = prefix;
        Chat.info("Commands:");
        Chat.info(p + "t <module>  -  toggle a module");
        Chat.info(p + "bind <module> [none]  -  bind to the next key you press");
        Chat.info(p + "binds  -  list key binds");
        Chat.info(p + "set <module> <setting> <value>  -  change a setting");
        Chat.info(p + "reset <module>  -  reset a module's settings");
        Chat.info(p + "drawn <module>  -  show/hide in the HUD list");
        Chat.info(p + "modules  -  list modules");
        Chat.info(p + "friend add|del|list|clear [name]");
        Chat.info(p + "config save|load|list [name]  -  profiles");
        Chat.info(p + "prefix <text>  -  change the command prefix");
        Chat.info(p + "panic  -  turn every module off");
        Chat.info(p + "vclip <blocks> / hclip <blocks>  -  teleport up/forward");
        Chat.info(p + "font on|off  -  custom GUI font");
        Chat.info(p + "gui  -  open the click GUI");
    }

    private static Module module(String[] a, int i) {
        if (a.length <= i) throw new IllegalArgumentException("missing module name");
        Module m = ModuleManager.find(a[i]);
        if (m == null) throw new IllegalArgumentException("no module called '" + a[i] + "'");
        return m;
    }

    private static void toggle(String[] a) {
        Module m = module(a, 0);
        if (m.isAlwaysOn()) {
            Chat.info(m.name + " is a settings module; open the GUI to change it.");
            return;
        }
        m.toggle();
        if (!ModuleManager.NOTIFICATIONS.isEnabled()) Chat.info(m.name + (m.isEnabled() ? " on" : " off"));
    }

    private static void bind(String[] a) {
        Module m = module(a, 0);
        if (a.length > 1 && (a[1].equalsIgnoreCase("none") || a[1].equalsIgnoreCase("clear"))) {
            m.key = -1;
            ModuleManager.markDirty();
            Chat.info(m.name + " unbound.");
            return;
        }
        ModuleManager.startBinding(m);
    }

    private static void binds() {
        int n = 0;
        for (Module m : ModuleManager.ALL) {
            if (m.key < 0) continue;
            Chat.info(m.name + ": " + Keys.name(m.key) + (m.bindMode.is("Hold") ? " (hold)" : ""));
            n++;
        }
        if (n == 0) Chat.info("No binds yet. Use " + prefix + "bind <module>, or right-click a module's Bind row in the GUI.");
    }

    private static void set(String[] a) {
        Module m = module(a, 0);
        if (a.length == 1) {
            List<String> parts = new ArrayList<>();
            for (Setting s : m.settings) parts.add(s.name + "=" + s.valueText());
            Chat.info(m.name + ": " + String.join(", ", parts));
            return;
        }
        if (a.length < 3) throw new IllegalArgumentException("usage: " + prefix + "set <module> <setting> <value>");
        String settingName = String.join("", Arrays.copyOfRange(a, 1, a.length - 1));
        Setting s = m.setting(settingName);
        if (s == null) throw new IllegalArgumentException(m.name + " has no setting '" + settingName + "'");
        if (!s.parse(a[a.length - 1])) throw new IllegalArgumentException("can't use '" + a[a.length - 1] + "' for " + s.name);
        Chat.info(m.name + " " + s.name + " = " + s.valueText());
    }

    private static void reset(String[] a) {
        Module m = module(a, 0);
        for (Setting s : m.settings) s.reset();
        Chat.info(m.name + " settings reset.");
    }

    private static void drawn(String[] a) {
        Module m = module(a, 0);
        m.drawn.toggle();
        Chat.info(m.name + (m.drawn.get() ? " shown in" : " hidden from") + " the module list.");
    }

    private static void modules() {
        for (Category c : Category.values()) {
            List<String> names = new ArrayList<>();
            for (Module m : ModuleManager.byCategory(c)) names.add((m.isEnabled() ? "§a" : "§7") + m.name);
            Chat.info(c.label + ": " + String.join("§7, ", names));
        }
    }

    private static void friend(String[] a) {
        String sub = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "add" -> {
                need(a, 2, "friend add <name>");
                Chat.info(Friends.add(a[1]) ? "Added " + a[1] + " as a friend." : a[1] + " is already a friend.");
            }
            case "del", "remove", "rm" -> {
                need(a, 2, "friend del <name>");
                Chat.info(Friends.remove(a[1]) ? "Removed " + a[1] + "." : a[1] + " wasn't a friend.");
            }
            case "clear" -> { Friends.clear(); Chat.info("Friend list cleared."); }
            default -> Chat.info(Friends.all().isEmpty() ? "No friends added." : "Friends: " + String.join(", ", Friends.all()));
        }
    }

    private static void config(String[] a) {
        String sub = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "save" -> {
                need(a, 2, "config save <name>");
                Chat.info(Config.saveProfile(a[1]) ? "Saved profile " + a[1] + "." : "Could not save profile.");
            }
            case "load" -> {
                need(a, 2, "config load <name>");
                Chat.info(Config.loadProfile(a[1]) ? "Loaded profile " + a[1] + "." : "No profile called " + a[1] + ".");
            }
            case "reload" -> { Config.load(); Chat.info("Config reloaded from disk."); }
            case "folder", "dir" -> Chat.info("Config folder: " + Config.dir());
            default -> {
                List<String> p = Config.profiles();
                Chat.info(p.isEmpty() ? "No profiles yet. Save one with " + prefix + "config save <name>." : "Profiles: " + String.join(", ", p));
            }
        }
    }

    private static void prefix(String[] a) {
        need(a, 1, "prefix <text>");
        prefix = a[0];
        ModuleManager.markDirty();
        Chat.info("Command prefix is now " + prefix);
    }

    private static void font(String[] a) {
        boolean on = a.length == 0 ? !ModuleManager.CLICK_GUI.customFont.get() : !a[0].equalsIgnoreCase("off");
        ModuleManager.CLICK_GUI.customFont.set(on);
        Chat.info("Custom font " + (on ? "on" : "off") + ".");
    }

    private static void clip(String[] a, boolean vertical) {
        need(a, 1, (vertical ? "vclip" : "hclip") + " <blocks>");
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        double d = Double.parseDouble(a[0]);
        if (vertical) {
            p.setPos(p.getX(), p.getY() + d, p.getZ());
        } else {
            double yaw = Math.toRadians(p.getYRot());
            p.setPos(p.getX() - Math.sin(yaw) * d, p.getY(), p.getZ() + Math.cos(yaw) * d);
        }
        Chat.info("Teleported " + d + " blocks.");
    }

    private static void need(String[] a, int n, String usage) {
        if (a.length < n) throw new IllegalArgumentException("usage: " + prefix + usage);
    }
}

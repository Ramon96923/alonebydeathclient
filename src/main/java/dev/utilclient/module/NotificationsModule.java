package dev.utilclient.module;

import dev.utilclient.core.Chat;
import dev.utilclient.core.Toasts;

public final class NotificationsModule extends Module {
    private final ModeSetting where = mode("Show in", "Toast", "Toast", "Chat", "Both");
    private final BoolSetting clientOnly = bool("Skip client modules", true).desc("No popups for HUD/ClickGUI toggles");

    public NotificationsModule() {
        super("Notifications", "Popups when modules turn on or off", Category.CLIENT);
        enabledByDefault = true;
    }

    void onToggle(Module m) {
        if (m == this || (clientOnly.get() && m.category == Category.CLIENT)) return;
        boolean on = m.isEnabled();
        if (where.is("Toast") || where.is("Both")) {
            Toasts.push(m.name, on ? "Enabled" : "Disabled", on ? 0x4ADE80 : 0xF87171);
        }
        if (where.is("Chat") || where.is("Both")) {
            Chat.info(m.name + (on ? " §aenabled" : " §cdisabled"));
        }
    }
}

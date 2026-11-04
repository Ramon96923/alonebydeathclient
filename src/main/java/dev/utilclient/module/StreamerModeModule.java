package dev.utilclient.module;

/** Hides things you don't want on stream or in screenshots: the sidebar scoreboard and your coordinates. */
public final class StreamerModeModule extends Module {
    private final BoolSetting scoreboard = bool("Hide scoreboard", true).desc("Hides the sidebar (money, shards, kills...)");
    private final BoolSetting coords = bool("Hide coordinates", true).desc("Hides the HUD coordinates");

    public StreamerModeModule() { super("StreamerMode", "Hide scoreboard and coordinates", Category.DONUT); }

    public boolean hideScoreboard() { return isEnabled() && scoreboard.get(); }

    public boolean hideCoords() { return isEnabled() && coords.get(); }
}

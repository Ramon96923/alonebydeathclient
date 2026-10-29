package dev.utilclient.module;

public enum Category {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    RENDER("Render"),
    PLAYER("Player"),
    MISC("Misc"),
    CLIENT("Client");

    public final String label;

    Category(String label) { this.label = label; }
}

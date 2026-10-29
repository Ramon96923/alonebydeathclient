package dev.utilclient.module;

public enum Category {
    COMBAT("Combat"), RENDER("Render"), PLAYER("Player");

    public final String label;

    Category(String label) { this.label = label; }
}

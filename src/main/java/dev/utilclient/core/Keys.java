package dev.utilclient.core;

import com.mojang.blaze3d.platform.InputConstants;

public final class Keys {
    private Keys() {}

    /** Human readable key name ("R", "Right Shift"), or "None" when unbound. */
    public static String name(int key) {
        if (key < 0) return "None";
        try {
            return InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName().getString();
        } catch (Throwable t) {
            return "Key " + key;
        }
    }
}

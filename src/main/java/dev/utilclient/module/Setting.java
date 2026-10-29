package dev.utilclient.module;

/** Base class for everything shown as an option row in the click GUI. */
public abstract class Setting {
    public final String name;
    private final Runnable onChange;

    protected Setting(String name, Runnable onChange) {
        this.name = name;
        this.onChange = onChange;
    }

    protected void changed() { if (onChange != null) onChange.run(); }

    /** left = true for a left click, false for a right click */
    public abstract void click(boolean left);

    public abstract String valueText();
}

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

    /** button 0 = left click, 1 = right click */
    public abstract void click(int button);

    public abstract String valueText();
}

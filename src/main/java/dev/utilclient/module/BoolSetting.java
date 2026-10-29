package dev.utilclient.module;

public final class BoolSetting extends Setting {
    private boolean value;

    public BoolSetting(String name, boolean def, Runnable onChange) {
        super(name, onChange);
        this.value = def;
    }

    public boolean get() { return value; }

    public void toggle() { value = !value; changed(); }

    @Override public void click(boolean left) { if (left) toggle(); }

    @Override public String valueText() { return value ? "On" : "Off"; }
}

package dev.utilclient.module;

/**
 * One place for the rules that make combat modules look human (see util/Human). On by default; every
 * combat module goes through it. Turning it off restores the raw, instant behaviour (anarchy servers only).
 */
public final class HumanizerModule extends Module {
    private final NumberSetting maxCps = num("Max CPS", 13, 4, 20, 1)
            .desc("Total clicks per second for all modules plus your own clicks");
    private final BoolSetting onePerTick = bool("One action per tick", true)
            .desc("Never more than one attack/place/use in a single tick across all modules");
    private final NumberSetting reaction = num("Reaction time", 150, 0, 400, 10).unit("ms")
            .desc("Average delay before reacting to a new target, crystal or totem pop");
    private final BoolSetting gcd = bool("Mouse-step rotations", true)
            .desc("Every rotation is a whole number of mouse steps for your sensitivity (beats GCD/sensitivity checks)");
    private final BoolSetting sensSpeed = bool("Speed from sensitivity", true)
            .desc("Aim turns faster at high mouse sensitivity and slower at low, like your real hand");
    private final NumberSetting smooth = num("Aim smoothness", 65, 0, 100, 5).unit("%")
            .desc("How gently aim speeds up and slows into the target");
    private final NumberSetting noise = num("Aim wobble", 0.35, 0, 1.5, 0.05).unit("°").desc("Tiny hand shake while turning");
    private final NumberSetting hesitate = num("Hesitation", 6, 0, 25, 1).unit("%")
            .desc("Chance that any delay is a bit longer, like a person pausing");

    public HumanizerModule() {
        super("Humanizer", "Human aim", Category.CLIENT);
        enabledByDefault = true;
    }

    public int maxCps() { return maxCps.getInt(); }
    public boolean onePerTick() { return onePerTick.get(); }
    public double reactionMs() { return reaction.get(); }
    public boolean gcdFix() { return gcd.get(); }
    public boolean sensSpeed() { return sensSpeed.get(); }
    public float smoothness() { return (float) (smooth.get() / 100.0); }
    public float noise() { return noise.getFloat(); }
    public double hesitation() { return hesitate.get(); }
}

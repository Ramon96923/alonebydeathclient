package dev.utilclient.util;

import dev.utilclient.module.HumanizerModule;
import dev.utilclient.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Makes automated combat look like a person playing. Shared by every combat module, so the rules hold
 * across all of them at once:
 * <ul>
 *   <li><b>Mouse-step rotations</b>: every view change is a whole number of "mouse pixels" for your
 *       sensitivity, exactly like real mouse input (anticheats check this: "GCD"/"sensitivity" checks).</li>
 *   <li><b>Natural aim</b>: turning speeds up and slows down, varies over time, never repeats the same step,
 *       and lands on a slightly different spot of the target each time.</li>
 *   <li><b>Click budget</b>: all modules plus your own clicks share one CPS limit and at most one action per
 *       tick, with gaps that vary like a real finger.</li>
 *   <li><b>Reaction time</b>: nothing reacts to a new target, crystal or totem pop faster than a person can.</li>
 *   <li><b>Swap settle</b>: a hotbar swap and a click never happen in the same tick.</li>
 * </ul>
 */
public final class Human {
    private Human() {}

    private static HumanizerModule h() { return ModuleManager.HUMANIZER; }

    private static ThreadLocalRandom rng() { return ThreadLocalRandom.current(); }

    public static boolean on() { return h().isEnabled(); }

    // ---- ticks ---------------------------------------------------------------------------------

    private static int tick;

    /** START_CLIENT_TICK, before the modules run. */
    public static void onTick() { tick++; }

    public static int tick() { return tick; }

    // ---- random timing -------------------------------------------------------------------------

    /**
     * A human-ish delay in ms: normally distributed around {@code mean}, never below {@code min}, with a
     * small chance of a longer hesitation.
     */
    public static long delay(double mean, double sd, double min) {
        double v = mean + rng().nextGaussian() * sd;
        if (on() && rng().nextDouble() * 100 < h().hesitation()) v += 60 + rng().nextDouble() * 160;
        return (long) Math.max(min, v);
    }

    /** {@link #delay} with a spread of 22% of the mean, and at least 35% of it. */
    public static long delay(double mean) { return delay(mean, mean * 0.22, mean * 0.35); }

    /** A reaction time for something new (target, crystal, pop). 0 when the humanizer is off. */
    public static long reaction() {
        if (!on()) return 0;
        double m = h().reactionMs();
        return delay(m, m * 0.25, Math.max(40, m * 0.5));
    }

    /** Remembers when things were first noticed, for reaction delays (per caller). */
    public static final class Reaction {
        private final Map<Object, Long> due = new HashMap<>();
        private long lastClean;

        /** @return true once {@code key} has been known for a human reaction time */
        public boolean ready(Object key) { return ready(key, reaction()); }

        public boolean ready(Object key, long wait) {
            long now = System.currentTimeMillis();
            if (now - lastClean > 5000) { due.values().removeIf(t -> now - t > 10_000); lastClean = now; }
            Long t = due.get(key);
            if (t == null) { due.put(key, now + wait); return wait <= 0; }
            return now >= t;
        }

        public void clear() { due.clear(); }
    }

    // ---- click budget --------------------------------------------------------------------------

    private static final ArrayDeque<Long> CLICKS = new ArrayDeque<>();
    private static long lastClick, nextGap;
    private static int lastClickTick = -1;

    private static int recent() {
        long cutoff = System.currentTimeMillis() - 1000;
        while (!CLICKS.isEmpty() && CLICKS.peekFirst() < cutoff) CLICKS.pollFirst();
        return CLICKS.size();
    }

    /** May a module click (attack / use / place) right now? */
    public static boolean canClick() {
        if (!on()) return true;
        if (h().onePerTick() && lastClickTick == tick) return false;
        if (recent() >= h().maxCps()) return false;
        return System.currentTimeMillis() - lastClick >= nextGap;
    }

    /** Record a click a module just made. */
    public static void clicked() {
        long now = System.currentTimeMillis();
        CLICKS.addLast(now);
        lastClick = now;
        lastClickTick = tick;
        double mean = 1000.0 / Math.max(1, h().maxCps());
        nextGap = delay(mean * 0.85, mean * 0.25, Math.min(45, mean * 0.6));
    }

    /** Your own physical clicks count towards the same limit (from MouseHandlerMixin). */
    public static void userClick() {
        CLICKS.addLast(System.currentTimeMillis());
        lastClick = System.currentTimeMillis();
    }

    // ---- hotbar swaps --------------------------------------------------------------------------

    private static int swapTick = -100, swapGap = 1;

    /** Called by Inv.select whenever a module changes the hotbar slot. */
    public static void swapped() {
        swapTick = tick;
        swapGap = 1 + (rng().nextInt(100) < 35 ? 1 : 0);
    }

    /** False for a tick or two after a swap, so swap and click never land in the same tick. */
    public static boolean swapSettled() { return !on() || tick - swapTick >= swapGap; }

    // ---- mouse-step (GCD) rotations ------------------------------------------------------------

    /** Degrees one mouse "pixel" turns your view at the current sensitivity (vanilla's formula). */
    public static double pixel() {
        double s = Minecraft.getInstance().options.sensitivity().get();
        double f = s * 0.6 + 0.2;
        return f * f * f * 8.0 * 0.15;
    }

    /** Rounds a rotation change to whole mouse pixels. */
    public static float snap(float delta) {
        if (!on() || !h().gcdFix()) return delta;
        double px = pixel();
        return (float) (Math.round(delta / px) * px);
    }

    /** How fast this sensitivity turns compared with the default (50%), softened: 0.6x .. 1.8x. */
    public static float sensScale() {
        if (!on() || !h().sensSpeed()) return 1f;
        return (float) Math.max(0.6, Math.min(1.8, Math.sqrt(pixel() / 0.15)));
    }

    private static double carryX, carryY;
    private static float pendYaw, pendPitch;
    private static long lastFrameNs, lastApplyNs;

    /** Turn still queued for the coming frames (so aim can plan from where the view is heading). */
    public static float pendingYaw() { return pendYaw; }
    public static float pendingPitch() { return pendPitch; }

    /**
     * Queues a turn of about (dYaw, dPitch) degrees. It's spread over the next few rendered frames, so the
     * view glides like a hand on a mouse instead of jumping once per tick.
     */
    public static void turnView(LocalPlayer p, float dYaw, float dPitch) {
        if (!on()) { turnViewNow(p, dYaw, dPitch); return; }
        pendYaw += dYaw;
        pendPitch += dPitch;
        if (System.nanoTime() - lastFrameNs > 100_000_000L) flush(p);   // no frames (F1/minimised): apply now
    }

    /** Every rendered frame, from the HUD callback. */
    public static void frame() {
        long now = System.nanoTime();
        double dt = lastApplyNs == 0 ? 0.016 : Math.min(0.1, (now - lastApplyNs) / 1e9);
        lastApplyNs = now;
        lastFrameNs = now;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || (pendYaw == 0 && pendPitch == 0)) return;
        // exponential glide: most of a tick's turn lands within ~2-3 frames, all of it well inside the tick
        double k = 1 - Math.exp(-dt / 0.018);
        float sy = (float) (pendYaw * k), sp = (float) (pendPitch * k);
        if (Math.abs(pendYaw - sy) < 0.02f) sy = pendYaw;
        if (Math.abs(pendPitch - sp) < 0.02f) sp = pendPitch;
        pendYaw -= sy;
        pendPitch -= sp;
        turnViewNow(p, sy, sp);
    }

    private static void flush(LocalPlayer p) {
        float y = pendYaw, x = pendPitch;
        pendYaw = pendPitch = 0;
        turnViewNow(p, y, x);
    }

    /**
     * Turns the real view right now, through vanilla's own mouse code path ({@code Entity.turn}), in whole
     * mouse pixels. Leftover fractions carry over to the next call.
     */
    public static void turnViewNow(LocalPlayer p, float dYaw, float dPitch) {
        if (!on() || !h().gcdFix()) {
            p.turn(dYaw / 0.15, dPitch / 0.15);
            return;
        }
        double px = pixel();
        double f = px / 0.15;   // turn() input per pixel
        carryX += dYaw / px;
        carryY += dPitch / px;
        long nx = Math.round(carryX), ny = Math.round(carryY);
        carryX -= nx;
        carryY -= ny;
        if (nx != 0 || ny != 0) p.turn(nx * f, ny * f);
    }

    // ---- natural aim ---------------------------------------------------------------------------

    /**
     * Per-module aim state. {@link #step} returns how far to turn this tick (or frame): speeds up from
     * rest, slows into the target, wobbles a little and changes its pace every few hundred ms.
     */
    public static final class Aim {
        private float v;                 // current turn speed, deg per step
        private float pace = 1f;
        private long paceUntil;
        private Object pointKey;
        private double ox, oy, oz;
        private long pointUntil;

        public void reset() { v = 0; }

        /** @param maxSpeed degrees per step at full pace */
        public float[] step(float curYaw, float curPitch, float wantYaw, float wantPitch, float maxSpeed) {
            float dy = Mth.wrapDegrees(wantYaw - curYaw), dp = wantPitch - curPitch;
            if (!on()) {
                return new float[] {Mth.clamp(dy, -maxSpeed, maxSpeed), Mth.clamp(dp, -maxSpeed, maxSpeed)};
            }
            double dist = Math.sqrt(dy * dy + dp * dp);
            if (dist < 0.02) { v = 0; return new float[] {0, 0}; }
            long now = System.currentTimeMillis();
            if (now > paceUntil) {
                pace = 0.7f + rng().nextFloat() * 0.55f;
                paceUntil = now + 120 + rng().nextInt(300);
            }
            float smooth = h().smoothness();                          // 0 = snappy, 1 = very smooth
            float max = maxSpeed * pace * sensScale();
            float accel = max * (0.55f - 0.35f * smooth) * (0.8f + rng().nextFloat() * 0.4f);
            float speed = Math.min(v + accel, max);
            // ease into the target: never more than a (random) fraction of what's left
            float ease = (float) (dist * ((0.62 - 0.32 * smooth) + rng().nextGaussian() * 0.06));
            speed = Math.min(speed, Math.max(ease, (float) Math.min(dist, 0.35)));
            // low-pass the speed so it never jumps from one step to the next
            speed = v + (speed - v) * (0.75f - 0.4f * smooth);
            speed = Math.min(speed, (float) dist);
            v = speed;
            float k = (float) (speed / dist);
            float pitchK = 0.82f + rng().nextFloat() * 0.14f;     // people move vertically a bit slower
            float noise = h().noise();
            float sy = dy * k + (float) rng().nextGaussian() * noise * Math.min(1f, speed * 0.08f);
            float sp = dp * k * pitchK + (float) rng().nextGaussian() * noise * 0.6f * Math.min(1f, speed * 0.08f);
            return new float[] {sy, sp};
        }

        /** Step the real view toward {@code point}. @return degrees left to turn */
        public double turnView(LocalPlayer p, Vec3 point, float maxSpeed) {
            float[] want = Rotations.angles(p.getEyePosition(), point);
            // plan from where the view is already heading (turn still gliding in from last tick)
            float cy = p.getYRot() + pendingYaw(), cp = p.getXRot() + pendingPitch();
            float[] s = step(cy, cp, want[0], want[1], maxSpeed);
            Human.turnView(p, s[0], s[1]);
            float ry = Mth.wrapDegrees(want[0] - (cy + s[0])), rp = want[1] - (cp + s[1]);
            return Math.sqrt(ry * ry + rp * rp);
        }

        /**
         * A point on {@code box} to aim at that isn't always the dead centre: a random spot near the middle,
         * kept for a few hundred ms (people don't re-pick every tick), new per target.
         */
        public Vec3 point(Object key, AABB box) {
            long now = System.currentTimeMillis();
            if (!on()) return box.getCenter();
            if (key != pointKey || now > pointUntil) {
                pointKey = key;
                pointUntil = now + 250 + rng().nextInt(500);
                ox = Mth.clamp(rng().nextGaussian() * 0.22, -0.4, 0.4);
                oy = Mth.clamp(rng().nextGaussian() * 0.22, -0.35, 0.35);
                oz = Mth.clamp(rng().nextGaussian() * 0.22, -0.4, 0.4);
            }
            Vec3 c = box.getCenter();
            return new Vec3(c.x + ox * box.getXsize(), c.y + oy * box.getYsize(), c.z + oz * box.getZsize());
        }
    }
}

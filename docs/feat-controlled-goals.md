# Controlled Goals

## Summary

A `ControlledGoal<S, B>` is an attack (or behaviour) that can run **two ways from one
implementation**: autonomously, when the boss's own AI decides to use it, or **forced**, when the
editor or a command demands it. This is the heart of Boss API — because both paths run the same
goal, the attack you test in isolation is the same attack a player fights.

Each controlled goal has a **control id** (its handle in commands and the editor, e.g. `"slam"`) and
an **entry state** (the [animation state](feat-states) set when it starts).

## Usage

Extend `BEBoss.ControlledGoal` and split the activation logic:

```java
private static class SlamGoal extends ControlledGoal<MyBoss.State, MyBoss> {
    private int ticks;

    SlamGoal(MyBoss boss) {
        super(boss, "slam", MyBoss.State.SLAM, EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    // Autonomous: when the AI should choose this on its own.
    @Override protected boolean canUseNormally() {
        return boss.getTarget() != null && boss.slamCooldown <= 0
            && boss.distanceTo(boss.getTarget()) < 4.0;
    }
    @Override protected boolean canContinueNormally() { return ticks < 20 && boss.getTarget() != null; }

    // Forced (editor / command): when it is valid to fire on demand. Default is true.
    @Override protected boolean canUseForced() { return true; }
    @Override protected boolean canContinueForced() { return ticks < 20; }

    @Override protected void startControlled() { ticks = 0; /* trigger anim, etc. */ }
    @Override public void tick() { ticks++; /* aim, deal damage on the right frame */ }
    @Override protected void stopControlled() { boss.slamCooldown = 40; }
    @Override public boolean requiresUpdateEveryTick() { return true; }
}
```

Register it: `addControlledGoal(2, new SlamGoal(this));` in `registerGoals()`.

### How forcing works

- Under **manual control**, only the goal whose id has been forced can run — every other controlled
  goal is gated off. That is what makes a forced attack fire alone.
- In a normal fight (no manual control), controlled goals use `canUseNormally()` / `canContinueNormally()`
  like any vanilla goal. A command may still force one; forcing sets a "forced goal id" the base
  clears when the goal ends.
- `canUseForced()` is the guard for on-demand use. Return `false` when a forced fire cannot work
  (for example an attack that yanks an entity needs a living target, so it checks one is present).

### Aiming at the editor target

When testing, the boss's combat target is null, so read the editor target instead. The pattern is to
resolve "who am I attacking" once:

```java
// entity to attack: the editor target under manual control, else the combat target
LivingEntity t = boss.isManualControl()
    ? (boss.getControlTarget() instanceof LivingEntity le ? le : null)
    : boss.getTarget();

// or a bare position (block or entity), for attacks that only need a point:
Vec3 aim = boss.isManualControl() ? boss.getEditorTargetPosition()
    : (boss.getTarget() != null ? boss.getTarget().getEyePosition() : null);
```

This is how an attack fired at `/boss target pos ...` lands on the block you named.

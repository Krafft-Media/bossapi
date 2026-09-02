# The BEBoss Base

## Summary

`BEBoss<S extends Enum<S>>` is the abstract entity every Boss API boss extends. It is a `Monster`
and a GeckoLib `GeoEntity`, plus a testing/editor harness: a synced [animation state](feat-states),
a manual-control mode that gates the normal AI, an editor target (a block position or an entity), a
freeze toggle, and health helpers. A boss that extends it gains all the [driving tools](cat-testing)
for free.

## Usage

Extend it with your state enum and implement `getBossStates()`:

```java
public class MyBoss extends BEBoss<MyBoss.State> {
    public enum State { IDLE, SLAM, SPIT }   // index 0 is the resting state

    public MyBoss(EntityType<? extends Monster> type, Level level) { super(type, level); }

    @Override protected State[] getBossStates() { return State.values(); }
}
```

Register attacks in `registerGoals()` with `addControlledGoal(priority, goal)` (see
[Controlled Goals](feat-controlled-goals)); add your `NearestAttackableTargetGoal` / `HurtByTargetGoal`
as normal. `BEBoss` already installs two priority-0 helper goals — one that holds the boss still while
manually controlled and idle, and one that walks it to an editor **move** target.

### Per-tick hooks

`BEBoss.tick()` is the entry point and handles the frozen case for you. Put your own logic in the
overridable hooks so freeze and manual control keep working:

- `tickBossServer(ServerLevel)` — server-side per-tick logic (cooldowns, boss bar, custom idling).
- `tickBoss()` — runs on both sides.

### The API surface

Reading: `getState()`, `isManualControl()`, `getForcedGoalId()`, `isBossFrozen()`,
`isEditorMoving()`, `getControlTarget()` (the editor target entity, if any),
`getEditorTargetPosition()`, `getStateNames()`, `getControlledGoalIds()`, `canForceGoal(id)`.

Driving: `setManualControl(boolean)`, `forceGoal(id)` / `forceEditorGoal(id, delayTicks)`,
`clearBossControl()`, `setEditorState(name, delayTicks)`, `setEditorTarget(Vec3 | Entity)`,
`clearEditorTarget()`, `startEditorMove()` / `stopEditorMove()`, `setBossFrozen(boolean)`,
`setBossHealth(float)` / `setBossHealthPercent(float)`.

These are exactly what the [commands](feat-commands) and [editor screen](feat-editor) call, so
anything you can do by hand you can also do from code or a test.

### Persistence and state

`BEBoss` persists its frozen flag. Any gameplay state of your own (phases, charge, a dormancy flag)
is yours to save in `addAdditionalSaveData` / `readAdditionalSaveData`. Manual control is a
transient testing mode and is not saved.

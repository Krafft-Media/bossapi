# Animation States

## Summary

Every boss declares an `enum` of states, and `BEBoss` keeps the current one in **synced entity
data** so the client can animate from it. The first enum value (index 0) is the resting state the
framework returns to. States are how your GeckoLib controller knows what to play, and they are
reported to the editor and `/boss inspect` so you can see what the boss is doing.

## Usage

Declare the enum and return it from `getBossStates()`:

```java
public enum State { IDLE, SLAM, SPIT }

@Override protected State[] getBossStates() { return State.values(); }
```

A [Controlled Goal](feat-controlled-goals) sets its **entry state** automatically when it starts, so
`SlamGoal` with entry state `SLAM` puts the boss into `SLAM` for its duration. Drive your animation
controller off `getState()`:

```java
@Override
public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    controllers.add(new AnimationController<>(this, "main", 4, state -> switch (getState()) {
        case SLAM -> state.setAndContinue(SLAM_ANIM);
        case SPIT -> state.setAndContinue(SPIT_ANIM);
        default   -> state.setAndContinue(state.isMoving() ? WALK : IDLE);
    }));
}
```

### Setting states directly

You can also set a state without running a goal — useful for a posed idle, a stun, or a transition:

- `setState(State)` / `setState(String)` from code.
- `setEditorState(name, delayTicks)` puts the boss under manual control, stops it, and holds the
  state — this is what `/boss state <name>` and the editor's **States** tab do. The optional delay
  lets you line up a state change a fixed number of ticks out.

### Returning to rest

`BEBoss` does not force a state back to idle for you — a play-once attack animation would otherwise
stick after its goal ends. The simple pattern is to reset in `tickBossServer` when nothing is
driving an attack:

```java
if (!attacking && getForcedGoalId().isEmpty() && !isEditorMoving() && getState() != State.IDLE) {
    setState(State.IDLE);
}
```

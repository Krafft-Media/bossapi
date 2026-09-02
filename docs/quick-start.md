# Quick Start

This gets Boss API into your mod, turns one mob into a driveable boss, and fires a single attack at
a target you choose.

## 1. Add the dependency

Boss API is a normal NeoForge mod jar. Download `bossapi-<version>.jar` from the
[**releases page**](https://github.com/Krafft-Media/bossapi/releases/tag/v1.0.0), put it on your
project's classpath, and declare it as a dependency.

Drop the jar in your project's `libs/` folder, then in `build.gradle`:

```gradle
dependencies {
    implementation files("libs/bossapi-1.0.0.jar")
}
```

In your `neoforge.mods.toml`:

```toml
[[dependencies.yourmodid]]
    modId="bossapi"
    type="required"
    versionRange="[1.0.0,)"
    ordering="AFTER"
    side="BOTH"
```

Boss API requires **GeckoLib** (its bosses are `GeoEntity`s), so make sure GeckoLib is present too.

## 2. Make your boss extend BEBoss

Give the boss an enum of animation states (the first value is its resting state) and extend
[`BEBoss<S>`](feat-beboss) instead of `Monster`:

```java
public class MyBoss extends BEBoss<MyBoss.State> {
    public enum State { IDLE, SLAM, SPIT }

    public MyBoss(EntityType<? extends Monster> type, Level level) { super(type, level); }

    @Override protected State[] getBossStates() { return State.values(); }
}
```

## 3. Register attacks as Controlled Goals

Each attack is a [`ControlledGoal`](feat-controlled-goals) with a control id and an entry state.
`canUseNormally()` decides when the AI uses it on its own; `canUseForced()` decides when the editor
may force it.

```java
@Override
protected void registerGoals() {
    addControlledGoal(2, new SlamGoal(this)); // controlId "slam", entryState SLAM
    // ... plus your target-selection goals as usual
}
```

See [Controlled Goals](feat-controlled-goals) for the full shape of a goal.

## 4. Drive it in-game

Launch with your boss spawned, then either use the [commands](feat-commands) or the
[Boss Wand](feat-wand).

With commands (these also work from command blocks and `/execute`):

```
/boss select @e[type=yourmod:my_boss,limit=1]
/boss target pos 100 65 200
/boss goal slam
```

`select` puts the boss under **manual control** — it stops acting on its own, so `goal slam` fires
the slam alone, aimed at the position you set. Add `/boss freeze true` to pin it, `/boss health set 1`
to test a phase transition, or `/boss goal clear` and `/boss release` to hand it back to its AI.

With the wand: `/give @s bossapi:boss_wand`, left-click your boss to select it, right-click a block or
mob to aim it, and press the **Boss Editor** key (default **B**) to open the [editor screen](feat-editor).

## Where to go next

The [Features](features) hub covers everything: the [framework](cat-framework) you build on and the
[driving & testing](cat-testing) tools you steer it with.

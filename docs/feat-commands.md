# The /boss Commands

## Summary

`/boss` is the scriptable surface for driving the [selected boss](feat-selection). Every operation
the [editor screen](feat-editor) offers is here as a command, and because selection is global the
commands need no player context — they run from chat, command blocks, and `/execute`, which is what
makes them the backbone of automated boss tests. All of `/boss` requires permission level 2
(gamemasters).

## Usage

### Selecting

- `/boss select <entity>` — select a `BEBoss` (puts it under manual control). Entry point for tests.
- `/boss release` — deselect and return the boss to its AI.
- `/boss inspect` — print the boss's health, state, manual-control flag, forced goal, freeze, target,
  and the lists of its states and goal ids.

### Forcing attacks and states

- `/boss goal <id>` — force a [controlled goal](feat-controlled-goals) to run now.
- `/boss goal <id> <delay>` — force it after `delay` ticks (0–100).
- `/boss goal clear` — clear forced control (stop forcing).
- `/boss state <name>` / `/boss state <name> <delay>` — set an [animation state](feat-states) directly.

Goal and state names autocomplete from the selected boss.

### Aiming

- `/boss target pos <x> <y> <z>` — aim at a block position.
- `/boss target <entity>` — aim at (and track) an entity.
- `/boss target clear` — clear the editor target.
- `/boss move` / `/boss move stop` — walk the boss to its editor target and back off.

### Health and freeze

- `/boss health set <value>` — set absolute health.
- `/boss health percent <0–100>` — set health as a percentage of max (jump to a phase threshold).
- `/boss freeze` — toggle frozen; `/boss freeze <true|false>` — set it. A frozen boss holds
  completely still and ignores forced goals until unfrozen.

## A worked test

Fire one attack at a fixed point and check where it landed:

```
/boss select @e[type=yourmod:my_boss,limit=1]
/boss target pos 100 65 200
/boss goal spit
```

The boss is under manual control from `select`, so `spit` runs alone, aimed at `100 65 200`. Wait
the attack out, then assert on the world at that spot. Re-running gives the same result every time.

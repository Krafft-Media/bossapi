# Selection & Manual Control

## Summary

The driving tools all act on a single **selected** boss, and selection is **global** — one boss at a
time across the whole server, not per player. That is deliberate: a global selection lets the
[`/boss` commands](feat-commands) work from `/execute`, command blocks, and automation, where there
is no player to attribute a selection to.

Selecting a boss puts it into **manual control**, the mode that gates its autonomous AI so a forced
attack runs alone.

## Usage

Select a boss with `/boss select <entity>` or by left-clicking it with the [Boss Wand](feat-wand).
Both call the same operation, so the commands and the wand/screen always agree on which boss is
selected. `/boss release` (or shift-left-click with the wand) deselects and hands the boss back to
its AI.

### What manual control does

- The boss stops choosing targets and actions on its own.
- Only a **forced** [controlled goal](feat-controlled-goals) can run; every other one is gated off —
  so `/boss goal slam` fires the slam with no whip or spit interrupting it.
- The boss otherwise holds still (a built-in idle goal), except when you send it to an editor **move**
  target.

Manual control is transient and never saved. Killing or unloading the selected boss clears the
selection automatically (the next command that needs it reports "no boss selected").

### For automated tests

The global selection is what makes Boss API good for scripted testing. A test does:

```
/boss select @e[type=yourmod:my_boss,limit=1]
/boss target pos <x> <y> <z>
/boss goal <id>
```

and then asserts on the result — with a guarantee that nothing but the forced attack ran. Combined
with `/boss freeze` (pin the boss) and `/boss health` (jump to a phase), you can reproduce any single
moment of a fight on demand.

# The Boss Wand

## Summary

The Boss Wand (`bossapi:boss_wand`) is the hands-on way to select a boss and aim it in-world, without
typing coordinates. It drives the same global [selection](feat-selection) as the
[commands](feat-commands), so the wand, the commands, and the [editor screen](feat-editor) always
act on the same boss.

## Usage

Get one with `/give @s bossapi:boss_wand` (it is also in the creative Op Blocks / Tools tabs).

- **Left-click a boss** — select it (puts it under manual control).
- **Shift-left-click the selected boss** — deselect it, returning it to its AI.
- **Right-click a block** — set the selected boss's editor target to that point.
- **Right-click a mob** — set the target to that entity (the boss tracks it).

A particle marks the point when you set a block target, and the wand messages tell you what was
selected or targeted.

Once a boss is selected and aimed, open the [editor screen](feat-editor) (default key **B**) to force
its goals and states, or use the [`/boss` commands](feat-commands). Selecting a different boss
deselects the previous one — there is only ever one selected boss.

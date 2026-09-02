# The Editor Screen

## Summary

The editor screen is an in-game control panel for the [selected boss](feat-selection) — the same
operations as the [`/boss` commands](feat-commands), but point-and-click and always showing the
boss's live state. It is the comfortable way to iterate on a boss by hand; the commands remain the
way to script repeatable tests.

## Usage

Press the **Boss Editor** keybind (default **B**, rebindable in Controls under the *Boss API*
category) to open it. With a boss [selected](feat-selection) — via the [wand](feat-wand) or
`/boss select` — the panel shows and controls:

- **Health** — a slider to set the boss's current health (test phase transitions).
- **Freeze / Resume AI** — pin the boss in place, or hand it back to autonomous control.
- **Target & Move** — shows the current editor target and distance; send the boss walking to it or
  stop it. Set the target itself with the wand or `/boss target`.
- **Goals tab** — force any [controlled goal](feat-controlled-goals) to run (greyed out when a goal
  can't currently fire), or clear the forced goal.
- **States tab** — set any [animation state](feat-states) directly.
- **Delay** — a slider that applies a tick delay to the goal/state you trigger, so you can line up a
  change a fixed number of ticks out.

The panel refreshes from the server a few times a second, so health, state, target and which goals
are available stay current while you work. It does not pause the game, so you can watch the boss act
as you drive it.

Everything here maps one-to-one onto the [commands](feat-commands); reach for those when you want the
same action in a test or a command block.

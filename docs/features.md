# Features

Everything Boss API does, grouped by what you are trying to accomplish.

## [The Framework](cat-framework)

The code you write to make a boss. Extend one base class, register your attacks as driveable goals,
and drive your animations off a synced state.

- [The BEBoss Base](feat-beboss) — the abstract entity you extend, and what it adds over `Monster`.
- [Controlled Goals](feat-controlled-goals) — attacks that run autonomously *or* on command, from one
  code path.
- [Animation States](feat-states) — a synced enum that drives your GeckoLib controller.

## [Driving & Testing](cat-testing)

The tools that steer a boss so you can test one attack at a time, deterministically.

- [Selection & Manual Control](feat-selection) — the single selected boss, and the mode that gates its
  AI so a forced attack runs alone.
- [The /boss Commands](feat-commands) — select, force goals and states, aim, freeze, set health — from
  chat, command blocks, or automation.
- [The Boss Wand](feat-wand) — select a boss and set its target by clicking, in-world.
- [The Editor Screen](feat-editor) — a control panel for goals, states, target, freeze and health.

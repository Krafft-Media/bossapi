# The Framework

The code you write to make a boss. Boss API is deliberately thin: you extend one base class and
register your attacks as goals, and in return every attack becomes driveable by the
[commands](feat-commands), [wand](feat-wand) and [editor](feat-editor) — without writing any
per-boss tooling.

- [The BEBoss Base](feat-beboss) — the abstract `BEBoss<S>` entity you extend, its lifecycle hooks,
  and the manual-control machinery it adds over `Monster`.
- [Controlled Goals](feat-controlled-goals) — the `ControlledGoal` wrapper that lets one goal run
  autonomously in a fight and on demand from the editor.
- [Animation States](feat-states) — the synced state enum that drives your GeckoLib animation
  controller and is reported back to the editor.

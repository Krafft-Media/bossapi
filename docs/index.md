# Boss API

Boss API is a small NeoForge library mod for building **GeckoLib bosses** and testing their
attacks the way you actually want to: **one attack at a time, at a target you choose, with nothing
else firing to muddy the result.**

A boss with several attacks is hard to test in a live fight — you aggro it, and it whips, hooks and
lobs in whatever order its AI decides, so you can never cleanly see whether *the sand lob* lands
where it should. Boss API fixes that by giving every boss a second mode: an editor/testing harness
that sits alongside the normal autonomous AI. Put a boss under **manual control** and it does
nothing until you force a single goal — so the attack you are testing runs in isolation, aimed at a
block or entity you picked, repeatably.

## What you get

- **A base class, [`BEBoss<S>`](feat-beboss).** Extend it instead of `Monster`. Your attacks become
  [Controlled Goals](feat-controlled-goals) that run autonomously in a real fight *or* on demand from
  the editor — the same code path, so you test what players actually get.
- **The [`/boss` commands](feat-commands).** Select a boss, then force a goal or state at a chosen
  target, freeze it, or set its health. The selection is global, so the commands work from
  `/execute`, command blocks, and automation with no player context — ideal for scripted tests.
- **The [Boss Wand](feat-wand) and [editor screen](feat-editor).** The same controls for hands-on
  work in-game: click a boss to select it, right-click to aim it, press a key to open a control panel.

## How it fits together

Your mod depends on Boss API and your boss `extends BEBoss<YourStates>`. Nothing else changes about
how you write a mob — you still register it, give it attributes, and add goals. The difference is
that the goals you register through Boss API can be *driven*, and the editor and commands can read
and steer the boss's state live.

## Get it

Download the latest `bossapi-<version>.jar` from the
[**releases page**](https://github.com/Krafft-Media/bossapi/releases/tag/v1.0.0) (also on the
**Releases** tab in the sidebar), drop it on your mod's classpath, and declare the dependency — the
[Quick Start](quick-start) walks through it.

New here? Start with the [Quick Start](quick-start), then browse the [Features](features).

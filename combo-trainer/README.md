# Combo Trainer (Fabric 1.21.1)

A **client-side practice tool**, not an automation/cheat mod. It never attacks,
moves, or aims for you — it only measures what *you* did and gives you instant
feedback so you can drill real sword-PvP timing.

## What it does

- **CPS counter** — clicks-per-second in the last second.
- **Crit-timing check** — each swing, it checks the same conditions vanilla
  uses for a critical hit (falling, airborne, not climbing/swimming/riding,
  no blindness) and tells you whether that swing *would* crit.
- **W-tap (sprint-reset) check** — tells you whether you cancelled sprint
  within ~3 ticks of swinging, which is what gives full knockback instead of
  sprint-reduced knockback.
- **Combo streak / accuracy** — landed hits vs. missed swings, confirmed by
  watching the target's actual hurt animation (not just "you pressed attack").
- **Training dummy command** — `/trainingdummy summon [mobId]` spawns a mob
  (zombie by default) a few blocks in front of you, tagged so it auto-heals
  every tick and cannot die, so you can drill combos without resetting it.
  `/trainingdummy clear` removes any dummies you've spawned.
- Toggle the HUD with **`'`** (apostrophe) — rebindable in Controls.

## Why no auto-block / auto-strafe / "auto combat"

Those automate the actual fighting decisions (when to hit, when to
juke/strafe) rather than training your own timing, and most servers'
anti-cheat will flag them even in odd contexts like resource-pack scans.
This mod only reads input and renders text/plays a sound — it's the same
category of thing as a stopwatch, not a bot.

## Building

Requires JDK 21. This project doesn't include the `gradlew`/`gradlew.bat`
scripts or the wrapper jar (binary files) — if you have Gradle installed
locally, generate them once with:

```
gradle wrapper --gradle-version 8.8
```

Then build with:

```
./gradlew build
```

(If you don't have Gradle installed, opening the folder in IntelliJ IDEA with
the Minecraft Development plugin, or in a Fabric-template project, will fetch
it for you automatically.)

The compiled mod jar will be in `build/libs/combo-trainer-1.0.0.jar`.
Drop it into your `.minecraft/mods` folder along with:

- [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.1
- [Fabric API](https://modrinth.com/mod/fabric-api) matching 1.21.1

## Notes on accuracy of this template

I pinned `yarn_mappings`, `loader_version` and `fabric_version` in
`gradle.properties` to versions that are current for 1.21.x as of writing.
Fabric ships new builds constantly — if the project fails to resolve
dependencies, check https://fabricmc.net/develop and bump those three
properties to whatever is listed there for Minecraft 1.21.1, then re-run
`./gradlew build`. I wasn't able to compile this in my sandbox (no access to
Mojang's asset/library servers), so please run a build locally before
trusting it fully; ping me with any compiler errors and I'll fix them.

## Ideas for extending it

- Log each session's hit/crit/W-tap rate over time so you can see practice trends.
- Add a "shield-break" timing check (block held then released right as you swing).
- Let `/trainingdummy summon` take a health value or knockback resistance for
  more realistic PvP-weight dummies.

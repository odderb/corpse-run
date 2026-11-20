# CorpseRun development

## Building
`./gradlew build`. Gradle runs on JDK 21 (pinned in `gradle/gradle-daemon-jvm.properties`), so a JDK 21 must be installed, but your default Java can be newer. The jars end up in `fabric/build/libs` and `neoforge/build/libs`.

## Ghost model
Ghosts are drawn with a custom model loaded from Blockbench's **Bedrock** exports (use a *Bedrock Entity* project):
- `common/src/main/resources/assets/corpserun/geo/ghostie.geo.json` (File → Export → Export Bedrock Geometry)
- `common/src/main/resources/assets/corpserun/animations/ghostie.animation.json` (Animation → Export Animations). The animation named `float` (Blockbench calls it `animation.<model>.float`) loops while you're a ghost.
- `common/src/main/resources/assets/corpserun/textures/entity/tex_ghostie.png`

Supported: bones (pivot, rotation, parent), cubes with box UV, inflate, mirror and their own rotation; position, rotation and scale keyframes with linear or catmullrom easing. Not supported: per-face UV, meshes, Molang expressions. In a running dev client, F3+T reloads the files; errors are logged and a plain cube is shown instead.

## Testing with Create Aeronautics
NeoForge dev runs (`runClient`, `runServer`, `runMultiplayer`) also load Sable, Create and Create Aeronautics, so corpses on airships (Sable sub-levels) can be tested. They only exist in dev runs; the mod just bundles Sable Companion, which does nothing without Sable. Versions are in `gradle.properties`. To test without them, set `dev_aeronautics=false` there, or pass `-Pdev_aeronautics=false` for a single run (e.g. `./gradlew :neoforge:runClient -Pdev_aeronautics=false`).

## Testing multiplayer locally
`./gradlew :neoforge:runMultiplayer` (or `:fabric:runMultiplayer`) starts a local dedicated server, waits for it to come up, then opens two clients, `Player1` and `Player2`, that join it automatically. Both are operators on the server. Close both game windows to stop the server; cancelling the build with Ctrl+C stops everything.

- Each game gets its own folder: `runs/mp-server`, `runs/mp-client1` and `runs/mp-client2` inside the loader's folder. Console output goes to `console.log` in each.
- The first run stops and asks you to accept the [Minecraft EULA](https://aka.ms/MinecraftEULA) by setting `eula=true` in `runs/mp-server/eula.txt`.
- The server only listens on `127.0.0.1:25565`, with online mode and spawn protection off. Remote console (RCON) is on at port 25575 so the task can stop the server cleanly.
- Expect about 2 GB of memory per client.

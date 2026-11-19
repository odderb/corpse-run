# CorpseRun

A Minecraft 1.21.1 mod for **NeoForge** and **Fabric**.

When you die, you come back as a **Ghost** and your body stays behind where you last stood on solid ground. Run back to your corpse and resurrect.

## Being a ghost
- **Untouchable:** you can't take damage (except from the void or `/kill`), mobs ignore you, and your health and hunger never drop. Fire and lava can't burn you.
- **Fast and light:** you move 50% faster, and you can walk on still water and lava. Hold shift to sink in and swim, then swim up to pop back on top.
- **Hands off:** you can't attack, break or place blocks, use items, open chests, trade or pick anything up. Doors, trapdoors, gates, buttons and levers still work.
- **Unseen:** only other ghosts can see you. Living players can still hear you.
- **A different view:** the world turns a deep periwinkle blue under a light layer of static, and most of your HUD is hidden. Your inventory still works.

## Finding your body
- **Howl:** left click to let out a ghostly call that everyone nearby can hear. While it plays, glowing footprints lead you along the way back.
- **Follow the trail:** the footprints follow the path you walked before you died. If you died in another dimension, they lead to the portal you came through.
- **Listen:** your corpse gives off a low hum you can hear when you're nearby.
- **Lost?** If you stray too far from your trail, you'll be offered `/corpse reset` to go back to your spawn point.

## Ethereal blocks
- Right-click a block with an empty hand to place an ethereal block. Stand on it, climb through it, or sneak to sink down, just like scaffolding.
- Only ghosts can see or touch them; to everyone else they're air. You can have 3 at once, and they all vanish when you resurrect.

## Coming back
- Once you're close to your corpse, the message above your hotbar tells you to `/resurrect`. You come back right where you stand.
- After resurrecting you're **Invigorated** for 10 seconds: you can't be hurt, and your hits knock mobs back much further. Use it to get away from whatever killed you.
- Your first resurrection earns the **Corpse Run** advancement.
- CorpseRun never touches your items. Whether you keep them is up to `keepInventory` and your other mods.

## Commands
| Command | Effect |
|---|---|
| `/resurrect` | Come back to life when you're near your corpse. |
| `/corpse resurrect` | Same as `/resurrect`. |
| `/corpse reset` | Go back to your spawn point, still as a ghost. 5 second cooldown. |
| `/corpse fix <player>` | **Operators only.** Brings a stuck ghost back to life where they stand. |

## Config
Settings live in the `config` folder and are created the first time you start the game. Edit them, then restart.

`corpserun-server.properties` (on a server, the server's copy is the one that counts):

| Setting | Default | What it does |
|---|---|---|
| `resurrectRange` | `10` | How close, in blocks, you need to be to your corpse to resurrect. |
| `footprints` | `true` | Show footprints when a ghost howls. |
| `trailMaxPoints` | `4096` | How much of your path is remembered (about 6 km by default). |
| `guidanceLookahead` | `24` | How many blocks of footprints appear ahead of you. |
| `resurrectProtectionSeconds` | `10` | How long you can't be hurt after resurrecting. |
| `resurrectKnockbackPercent` | `300` | How hard your hits knock back while Invigorated. |
| `corpseHum` | `true` | Play the hum from your corpse. |
| `humRange` | `48` | How far away, in blocks, the hum can be heard. |
| `ghostSpeedPercent` | `150` | Ghost walking speed, as a percentage of normal. |
| `etherealBlockLimit` | `3` | How many ethereal blocks a ghost can have at once. `0` turns them off. |
| `ghostsVisibleToLiving` | `false` | Let living players see ghosts. |
| `wanderDistance` | `64` | How far from your trail you can stray before `/corpse reset` is suggested. `0` turns this off. |

`corpserun-client.properties`:

| Setting | Default | What it does |
|---|---|---|
| `ghostScreenEffect` | `true` | Show the blue tint and static while you're a ghost. |
| `ghostHideHud` | `true` | Hide your hotbar and status bars while you're a ghost. |

Building from source? See [DEVELOPMENT.md](DEVELOPMENT.md).

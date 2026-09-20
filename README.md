# PolaroidHomes

[![Build & Release](https://github.com/PolaroidStudio/PolaroidHomes/actions/workflows/build.yml/badge.svg)](https://github.com/PolaroidStudio/PolaroidHomes/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-95d027)](LICENSE)
[![Paper](https://img.shields.io/badge/paper-1.21%2B-289bd0)](https://papermc.io/)
[![Java](https://img.shields.io/badge/java-21-f2c42f)](https://adoptium.net/)
[![EssentialsX](https://img.shields.io/badge/EssentialsX-required-eb4b30)](https://essentialsx.net/)
[![Model Engine](https://img.shields.io/badge/Model%20Engine-optional-6f6085)](https://www.mythiccraft.io/index.php?resources/model-engine.2440/)

A visual layer over EssentialsX homes. It gives players a menu of their homes with an icon they
choose for each one, shows the slots their rank has not unlocked yet, and plays an effect when a
home teleport starts and when it arrives.

## What it does

**A homes menu.** `/homes` opens a grid. Each home is drawn with the icon its owner picked; an
empty slot the player has already unlocked prompts them to set a home there; a slot above their
rank shows a padlock and names the rank that unlocks it. The grid is sized at the server's highest
configured tier, so a player can see what the ranks above them are worth.

**Per-home icons.** Shift-clicking a home opens a picker. The chosen icon is stored by this plugin
and follows the home through a rename; deleting a home drops its icon rather than leaving it
orphaned.

**Teleport effects.** A departure effect plays where the player stands, and an arrival effect plays
at the destination. Either a Model Engine animation or vanilla particles, or nothing at all.

### What it deliberately does not do

**It does not manage home limits.** EssentialsX already resolves a player's limit from its own
`sethome-multiple` groups and the matching `essentials.sethome.multiple.<group>` permissions. This
plugin reads that answer and never stores or grants a limit of its own, which is also why it needs
no permissions-plugin integration: the permissions are already resolved through Bukkit by the time
EssentialsX answers. To give a rank more homes, edit EssentialsX's configuration.

**It does not replace `/sethome`, `/delhome` or `/home`.** Those stay EssentialsX's, warmups,
cooldowns and charges included. The teleport the menu performs runs through EssentialsX's own
command path so none of that is bypassed.

## Requirements

| | |
|---|---|
| Server | Paper 1.21 or newer (the `api-version` is a floor, so later lines work too) |
| Java | 21 |
| Required | [EssentialsX](https://essentialsx.net/) |
| Optional | [Model Engine](https://www.mythiccraft.io/index.php?resources/model-engine.2440/) R4.1.0+, for animated teleport effects |
| Optional | Nexo, ItemsAdder, Oraxen, HeadDatabase, for custom icons |

Folia is supported.

## Installation

1. Install EssentialsX if it is not already there.
2. Drop `PolaroidHomes-<version>-b<build>.jar` into `plugins/`.
3. Start the server once to generate `plugins/PolaroidHomes/config.yml` and
   `plugins/PolaroidHomes/lang/messages_en.yml`.
4. Edit the configuration, then run `/homes reload`.

## Configuration

Every key below lives in `config.yml`. Every player-facing string lives in
`lang/messages_<language>.yml` and nowhere else.

### `language`

Which language file to load. A key missing from that file falls back to the English one shipped
inside the jar, so a partial translation degrades key by key instead of showing raw keys.

### `gui`

| Key | Meaning |
|---|---|
| `rows` | Rows of the window, 1–6. The last row is navigation chrome, so the usable area is `(rows - 1) * 9`. |
| `max-displayed-slots` | The ceiling on how many home slots are drawn before paging. See below. |
| `click-sound`, `click-sound-volume`, `click-sound-pitch` | One sound for the whole plugin, played only by buttons that do something. Set the sound to `''` to disable it. |
| `default-icon` | Drawn for a home whose owner has not picked an icon. |
| `empty-slot-icon` | Drawn for an unlocked slot with no home in it. |
| `locked-slot-icon` | Drawn for a slot above the player's rank. |
| `filler-icon` | The background pane. Its tooltip is hidden. |
| `icon-choices` | The list offered in the picker. |

#### Why `max-displayed-slots` exists

The grid is sized at the server's maximum, not at the viewer's own limit:

```
visibleSlots = min(highest configured EssentialsX tier, max-displayed-slots)
```

A server that configures `sethome-multiple.vip: 9999`, or a tier named `unlimited`, would otherwise
ask the menu to render 9999 slots. Bukkit tops out at 54 per window. The cap is what turns that
from a crash into a paginated grid, and it is itself clamped to what the configured `rows` can
actually hold.

#### Icon references

An entry in `icon-choices`, and the value stored when a player picks one, is an item reference.
Both separators work (`prefix:id` and `prefix-id`):

| Reference | Source |
|---|---|
| `DIAMOND_BLOCK` | a vanilla material |
| `nexo:my_item` | Nexo |
| `itemsadder:suite:my_item` | ItemsAdder |
| `oraxen:my_item` | Oraxen |
| `headdatabase:12345` | HeadDatabase |
| `head:Notch` | a player's head, no dependency |
| `texture:<hash>` | a head from a `textures.minecraft.net` hash, no dependency |
| `basehead:<base64>` | a head from a raw base64 textures value, no dependency |

A reference whose plugin is not installed, or whose id no longer exists, is simply dropped from the
picker. It is never an error.

### `teleport-effects`

| Key | Meaning |
|---|---|
| `mode` | `model-engine`, `particles` or `none`. |
| `fallback` | Used when `mode` is `model-engine` but Model Engine is absent. Accepts `particles` or `none`. |
| `entry` | The departure effect, played where the player stands. |
| `arrival` | The arrival effect, played at the destination. |
| `max-effect-seconds` | Hard ceiling on how long a Model Engine marker entity may live. |

Each of `entry` and `arrival` takes:

| Key | Meaning |
|---|---|
| `model`, `animation` | Model Engine ids, used only in `model-engine` mode. |
| `duration` | Seconds. See below. |
| `particle`, `particle-count`, `particle-radius` | Used in `particles` mode. |

#### Why `duration` is declared and not detected

Model Engine's `AnimationHandler.playAnimation(...)` returns an animation property, not a length,
and the API exposes no duration getter anywhere. The only runtime signal is
`hasFinishedAllAnimations()`, which can only be polled after the animation is already running.

The plugin needs the length *before* it starts, because it uses it to extend the EssentialsX
teleport warmup so the departure animation finishes before the player is moved. Declaring the value
is the only way to know it in time. The warmup is only ever extended, never shortened: a server
that configured a longer teleport delay did so deliberately.

A `duration: auto` mode — polling `hasFinishedAllAnimations()` with a hard timeout ceiling — is
noted in the source as a possible future addition.

### `storage`

| Key | Meaning |
|---|---|
| `type` | `yaml` is the only backend in this release. The storage layer sits behind an interface so a SQL backend can be added without touching the menu. |
| `save-interval-seconds` | How often pending icon changes are written. Icons are also written on disable. |

## Commands

| Command | Permission | What it does |
|---|---|---|
| `/homes` | `polaroidhomes.use` | Opens your homes menu. |
| `/homes help` | — | Lists the commands you can use. |
| `/homes <player>` | `polaroidhomes.admin` | Opens an online player's menu, read-only. |
| `/homes reload` | `polaroidhomes.admin` | Re-reads the configuration and the language file. |

Aliases: `/phomes`, `/homemenu`.

## Permissions

| Permission | Default | Grants |
|---|---|---|
| `polaroidhomes.use` | everyone | Opening the menu and teleporting from it. |
| `polaroidhomes.icon` | everyone | Changing the icon of your own homes. |
| `polaroidhomes.admin` | op | Reloading, and opening another player's menu. |

Home limits are **not** granted here. They come from
`essentials.sethome.multiple.<group>`, which EssentialsX owns.

## Building from source

```bash
git clone https://github.com/PolaroidStudio/PolaroidHomes.git
cd PolaroidHomes
./gradlew test
./gradlew jar
```

The jar lands in `build/libs/`. A JDK 21 toolchain is required and is never downloaded
automatically; point Gradle at one with `JAVA_HOME_21_X64` or an `org.gradle.java.installations.*`
entry if auto-detection does not find yours.

Every integration is `compileOnly`: nothing is shaded into the jar, so the versions on the server
are the ones that get used.

## License

MIT. See [LICENSE](LICENSE).

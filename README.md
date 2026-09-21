# PolaroidHomes

[![Build & Release](https://github.com/PolaroidStudio/PolaroidHomes/actions/workflows/build.yml/badge.svg)](https://github.com/PolaroidStudio/PolaroidHomes/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-95d027)](LICENSE)
[![Paper](https://img.shields.io/badge/paper-1.21%2B-289bd0)](https://papermc.io/)
[![Java](https://img.shields.io/badge/java-21-f2c42f)](https://adoptium.net/)
[![EssentialsX or HuskHomes](https://img.shields.io/badge/EssentialsX%20or%20HuskHomes-required-f2c42f)](https://essentialsx.net/)
[![Model Engine](https://img.shields.io/badge/Model%20Engine-optional-6f6085)](https://www.mythiccraft.io/index.php?resources/model-engine.2440/)

A visual layer over somebody else's homes. It gives players a menu of their homes with an icon they
choose for each one, shows the slots their rank has not unlocked yet, and plays an effect when a
home teleport starts and when it arrives.

The homes themselves come from **EssentialsX or HuskHomes** — whichever you already run. This plugin
never owns a home or a home limit; it reads them from the plugin that does.

## What it does

**A homes menu.** `/homemenu` opens a grid. Each home is drawn with the icon its owner picked; an
empty slot the player has already unlocked prompts them to set a home there; a slot above their
rank shows a padlock and names the rank that unlocks it. The grid is sized at the server's highest
configured tier, so a player can see what the ranks above them are worth.

**Per-home icons.** Shift-clicking a home opens a picker. The chosen icon is stored by this plugin
and follows the home through a rename; deleting a home drops its icon rather than leaving it
orphaned.

**Rename and delete from the menu.** A home button carries four actions:

| Click | Action |
|---|---|
| Left | Teleport |
| Right | Rename — the new name is typed in chat |
| Shift | Change the icon |
| Drop (`Q`) | Delete — **click twice**, the button arms first and says so |

Delete takes the drop key rather than a mouse button because it is the one input a player cannot hit
by aiming badly, and because a destructive action should not share a button with a harmless one. It
never deletes on one press: the first arms the button, which turns red and says what the next press
does, and the arming expires after five seconds or on any other click. Both operations go through
the backend's own API, so the backend fires its own event and the icon follows along by the same
path a command would take.

Rename, delete and the icon picker are all **owner-only**, matching the read-only stance an admin
already has when they open somebody else's menu with `/homemenu <player>`. Teleport is refused there
too, and the backends still have their own commands for when an admin genuinely means it.

**A world blacklist.** Worlds listed in `worlds.blacklist` cannot hold homes. New ones are refused at
the backend's own creation event, and homes already there are shown as unavailable with teleport
refused and a lore line telling the player to delete them.

**`/homes` without registering it.** Both backends already own that label and load first, so it is
intercepted rather than registered. See [`commands`](#commands-1).

**Teleport effects.** A departure effect plays where the player stands, and an arrival effect plays
at the destination. Either a Model Engine animation or vanilla particles, or nothing at all.

### What it deliberately does not do

**It does not manage home limits.** Your home plugin already resolves a player's limit — EssentialsX
from its `sethome-multiple` groups and the matching `essentials.sethome.multiple.<group>` permissions,
HuskHomes from numeric `huskhomes.max_homes.<n>` permissions. This plugin reads that answer and never
stores or grants a limit of its own, which is also why it needs no permissions-plugin integration: the
permissions are already resolved through Bukkit by the time the home plugin answers. To give a rank
more homes, edit that plugin's configuration.

**It does not replace `/sethome`, `/delhome` or `/home`.** Those stay with your home plugin, warmups,
cooldowns and charges included. The teleport the menu performs runs through that plugin's own command
or API so none of it is bypassed.

## Requirements

| | |
|---|---|
| Server | Paper 1.21 or newer (the `api-version` is a floor, so later lines work too) |
| Java | 21 |
| Required | **One** of [EssentialsX](https://essentialsx.net/) or [HuskHomes](https://william278.net/project/huskhomes) 4.x |
| Optional | [Model Engine](https://www.mythiccraft.io/index.php?resources/model-engine.2440/) R4.1.0+, for animated teleport effects |
| Optional | Nexo, ItemsAdder, Oraxen, HeadDatabase, for custom icons |

Neither home plugin is a hard dependency, so the jar loads on a server with either one. It refuses to
enable only when **neither** is present, and says so in the console: there is nothing for it to show
without a home backend.

Folia is supported.

## Installation

1. Install EssentialsX or HuskHomes if neither is already there.
2. Drop `PolaroidHomes-<version>-b<build>.jar` into `plugins/`.
3. Start the server once to generate `plugins/PolaroidHomes/config.yml` and
   `plugins/PolaroidHomes/lang/messages_en.yml`.
4. Edit the configuration, then run `/homemenu reload`.

## Configuration

Three files. `config.yml` holds behaviour, `menu.yml` holds the layout of both menus, and every
player-facing string lives in `lang/messages_<language>.yml` and nowhere else.

### `hooks`

| Key | Meaning |
|---|---|
| `home-provider` | `auto`, `essentialsx` or `huskhomes`. Which plugin owns the homes this menu shows. |

`auto` uses whichever supported plugin is installed. If both are, **EssentialsX wins** — it was this
plugin's only backend before this option existed, so your stored icons were recorded against its home
names. The order is fixed rather than configurable so that two servers with the same plugins always
choose the same backend.

Naming a plugin that is not installed is an **error, not a fallback**. The console says so and the
plugin refuses to enable, rather than quietly reading an empty home list from a different backend —
which is the exact failure this option was added for: on a HuskHomes server the old build assumed
EssentialsX, read no homes from it, and rendered a perfectly correct empty grid.

Changing this key needs a **restart**. A reload logs a warning and keeps the current backend: the
event listeners are registered per provider, and the icons in the store are keyed to the home names of
whichever backend they were chosen under.

#### Support matrix

| | EssentialsX | HuskHomes |
|---|---|---|
| List a player's homes | yes | yes |
| Home coordinates in the lore | yes | yes, except a home on another server in a proxied network |
| Home limit | yes | yes |
| Per-home icons | yes | yes |
| Icon follows a rename | yes, via `HomeModifyEvent` | yes, via `HomeEditEvent` |
| Icon dropped on delete | yes, via `HomeModifyEvent` | yes, via `HomeDeleteEvent` |
| Teleport through the backend's own path | yes, `essentials:home` | yes, its timed-teleport API |
| Rename a home from the menu | yes, `IUser#renameHome` | yes, `renameHome(User, String, String)` |
| Delete a home from the menu | yes, `IUser#delHome` | yes, `deleteHome(User, String)` |
| Block home creation in a blacklisted world | yes, cancels `HomeModifyEvent` (CREATE and UPDATE) | yes, cancels `HomeCreateEvent` (create only) |
| Block a home *relocated* into a blacklisted world | **yes**, EssentialsX reports a move as UPDATE | no — see below |
| Departure and arrival effects | yes | yes |
| Named ranks on a locked slot | **yes** | no — see below |
| Warmup stretched to fit the entry animation | **yes** | no — see below |
| Grid shows slots above the player's own limit | yes | no, there is nothing to attribute them to |

**Named ranks.** EssentialsX enumerates its `sethome-multiple` groups, so a locked slot names the
cheapest rank that unlocks it. HuskHomes resolves a limit from numeric `huskhomes.max_homes.<n>`
permissions and keeps no list of the ranks granting them, so there is nothing to read. Rather than
print "unlocks at None", which reads as a broken config, the menu uses a shorter lore that says the
slot is locked and stops there. It also sizes the grid to the player's own limit instead of to a
server maximum that does not exist, so a HuskHomes menu has no locked slots at all.

**Warmup.** EssentialsX's `TeleportWarmupEvent` exposes `setDelay`, so the warmup is lengthened to the
declared `entry.duration` and the departure animation always finishes before the player moves.
HuskHomes' own `TeleportWarmupEvent` has `getWarmupDuration()` and **no setter** — the value comes from
the player's `huskhomes.teleport_warmup.<n>` permission — so there is nothing to write. On HuskHomes
the effect plays alongside whatever warmup is already configured, and a warmup shorter than
`entry.duration` cuts the animation off. Set HuskHomes' warmup to at least that duration if you want
the whole animation.

**Relocating a home into a blocked world.** EssentialsX fires `HomeModifyEvent` with cause `UPDATE`
when an existing home is moved, so a home relocated *into* a blacklisted world is refused there.
HuskHomes routes a relocation through `HomeEditEvent`, which does not distinguish a moved position
from a changed description, so cancelling it would also refuse edits that have nothing to do with
worlds. On HuskHomes such a home is therefore created successfully and then caught by the menu: it is
drawn as unavailable, teleporting to it is refused, and its lore tells the player to delete it. The
player is never stranded, but the block happens one step later than on EssentialsX.

**Rename and delete are both backends' own APIs, never their storage.** Each call goes through the
method the backend exposes, so the backend fires its own rename or delete event — which is exactly
the event this plugin's icon-lifecycle listener is already hooked to. That is why the icon follows a
menu-driven rename and is dropped on a menu-driven delete without any extra code: the menu takes the
same path a command would. Writing the backend's files directly would move the home and orphan its
icon.

HuskHomes' `renameHome` and `deleteHome` return `void` and hand the work to its own async executor,
so the menu reports "accepted" rather than "done" and never calls `join()` on anything. A rename that
the HuskHomes database then refuses reports itself through HuskHomes' own message.

**Deleting every home at once.** HuskHomes' `DeleteAllHomesEvent` names the owner but not the homes,
and this plugin's icon store cannot enumerate a player's rows, so those icons are left behind. They are
harmless — they are keyed to names nothing resolves — and a home recreated under an old name simply
gets its old icon back.

### `commands`

```yaml
commands:
  intercept:
    homes: true    # /homes opens the menu
    home: false    # /home stays the backend's direct teleport
```

The plugin **registers** `/homemenu` plus the aliases `phomes`, `homesmenu` and `hmenu`. It
deliberately does **not** register `/homes` or `/home`: EssentialsX registers `homes` as an alias of
its own `/home`, HuskHomes registers it as an alias of `/homelist`, and both declare `load: BEFORE`.
A registration here loses that race with nothing logged anywhere, which was a real shipped bug —
players typed `/homes`, the backend answered, and this menu never opened. `CommandLabelTest` pins the
registered labels against it.

Interception is the honest way to get the standard name. The typed line is caught in
`PlayerCommandPreprocessEvent` and answered with the menu before the backend sees it.

**Only the bare command is ever swallowed.** `/homes` opens the menu; `/homes someplayer` does not,
because that form belongs to the backend — it is HuskHomes' own `/homelist <player>`. The same rule
protects `/home <name>`, the most-run command on most servers, which keeps teleporting whatever you
set here. Matching ignores case and a leading slash. A namespaced form is never intercepted:
`/essentials:homes` is a player naming the plugin they want, and answering that with a different
plugin's menu would be dishonest.

`/homemenu` and its aliases stay registered regardless, so turning interception off never leaves the
plugin unreachable. A player without `polaroidhomes.use`, or a server whose backend is not answering,
has their line passed through untouched rather than swallowed and refused.

Upgrading from an older config leaves interception **off**, although a fresh install ships it on for
`homes`. Changing what `/homes` does during an upgrade nobody read the changelog for is a surprise
rather than a migration.

### `worlds`

```yaml
worlds:
  blacklist: []
```

Homes are not allowed in the worlds listed here, and the rule is enforced in two places because a
blacklist that only stops new homes leaves every home made before you added the world still working.

| | What happens |
|---|---|
| Creating a home there | The backend's own creation event is cancelled, so it does not matter how the home was made — `/sethome`, another plugin, or an API call all hit the same event. The player is told which world is blocked. |
| A home already there | It still appears in the menu, drawn as unavailable with its world marked blocked. Teleporting to it is **refused** with the same message, and its lore tells the player to delete it — which the menu itself can do. |

Hiding such a home would be worse than showing it: the player could not act on the problem. Showing
it and refusing it gives them the message and the delete button in the same place.

Matching ignores case. A name that matches no loaded world is kept and simply never matches, so you
may blacklist a world before creating it or leave an entry behind after deleting one; neither is an
error.

### `language`

Which language file to load. A key missing from that file falls back to the English one shipped
inside the jar, so a partial translation degrades key by key instead of showing raw keys.

### `gui`

| Key | Meaning |
|---|---|
| `click-sound`, `click-sound-volume`, `click-sound-pitch` | One sound for the whole plugin, played only by buttons that do something. Set the sound to `''` to disable it. |
| `default-icon` | Drawn for a home whose owner has not picked an icon. |
| `empty-slot-icon` | Drawn for an unlocked slot with no home in it. |
| `locked-slot-icon` | Drawn for a slot above the player's rank. |
| `filler-icon` | The background pane. Its tooltip is hidden. |
| `icon-choices` | The list offered in the picker. |

### `menu.yml`

Where both menus are laid out. Each screen is a picture of the window drawn with characters, plus
one entry saying what each character is:

```yaml
homes:
  rows:
    - "HHHHHHHHH"
    - "HHHHHHHHH"
    - "<###I###>"
  elements:
    'H': { type: home-slot }
    '#': { type: filler, item: BLACK_STAINED_GLASS_PANE }
    '<': { type: previous-page, item: ARROW, name: "<#f2c42f>ᴘʀᴇᴠɪᴏᴜs ᴘᴀɢᴇ" }
    '>': { type: next-page, item: ARROW }
    'I': { type: info, item: PAPER }
```

One to six rows, every row exactly nine characters. A space is an empty slot and needs no
declaration. `home-slot` marks where homes go, filled in reading order with homes, then free slots,
then locked ones. The other types are `previous-page`, `next-page`, `info`, `close` and `filler`
for the grid, and `icon-slot`, `previous-page`, `next-page`, `icon-reset`, `icon-back` and `filler`
for the picker. Each element may declare `item` (any reference from the table below), `name`, `lore`,
`custom-model-data` and `glow`; leaving `name` and `lore` out keeps the text from the language file,
so translations keep working.

The file is validated on startup and on `/homemenu reload`. A section it cannot use is reported in the
console — naming the row or the key at fault — and that screen falls back to the built-in layout.
The plugin is never disabled over a menu typo. An item reference that cannot be resolved falls back
to a safe vanilla item and is reported once, so the window still opens.

`/homemenu reload` closes every open menu, because a window is sized from its layout when it is built
and cannot be resized in place. The new layout applies to the next window opened.

#### Why `max-displayed-slots` exists

It lives in `menu.yml`, next to the layout it caps, because it only means anything relative to one.
The grid is sized at the server's maximum, not at the viewer's own limit:

```
visibleSlots = min(highest configured rank limit, max-displayed-slots)
```

A server that configures `sethome-multiple.vip: 9999`, or a tier named `unlimited`, would otherwise
ask the menu to render 9999 slots. Bukkit tops out at 54 per window. The cap turns that into a
paginated grid — and if you raise it past one page of `home-slot` positions, the layout must carry
both paging buttons, or startup rejects it and falls back.

Upgrading from an earlier release moves your `gui.max-displayed-slots` into `menu.yml`
automatically; `gui.rows` is dropped, because the row strings are the row count now.

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

The plugin needs the length *before* it starts, because on EssentialsX it uses it to extend the
teleport warmup so the departure animation finishes before the player is moved. Declaring the value is
the only way to know it in time. The warmup is only ever extended, never shortened: a server that
configured a longer teleport delay did so deliberately. On HuskHomes the warmup cannot be changed at
all — see the support matrix — so the duration only times the effect there.

A `duration: auto` mode — polling `hasFinishedAllAnimations()` with a hard timeout ceiling — is
noted in the source as a possible future addition.

### `icons`

| Key | Meaning |
|---|---|
| `save-interval-seconds` | How often pending icon changes are written to the database. Icons are also written on disable. |

Which database that is lives in `data.yml`, not here, so reloading the menu configuration can
never imply reopening a connection pool.

## `data.yml`

Icons are stored in SQL. SQLite is the default and needs no setup; MySQL is for several servers
sharing one icon store.

| Key | Meaning |
|---|---|
| `type` | `sqlite` or `mysql`. Anything unrecognised falls back to `sqlite`. |
| `sqlite.file` | File name only. Always created at `plugins/PolaroidHomes/data/homes.db`. |
| `mysql.host`, `mysql.port`, `mysql.database`, `mysql.username`, `mysql.password` | Connection details. |
| `mysql.pool-size` | Connections for MySQL. SQLite is always 1: it has a single writer, so extra connections only queue on the write lock. |

The JDBC drivers and the connection pool are **not** bundled in the jar. Paper downloads them at
load time through the plugin loader, so the server keeps exactly one copy of each driver.

Changing `type`, the SQLite file, or the MySQL host or database needs a **full restart**. A reload
that sees a different backend keeps the open pool and logs a warning: swapping a live pool would
strand in-flight writes, and the in-memory cache in front of it was filled from the old one.

An existing `icons.yml` from an earlier release is imported into the database automatically on the
first enable. The original is renamed to `icons.yml.migrated` rather than deleted, and the number
of imported rows is logged.

## Configuration versioning

Every shipped YAML carries a `config-version`. On enable the plugin compares it against the version
the jar ships and, when the file is older, migrates it forward:

- The original is copied to `<name>.yml.bak-v<old version>` first.
- Keys the new version adds arrive with their defaults; keys that moved are carried across with the
  value you set; keys that no longer exist are dropped.
- Everything you customised is left exactly as it was — the file is never replaced by the default.
- Comments shipped with the plugin are restored after the rewrite. Comments you wrote yourself are
  not recoverable, which is why the backup is written unconditionally.

A file **newer** than the jar (you downgraded the plugin) is refused: the plugin logs a warning and
leaves it untouched rather than migrating it backwards and dropping settings it does not know.

## Commands

| Command | Permission | What it does |
|---|---|---|
| `/homemenu` | `polaroidhomes.use` | Opens your homes menu. |
| `/homemenu help` | — | Lists the commands you can use. |
| `/homemenu <player>` | `polaroidhomes.admin` | Opens an online player's menu, read-only. |
| `/homemenu reload` | `polaroidhomes.admin` | Re-reads the configuration and the language file. |
| `/homes` | `polaroidhomes.use` | Opens your homes menu. **Intercepted, not registered** — see [`commands`](#commands-1). Only the bare form; `/homes <player>` still reaches the backend. |

Aliases: `/phomes`, `/homesmenu`, `/hmenu`.

## Permissions

| Permission | Default | Grants |
|---|---|---|
| `polaroidhomes.use` | everyone | Opening the menu and teleporting from it. |
| `polaroidhomes.icon` | everyone | Changing the icon of your own homes. |
| `polaroidhomes.rename` | everyone | Renaming your own homes from the menu. |
| `polaroidhomes.delete` | everyone | Deleting your own homes from the menu, with a confirmation click. |
| `polaroidhomes.admin` | op | Reloading, and opening another player's menu. |

`polaroidhomes.rename` and `polaroidhomes.delete` follow the style `polaroidhomes.icon` set: on by
default, because they act on the player's own homes and both backends already let them do the same
thing by command. Revoking one hides nothing — the action still refuses with the ordinary
no-permission message — but revoking it does not take the backend's own `/delhome` away, so it only
closes the menu route.

Home limits are **not** granted here. They come from your home plugin:
`essentials.sethome.multiple.<group>` on EssentialsX, `huskhomes.max_homes.<n>` on HuskHomes.

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

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

**A catalog of purchasable teleport effects.** A departure effect plays where the player stands and
an arrival effect plays at the destination — but *which* one is per player, not per server. Operators
declare a catalog of Model Engine animations and vanilla particle effects; each entry generates the
permission node that unlocks it, which you sell or grant from whatever you already use. A player
equips exactly one, from either category, through a menu. A player who has equipped nothing gets a
completely clean teleport. There is no default effect, no economy code here, and no Vault
dependency: this plugin only reads permissions.

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
| Effect catalog and per-player equipping | yes | yes |
| Named ranks on a locked slot | **yes** | no — see below |
| Warmup stretched to fit the equipped animation | **yes** | no — see below |
| Grid shows slots above the player's own limit | yes | no, there is nothing to attribute them to |

**Named ranks.** EssentialsX enumerates its `sethome-multiple` groups, so a locked slot names the
cheapest rank that unlocks it. HuskHomes resolves a limit from numeric `huskhomes.max_homes.<n>`
permissions and keeps no list of the ranks granting them, so there is nothing to read. Rather than
print "unlocks at None", which reads as a broken config, the menu uses a shorter lore that says the
slot is locked and stops there. It also sizes the grid to the player's own limit instead of to a
server maximum that does not exist, so a HuskHomes menu has no locked slots at all.

**Warmup.** EssentialsX's `TeleportWarmupEvent` exposes `setDelay`, so the warmup is lengthened to
the equipped entry's declared `duration` and the departure animation always finishes before the
player moves. Because effects are per player, so is that stretch: a particle effect asks for none
and a player with nothing equipped keeps the warmup EssentialsX was configured with. It is only ever
extended, never shortened.

HuskHomes' own `TeleportWarmupEvent` has `getWarmupDuration()` and **no setter** — the value comes
from the player's `huskhomes.teleport_warmup.<n>` permission — so there is nothing to write. On
HuskHomes the effect plays alongside whatever warmup is already configured, and a shorter warmup cuts
the animation off. See [`teleport-effects`](#teleport-effects) for what that means once each entry
declares its own duration.

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

Where all four menus are laid out — the homes grid (`homes`), the icon picker (`icons`), the effect
category picker (`effects`) and one category's effect list (`effect-list`). Each screen is a picture
of the window drawn with characters, plus one entry saying what each character is:

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
then locked ones. The other types are:

| Screen | Element types |
|---|---|
| `homes` | `home-slot`, `previous-page`, `next-page`, `info`, `close`, `effects`, `filler` |
| `icons` | `icon-slot`, `previous-page`, `next-page`, `icon-reset`, `icon-back`, `filler` |
| `effects` | `effect-animations`, `effect-particles`, `effect-none`, `effect-back`, `filler` |
| `effect-list` | `effect-slot`, `previous-page`, `next-page`, `effect-none`, `effect-back`, `filler` |

`effect-list` is used for **both** categories. Two layouts would be two things to keep in step for
no gain, and a menu that changes shape halfway through reads as a bug. An element one screen
understands is rejected on another, so a category button placed on the homes grid is a startup error
rather than a slot that draws an item and does nothing.

Each element may declare `item` (any reference from the table below), `name`, `lore`,
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

A **catalog** of effects players unlock and equip, not one effect for everybody. Each entry is a
product. A player equips exactly one of them, from either category, and that one plays on their
teleports. A player who has equipped nothing gets a completely clean teleport: no model, no
particles, no extra warmup. **There is no default effect.**

| Key | Meaning |
|---|---|
| `homes` | Decorate home teleports. |
| `tpa` | Decorate an accepted `/tpa`. Only the travelling player is decorated, so a `/tpahere` never is. |
| `animations` | Catalog entries rendered by Model Engine. |
| `particles` | Catalog entries rendered with vanilla particles. |
| `max-effect-seconds` | Hard ceiling on how long a Model Engine marker entity may live. |

Each catalog entry takes:

| Key | Meaning |
|---|---|
| `display-name` | MiniMessage name on the menu button. Required. |
| `icon` | Item reference for the menu button, resolved through the same item layer as `icon-choices`. Required. |
| `entry` | The departure half. Required. |
| `arrival` | The arrival half. Required. |

An `animations` half takes `model`, `animation` and `duration`. A `particles` half takes `particle`,
`count` and `radius`.

Both halves are required, and both belong to one entry, because a teleport effect is authored as a
pair: a charge-up that builds and a landing that resolves. Letting one effect's departure meet
another's arrival would produce a teleport that starts as one thing and lands as another, and it
would be a product you could not describe in a shop with one line.

#### Permissions are generated, never written

A player unlocks an entry by holding the node derived from its id:

```
teleport-effects.animations.purple_charge  ->  polaroidhomes.animation.purple_charge
teleport-effects.particles.portal          ->  polaroidhomes.particle.portal
```

You never write those nodes in `config.yml`, so they cannot drift out of step with the catalog.
Sell or grant them from whatever you already use — a shop plugin, a rank, a crate. **This plugin has
no economy code**, declares no Vault dependency, and never asks who paid for what. It only asks
whether the permission is held right now.

An id may contain lowercase letters, numbers, `_` and `-` only, because it becomes a permission
node. Anything else is rejected with a console message naming the entry.

Losing the permission does **not** clear what a player equipped. The effect simply stops playing,
and it starts again by itself if the rank comes back — nobody has to re-equip after a renewal. The
alternative, deleting the row the moment a rank lapses, turns every renewal into a support ticket.

#### A malformed entry costs one effect, not the feature

Every field above is validated, and an entry that fails is dropped with a console line naming the
entry and the field. The rest of the catalog still loads. Nothing is ever quietly substituted: an
entry naming a particle this Minecraft version does not have is rejected rather than swapped for a
different particle, because a product that does not look like its name is worse than one that is
missing.

#### Animations without Model Engine are hidden, not silent

If Model Engine is not installed, every entry under `animations` is treated as **unavailable**:
hidden from the effect menu, never playable, even for a player who already holds its permission.

The alternative — leaving it visible and having it play nothing — is worse in the one way that
matters for a catalog you sell. A player who bought a visible, equippable effect and then sees
nothing on every teleport cannot tell a missing dependency from a bug, and opens a ticket about it.
Hiding it makes the shortfall yours to notice, which is why the plugin also says so loudly in the
console at every startup.

#### Warmup varies per player now

With EssentialsX, the teleport warmup is stretched to the equipped entry's declared `duration` so
the departure always finishes before the player moves. That now varies by player:

- a player wearing a 3-second animation waits three seconds;
- a player wearing a particle effect gets **no stretch at all** — particles are drawn in one burst,
  so there is nothing to wait for, and a particle entry has no duration field;
- a player wearing nothing keeps exactly the warmup EssentialsX was configured with.

The warmup is only ever **extended**, never shortened, so your own configured delay is still the
floor for everyone. Two players on one server waiting different lengths of time for the same command
is inherent to selling effects of different lengths.

HuskHomes does not allow its warmup to be changed at all — see the support matrix — so there the
effect simply plays alongside whatever warmup is configured. Per-player effects make that gap harder
to close rather than easier: there is no longer one duration to set HuskHomes' warmup against, so if
you want every animation to finish you have to set it to the longest one in your catalog, and every
shorter effect then leaves the player standing still after it has ended. Keeping your animation
durations close together is the practical answer.

#### Why `duration` is declared and not detected

Model Engine's `AnimationHandler.playAnimation(...)` returns an animation property, not a length,
and the API exposes no duration getter anywhere. The only runtime signal is
`hasFinishedAllAnimations()`, which can only be polled after the animation is already running.

The plugin needs the length *before* it starts, because on EssentialsX it uses it to extend the
teleport warmup. Declaring the value is the only way to know it in time.

A `duration: auto` mode — polling `hasFinishedAllAnimations()` with a hard timeout ceiling — is
noted in the source as a possible future addition.

#### The effect menu

A button on the homes grid opens a **category picker** with one button per category. Clicking a
category opens that category's list, paged, with an explicit unequip button and a way back to the
picker.

A picker rather than one window holding both sections, because the catalogs are a price list you
grow over time and nothing here can bound their size. A combined window would either have to page
both sections together — putting one category's entries on a screen whose other half belongs to the
other — or reserve fixed rows for each, wasting half the window when one category is empty and
truncating the other when it is not.

In a list, an entry takes one of three shapes:

- **equipped** — glinting; clicking it takes it off;
- **unlocked** — its own icon and name; clicking it equips it, replacing whatever was on;
- **locked** — the `locked-slot-icon`, no click, and a lore line saying it is locked.

A locked entry names **no price**. This plugin knows only whether a permission is held; it has no
idea what the node costs, where it is sold, or whether it is sold at all rather than granted with a
rank. Edit `menu.effects.entry_locked.lore` in your language file if you want to say where yours are
sold.

The effects button is owner-only. It is drawn in an admin's view of somebody else's menu — hiding it
would make the layout differ between the two — and it refuses the click.

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

### Upgrading to the effect catalog (config.yml v5 → v6)

This is the one upgrade that changes what your players see, so read it before you take it.

**What happens to your config.** Your existing global effect is preserved as a catalog entry. The
migration reads `teleport-effects.mode`, `entry` and `arrival` *before* deleting them and writes
them back out under `teleport-effects.animations.legacy_effect` or
`teleport-effects.particles.legacy_effect`, depending on the mode. Your model id, animation ids,
duration, particle, count and radius all carry across exactly. A `display-name` and an `icon` are
added, because no entry can exist without them and you had neither before — change them to taste.
`max-effect-seconds` is untouched. `teleport-effects.tpa.enabled` becomes the new `teleport-effects.tpa`
flag with the same value, and `teleport-effects.homes` is written `true`, which is what your server
was already doing. A server running `mode: none` gets no catalog entry at all: there was nothing
playing, so there is nothing to preserve. The pre-migration backup is written as always.

**What happens to your players.**

> **Nobody has anything equipped after the upgrade, so effects stop appearing until you grant the
> permissions.**

That is inherent to the feature, not a migration bug. Effects are now sold per player, and the only
other option would be to auto-grant `polaroidhomes.animation.legacy_effect` to everyone — which
would put a free entry in your shop that you did not choose to give away. The plugin says this in
the console at **every** startup rather than only once, so it is read rather than discovered.

To get back to where you were, grant the generated node (`polaroidhomes.animation.legacy_effect` or
`polaroidhomes.particle.legacy_effect`) to whichever group you want, and have those players equip it
from the effect menu. To go the other way, delete the entry and nobody ever sees it.

**What happens to menu.yml (v1 → v2).** The effect picker and list sections are added with their
defaults. The effects button is added to the homes grid **only if your grid is still exactly the
shipped v1 layout**. If you customised your rows, they are left untouched and you add the character
yourself — hunting for a filler slot to overwrite would move a button you placed deliberately.

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
| `polaroidhomes.effects` | everyone | Opening the effect catalog and equipping from it. |
| `polaroidhomes.animation.<id>` | **nobody** | Unlocking one animation entry. Generated from its catalog id. |
| `polaroidhomes.particle.<id>` | **nobody** | Unlocking one particle entry. Generated from its catalog id. |
| `polaroidhomes.admin` | op | Reloading, and opening another player's menu. |

The `animation.` and `particle.` nodes are the ones you sell. They are **generated from the catalog**
in `config.yml`, never declared — so they cannot be listed in `paper-plugin.yml` and cannot drift out
of step with the entries they unlock. None of them is granted by default: an effect nobody has been
sold is an effect nobody wears. `polaroidhomes.effects` only opens the menu; without any of these,
that menu shows a catalog with every row locked.

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

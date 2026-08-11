# .PVP KIT

A client-side Fabric mod for Minecraft **26.2**, built as a personal PvP toolkit for
crystal PvP, LAN sessions with friends, and general combat quality-of-life. Everything
is configured in-game via **Mod Menu** — no config-file editing required.

> Client-side only. Never use the sandbox toggles (see [No Cooldown](#no-cooldown-page))
> on a public server — read the [Fair use](#fair-use) section before you fly, no-clip,
> or godmode your way onto someone else's server.

> **26.2.5-alpha.** This release carries a large batch of unverified work: the reworked
> module HUD, the practice bot's health tag and armour selection, the starvation and
> shield-knockback fixes, and the whole `/arena` command. It compiles and the APIs are
> checked against 26.2, but none of it has been played yet. Expect rough edges, and use
> the last stable tag (`v26.2.4`) if you want something proven.

## Requirements

- Minecraft **26.2**
- [Fabric Loader](https://fabricmc.net/) `0.19.3+`
- [Fabric API](https://modrinth.com/mod/fabric-api) `0.153.0+26.2`
- [Cloth Config](https://modrinth.com/mod/cloth-config) `26.2.155`
- [Mod Menu](https://modrinth.com/mod/modmenu) `20.0.0-beta.4` (optional, but needed for the settings screen)
- **Multi Key Bindings** by kennybc (optional — lets you bind more than one key to any action in the game, this mod's included; see [Keybinds](#keybinds))
- Java 25

## Install

1. Install Fabric Loader for Minecraft 26.2.
2. Drop Fabric API, Cloth Config, and (optionally) Mod Menu into your `mods` folder.
3. Grab the latest jar from [Releases](../../releases) (or build it yourself, below) and
   drop it in `mods` too.
4. Launch. Configure everything from **Mod Menu → .PVP KIT**.

## Build from source

```bash
git clone https://github.com/CattingLeo/Minecraft-PVP-Mod.git
cd Minecraft-PVP-Mod
./gradlew clean build
```

The finished jar lands in `build/libs/` — take the one **without** the `-sources` suffix.

## Features

Configured entirely from **Mod Menu → .PVP KIT**, across six pages:

### Display
FPS, CPS, and Ping — all rainbow-cycling text, positioned bottom-left to stay clear of
minimap overlays like Xaero's. Also the totem corner pop: the actual totem-of-undying
sprite pops in a screen corner the moment *you* pop a totem — a small echo of the vanilla
animation (scale-in, spin, shrink-out, plus a little gold/green spark burst) instead of
the big centre-screen one, which can optionally stay hidden. Corner and size are
configurable. A brief full-screen red flash fires at the same instant (its own toggle, on
by default).

### Clean View
- No slowness FOV zoom — while keeping speed/sprint/bow-draw zoom intact, scaled to the
  Slowness level rather than just clamped.
- No Speed FOV zoom — cancels the FOV zoom-out from the Speed effect the same way,
  while keeping the actual speed boost itself completely intact.
- No nausea / nether-portal screen warp.
- No hurt-camera tilt.
- No blindness / darkness fog.

### Combat
- **Crystal-only explosion removal** — cancels explosion particles specifically where
  an End Crystal was just destroyed, leaving creeper and TNT explosions untouched.
  Client-side entity tracking makes this possible where a resource pack alone cannot
  (a pack can't tell one explosion source from another).
- **Cooldown-ready crosshair flash** — the crosshair sprite (including custom
  resource-pack crosshairs) flashes fully red the instant a held item's cooldown ends.
- **Hit marker** — white corner brackets flash on a landed attack, visually distinct
  from the cooldown flash.
- **Hotbar swap crosshair flash** — the crosshair flashes green every time you switch
  hotbar slots, using the same flash mechanism as the (red) cooldown-ready flash above.
- **Auto Totem** — keeps a totem in your offhand any time one exists elsewhere in your
  inventory.
- **Auto Eat** — switches to hotbar food and eats once hunger drops below a configurable
  threshold.

### Scroll wheel
- **Disable scroll-wheel hotbar switching** — stops the wheel changing your hotbar slot,
  freeing it for something else. GUI/inventory/chat scrolling is unaffected.
- **Scroll UP / DOWN action** — pick what each wheel direction does, so the wheel works
  like a keybind: hotbar next/previous, or toggle Fullbright, Freecam, HUD, Module HUD,
  Auto Totem, Auto Eat, Kill Aura, Flight, No Damage, or Xray. Only fires
  while you're actually playing, not in menus or chat.

  These are config dropdowns rather than real bindable keys because Minecraft's Key Binds
  screen can't capture a scroll event at all — see [DEVELOPMENT.md](DEVELOPMENT.md).

### Freecam
Detaches the view from your body so you can fly the camera around freely while your
player stands frozen in place. WASD to move, space/shift for up/down, sprint to go
faster; mouse look works as normal. Unbound by default — set **Toggle Freecam** under
Options → Controls → Key Binds → PvP Kit. Since it sees through walls, it's
private-world/LAN territory — see [Fair use](#fair-use).

### Module HUD
A client-style module panel in the top-right corner — click a module to toggle it right
there, no need to dig into Mod Menu. Doesn't pause the game. Bound to **Right Shift** by
default (every other keybind in this mod starts unbound — this one's the deliberate
exception).

Modules are grouped into collapsible **Combat / Render / Player / Movement / Misc**
sections with a **search box** at the top: type `tot` and you get Auto Totem, Totem Pop
Counter and Totem Flash regardless of which sections are open. The title bar shows how
many modules are enabled out of the total, so the panel is worth a glance even fully
collapsed. Your search text and which sections are open survive closing and reopening it.

Sub-settings deliberately stay in Mod Menu → .PVP KIT — the sliders, HUD position,
per-ore Xray checkboxes and enum pickers belong on the screen built for them. What the
panel lists is the set of things you'd flip mid-game.

### Xray
Makes ordinary terrain (stone, dirt, deepslate, netherrack, and so on) fully invisible
client-side, so ore blocks show right through it from anywhere — the classic full-block
Xray effect, not just a glow/outline. Per-ore toggles for Coal, Iron, Copper, Gold
(including Nether Gold Ore), Redstone, Lapis, Emerald, Diamond, Ancient Debris, and
Nether Quartz, plus an optional toggle for chests/barrels/shulker boxes/spawners. Off by
default; toggling it (keybind, scroll action, or the Mod Menu switch) takes effect
immediately, no world reload needed. Works correctly alongside Sodium — it hooks
BlockState-level render/occlusion methods that Sodium's mesh builder consults the same
way vanilla does, not the vanilla chunk-renderer classes Sodium replaces. **Read
[Fair use](#fair-use) before using this anywhere but your own world.**

### No Cooldown page
Independent sandbox toggles, plus the original three-way cooldown-removal mode:

| Toggle | Effect |
|---|---|
| **Mode** | Cycles Disabled → No spear cooldown → No cooldown (full attack charge for every weapon, plus no item-use cooldowns: pearls, wind charge, mace, chorus fruit, etc.) |
| **Unlimited Durability** | Items never lose durability |
| **Instant Use** | Eating, drinking, bow draw, crossbow load, and shield raise all complete in a single tick |
| **No Damage** | Full invincibility |
| **Flight** | Creative-style flight without switching game mode |
| **Infinite Hunger** | Food and saturation stay pinned at full |
| **Kill Aura** | Auto-attacks the nearest valid target in range (configurable range and target type: players / hostile mobs / all) — only swings on a fully charged attack meter and only with real line of sight |
| **All Off** | One-click reset for everything on this page |

## Commands

- **`/practicebot idle`** — summons a full-netherite (Protection IV, and genuinely
  *unbreakable*) combat dummy standing exactly where you are, wearing your own name and skin.
  It fights like a real player: real damage, real knockback, real hit reactions — it just
  never dies, because it carries an endless supply of totems (see below). Its name tag shows
  live health, colour-coded green → yellow → red, with any absorption from a totem pop in gold.
- **`/practicebot remove`** — despawns it.

A mode is always required — bare `/practicebot` isn't a command, so the first thing it
suggests is the list of modes.

Its armour's protection enchant is selectable, so you can practise against the kit that
actually matters — add one of these after the command (or after a mode):

| Argument | Armour |
|---|---|
| *(none)* | Protection IV — the default |
| **`blast_protection`** | Blast Protection IV — for crystal and anchor trades |
| **`projectile_protection`** | Projectile Protection IV — for bow and crossbow fights |
| **`fire_protection`** | Fire Protection IV |
| **`protection`** | Protection IV, stated explicitly |

Armour goes **after a mode**, and every mode accepts every option — so `/practicebot idle
blast_protection` is a crystal-resistant dummy that just stands there, and `/practicebot
defend projectile_protection` is one that backs away wearing Projectile Protection. The
arguments match the vanilla enchantment ids, and all four cap at IV, so the level never
varies.

Armour isn't offered directly off the root — that would put eight suggestions on the first
level and bury the four that actually choose behaviour. Naming a mode first keeps the
suggestion list to exactly the four behaviours, then exactly the four armours.

Everything the bot wears and holds carries the **Unbreakable** flag — not merely Unbreaking
III. Its kit takes damage every exchange and nothing ever repairs it (Mending needs XP orbs,
which a fake player never picks up), so without this the armour would eventually break
mid-session and quietly change what you're practising against.

Combat modes (each re-summons the bot in that mode, swapping to the right gear):

| Command | Behaviour |
|---|---|
| **`/practicebot shield`** | Stands still holding its shield up. Disable the shield with an axe and it drops its guard for the real cooldown, exactly like a player |
| **`/practicebot defend`** | Backs away from you, and backs off harder when you're airborne or holding a mace |
| **`/practicebot unmoveable`** | Locked to the spot: nothing can shift it -- not knockback, explosions, water or pistons. Still takes real damage, hit reactions and totem pops |

**Endless totems, not god mode.** The bot holds a Totem of Undying in its offhand which is
restocked every tick. Each death is a genuine vanilla totem activation — the pop animation
plays, it drops to half a heart, and vanilla's own post-totem Regeneration and Absorption
kick in — so you get to practise actually bursting through a totem. It deliberately carries
no Resistance or Regeneration effects, since those suppress damage and knockback and make
hits feel like they aren't landing. It doesn't starve either: it runs a real player tick, so
it has real hunger that drains every time you hit it and no way to eat — left alone that
ends in permanent starvation damage that looks like the bot taking damage out of nowhere.
Its food is pinned just below the level where vanilla would start healing it, so it neither
starves nor quietly regenerates. Its knockback resistance is left exactly as the full
netherite set grants it (0.4), so a hit moves it the same distance it would move a real
player wearing the same kit — no more, no less.

Whatever mode and armour it was in is remembered across world rejoins along with its
position, and it now tells you which mode it came back in. That matters most for
`unmoveable`: a bot restored in that mode ignores knockback *by design*, which is easy to
mistake for a broken bot.

**Singleplayer / world-host only.** Spawning a real, hittable entity needs server
authority, and this mod is client-side only — it only has that authority when you're
the one hosting (true singleplayer, or you used "Open to LAN"). If you join a friend's
world instead of hosting, you can't summon your own bot there — but if *they* summon
one in their world, you can absolutely fight it once it's up, since it's a real entity
synced to everyone normally.

If one was active when you last quit, it comes back automatically (same spot, same
shield mode) the next time you load that world — no need to type the command again.

## Fair use

This mod's design draws a hard line between two categories:

- **Everything outside the No Cooldown page** is purely informational or cosmetic — it
  only reflects information already visible on your own screen (your own hits, your own
  cooldowns, nearby players' positions when you can see them anyway). This is fine on
  essentially any server; check individual server rules if in doubt.
- **The No Cooldown page** is built for **private worlds and LAN sessions with friends
  who know it's active** — same spirit as Bedrock Edition's cheat toggles. Damage,
  durability, hunger, and flight are server-authoritative, so these do nothing
  meaningful on a server you don't host, and using them against people who haven't
  agreed to it is exactly what anti-cheat systems exist to catch. Use this page in your
  own worlds, not on a competitive server.
- **Kill Aura deserves an extra-bold warning**: attacking is client-initiated, so unlike
  the rest of the No Cooldown page it **does** work on servers you don't host. Using it
  against people who haven't agreed to it is plain cheating and a fast ban on any
  moderated server. It exists here for the same reason the rest of the sandbox page
  does — private worlds and LAN games where everyone's in on it.
- **Xray gets the same warning, for a different reason**: it's not combat-related, but
  the server always sends your client the full block data for loaded chunks regardless
  of what you can see, so Xray works on literally any server, not just ones you host.
  Most servers explicitly ban it and many run anti-Xray detection — treat it the same as
  Kill Aura: your own worlds, or servers that have explicitly said it's allowed.

## Keybinds

Options → Controls → Key Binds → two sections, **"PVP"** and **"No Cooldown"**. Every
key ships **unbound** — nothing fires until you set one yourself — **except Toggle
Module HUD, which ships bound to Right Shift** since that was requested as its default.

Want more than one key on the same action (including vanilla ones — Sprint, hotbar
slots, Attack, anything)? Install **Multi Key Bindings** (by kennybc) alongside this mod
— it adds a "+" to every row in the Key Binds screen for exactly that, so there's no
need for this mod to duplicate it.

**PVP**: toggle Fullbright, Hit Marker, Cooldown Flash, Crystal-Only Explosion Removal,
Totem Corner Pop, HUD, Clean View, Auto Totem, Auto Eat, Xray, and **Module
HUD** (Right Shift — opens the clickable module list rather than a plain toggle); plus
**Screenshot** and **Start/Stop Recording** (captures to
`files/screenshots/` and `files/screen recording/<timestamp>/` as a PNG sequence — turn
it into a video with `ffmpeg -framerate 10 -i frame_%06d.png -c:v libx264 -pix_fmt
yuv420p out.mp4`, or use OBS Studio for real-time recording).

**No Cooldown**: Cycle Mode, and a toggle for each of the six Extras above (including
Kill Aura).

## License

[CC0-1.0](LICENSE) — public domain.

## Technical documentation

For exact mixin targets, version-sensitive API notes, and implementation details,
see [DEVELOPMENT.md](DEVELOPMENT.md).

## Credits

Built by [CattingLeo](https://github.com/CattingLeo).

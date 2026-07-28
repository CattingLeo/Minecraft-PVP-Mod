# .PVP KIT

A client-side Fabric mod for Minecraft **26.2**, built as a personal PvP toolkit for
crystal PvP, LAN sessions with friends, and general combat quality-of-life. Everything
is configured in-game via **Mod Menu** — no config-file editing required.

> Client-side only. Never use the sandbox toggles (see [No Cooldown](#no-cooldown-page))
> on a public server — read the [Fair use](#fair-use) section before you fly, no-clip,
> or godmode your way onto someone else's server.

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

Configured entirely from **Mod Menu → .PVP KIT**, across seven pages:

### HUD
FPS, CPS, and Ping — all rainbow-cycling text, positioned bottom-left to stay clear of
minimap overlays like Xaero's.

### Totem
The actual totem-of-undying sprite pops in a screen corner the moment *you* pop a totem —
a small echo of the vanilla animation (scale-in, spin, shrink-out, plus a little gold/green
spark burst) instead of the big centre-screen one, which can optionally stay hidden. Corner
and size are configurable. A brief full-screen red flash fires at the same instant (its own
toggle, on by default).

### Clean View
- No slowness FOV zoom — while keeping speed/sprint/bow-draw zoom intact, scaled to the
  Slowness level rather than just clamped.
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

### Locator
A fancy arrow slides along the screen edge, pointing toward your nearest other player,
with a live distance readout. Hides automatically once you have genuine line of sight
(a real raycast, not just a facing check) or they're within your rough FOV. Off by
default. See [Fair use](#fair-use).

### Utility
| Toggle | Effect |
|---|---|
| **Auto Totem** | Keeps a totem in your offhand any time one exists elsewhere in your inventory |
| **Auto Eat** | Switches to hotbar food and eats once hunger drops below a configurable threshold |
| **Criticals** | Keeps you hopping while on the ground so every hit lands while airborne — automates the same legal bunny-hop timing good PvP players already use manually, doesn't fake anything server-side |
| **Module HUD** | Top-right list naming whichever of this mod's toggles are currently on. Bound to **Right Shift** by default (every other keybind in this mod starts unbound — this one's the deliberate exception) |

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

- **`/practicebot`** — summons a full-netherite (Protection IV, Unbreaking III, Mending),
  unkillable combat dummy standing exactly where you are, wearing your own name and skin.
- **`/practicebot shield`** — same, but it holds its shield up permanently.
- **`/practicebot remove`** — despawns it.

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
- **The No Cooldown page and the Locator arrow** are built for **private worlds and LAN
  sessions with friends who know it's active** — same spirit as Bedrock Edition's cheat
  toggles. Damage, durability, hunger, and flight are server-authoritative, so these do
  nothing meaningful on a server you don't host, and using them against people who
  haven't agreed to it is exactly what anti-cheat systems exist to catch. Use this page
  in your own worlds, not on a competitive server.
- **Kill Aura deserves an extra-bold warning**: attacking is client-initiated, so unlike
  the rest of the No Cooldown page it **does** work on servers you don't host. Using it
  against people who haven't agreed to it is plain cheating and a fast ban on any
  moderated server. It exists here for the same reason the rest of the sandbox page
  does — private worlds and LAN games where everyone's in on it.

## Keybinds

Options → Controls → Key Binds → two sections, **"PVP"** and **"No Cooldown"**. Every
key ships **unbound** — nothing fires until you set one yourself — **except Toggle
Module HUD, which ships bound to Right Shift** since that was requested as its default.

Want more than one key on the same action (including vanilla ones — Sprint, hotbar
slots, Attack, anything)? Install **Multi Key Bindings** (by kennybc) alongside this mod
— it adds a "+" to every row in the Key Binds screen for exactly that, so there's no
need for this mod to duplicate it.

**PVP**: toggle Fullbright, Locator Arrow, Hit Marker, Cooldown Flash, Crystal-Only
Explosion Removal, Totem Corner Pop, HUD, Clean View, Auto Totem, Auto Eat, Criticals,
Module HUD (Right Shift); plus **Screenshot** and **Start/Stop Recording** (captures to
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

# PVP — Technical Documentation

See [README.md](README.md) for user-facing setup and features. This file covers
exact mixin targets and version-sensitive implementation notes.

Client-side Fabric mod for Minecraft **26.2**.

## Configuration
Everything is toggled in-game: **Mod Menu -> PVP** (Cloth Config), with pages
HUD / Totem / Clean View / Combat / Locator / **No Cooldown**. Two config files:
`config/pvpkit.json` and `config/nocooldown.json`. No rebuilds to flip features.

## No Cooldown page
Private-world / LAN-with-friends sandbox toggles. Effective in worlds you host;
damage/durability/hunger/flight are server-authoritative elsewhere, so this page
does nothing meaningful (and would be rule-breaking) on a public server.

- **Mode** — cycling: Disabled -> No spear cooldown -> No cooldown (full attack charge
  for everything + no item-use cooldowns: pearl, wind charge, mace, chorus fruit, ...).
- **Unlimited Durability** — items never lose durability (`ItemStack#setDamageValue`
  cancelled — the single choke point every `hurtAndBreak` overload writes through).
- **Instant Use** — eating, drinking, bow draw, crossbow load, shield raise all
  complete in one tick (`Item#getUseDuration` forced to 1).
- **No Damage** — full invincibility, reusing the same check creative-mode players
  already pass (`Player#isInvulnerableTo`).
- **Flight** — creative-style flight (double-tap space) without switching game mode.
  Turning it off only revokes flight this mod granted.
- **Infinite Hunger** — food/saturation pinned at full (`FoodData#addExhaustion`
  cancelled at its single accumulation point, topped up each tick).
- **All Off** — a checkbox that always shows unchecked; check it and hit Save to
  reset every No Cooldown toggle above back to off in one click.

## Features (HUD / Totem / Clean View / Combat / Locator pages)
- **HUD** — FPS + CPS + Ping (tab-list latency), all **RGB rainbow-cycling** text, bottom-left (clear of Xaero).
- **Totem corner pop** — small gold pop (configurable corner) on your own totem (event 35); centre
  animation hidden via `GameRenderer#displayItemActivation`.
- **Clean view** — no slowness FOV zoom (keeps speed/sprint/bow-draw, scaled to Slowness level),
  no nausea/portal warp, no hurt tilt, no blindness/darkness fog.
- **Fullbright** — client-side Night Vision (topped up quietly), so it **works under shaders**
  incl. Complementary Reimagined/Unbound (high gamma does not; night vision does).
- **Crystal-only explosion removal** — cancels explosion particles only where an End Crystal just
  was; creeper/TNT explosions stay normal.
- **Cooldown red crosshair flash** — the crosshair sprite (your custom one included) flashes fully
  red the instant a held item's cooldown ends.
- **Locator arrow** — fancy corner/edge arrow pointing toward your nearest other
  player, with a live distance readout. No name entry, one on/off toggle. Hides
  automatically once you actually have a clear line of sight to them (real raycast via
  `Level#clip`, not a mixin) or they're within your rough FOV. Off by default:
  Mod Menu -> PVP -> Locator -> "Enable Locator Arrow".

  **Intended for private LAN sessions with friends you've invited yourself** -- everyone
  present is someone you know, comparable to a co-op friend-finder. On a public
  competitive server against strangers who haven't agreed to it, this exact feature is a
  generic enemy tracer/ESP, the category most PvP anti-cheats are built to catch --
  don't use it there. "Lobby" can't be reliably detected client-side, so the one real
  auto-disable is Spectator mode (on by default, toggle in the config).

  **"Show target through walls"** (off by default) adds a coloured outline visible
  through terrain around the target while they're hidden -- this reuses vanilla's own
  glow-outline mechanic (`Player#setGlowingTag`, the same one Spectator mode uses to
  highlight what you're looking at) rather than custom rendering, so it's low-risk and
  self-cleans the moment the target becomes visible, the feature is toggled off, or you
  switch targets. Same intended-use note as the arrow: private LAN with friends only.
- **Hit marker** — white corner brackets flash around the crosshair when your attack swing lands
  (`Player#attack`), visually distinct from the red cooldown flash.

All of the above are toggled from the Mod Menu config screen, not by editing code.

## Build
Requires JDK 25.
```bash
./gradlew clean build
```
Jar in `build/libs/` (without `-sources`). Needs Fabric API `0.153.0+26.2`.

## Version-sensitive spots (all non-required mixins — a miss is skipped, never a crash)
- HUD: `Minecraft#getFps()`, `GuiGraphicsExtractor#text/fill/pose`.
- Clean view: `Options#screenEffectScale/damageTiltScale`, `AbstractClientPlayer#getFieldOfViewModifier` (FovMixin),
  `MobEffectFogEnvironment#isApplicable` + `getMobEffect()` (FogMixin), `DimensionOrBossFogEnvrionment` (NetherFogMixin, Mojang's typo).
- Totem: `LivingEntity#handleEntityEvent` + event id `35`; `Gui#displayItemActivation` (centre hide).
- Fullbright: `MobEffects.NIGHT_VISION`, `MobEffectInstance(Holder,int,int,bool,bool,bool)`, `player.addEffect`.
- Crystal explosion: `ClientLevel#addParticle(ParticleOptions,double x6)` overload, `ParticleTypes.EXPLOSION(_EMITTER)`,
  `ClientEntityEvents.ENTITY_UNLOAD`, `EndCrystal`.
- Cooldown flash: `player.getCooldowns()`, `ItemCooldowns#isOnCooldown(ItemStack)`.


## Keybinds
Options -> Controls -> Key Binds -> two sections, **"PVP"** and **"No Cooldown"**.
Every key starts UNBOUND; nothing fires until you set one.

**PVP** section: Fullbright, Locator Arrow, Hit Marker, Cooldown Flash,
Crystal-Only Explosion Removal, Totem Corner Pop, HUD, Clean View (bundles
slowness FOV/nausea/hurt tilt/darkness/blindness). Plus:
- **Screenshot** -- captures your primary monitor to `files/screenshots/`.
- **Start/Stop Recording** -- captures ~10 frames/sec to `files/screen recording/<timestamp>/`
  as a PNG sequence while toggled on.

Both capture keys use `java.awt.Robot` (a standard JDK API, not a Minecraft internal),
deliberately -- Minecraft's own internal screenshot API showed real signs of being
restructured in the 26.x rendering rewrite, and a wrong guess there risked a silently
broken capture rather than a clean compile error. Trade-off: captures your whole primary
monitor, not cropped exactly to the game window -- accurate in fullscreen/maximized play.

"Recording" is an image sequence, not a video file (no video encoder ships with Java).
Turn it into an actual video afterward:
```bash
ffmpeg -framerate 10 -i frame_%06d.png -c:v libx264 -pix_fmt yuv420p out.mp4
```
For real-time, reliable recording, **OBS Studio** (free) remains the better tool --
this is a lightweight fallback, not a replacement for it.

**No Cooldown** section: Cycle Mode, Toggle Unlimited Durability, Toggle Instant Use,
Toggle No Damage, Toggle Flight, Toggle Infinite Hunger. (No keybind for "All Off" --
that's a Mod Menu-only action, since it's designed as a checkbox-then-Save reset.)

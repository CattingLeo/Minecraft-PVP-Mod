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
- **Kill Aura** — no mixin; a client tick handler that calls
  `MultiPlayerGameMode#attack` + `LocalPlayer#swing`, the same path a real click
  takes. Gated on `Player#getAttackStrengthScale >= 1` (every swing is a
  full-strength hit, one attack per charge — no packet spam), a genuine
  `LivingEntity#hasLineOfSight` raycast (never through walls), no open screen, and
  not Spectator. Nearest-target selection via `Level#getEntitiesOfClass` over the
  player AABB inflated by the configured range (2–6 blocks, distance-squared
  compared against range²). Target filter: players (excl. Spectators) / `Enemy`
  hostiles / all mobs + players. NOTE: attacks are client-initiated, so unlike the
  rest of this page it DOES affect servers you don't host — see Fair use in
  README.md.
- **All Off** — a checkbox that always shows unchecked; check it and hit Save to
  reset every No Cooldown toggle above back to off in one click.

## Features (HUD / Totem / Clean View / Combat / Locator pages)
- **HUD** — FPS + CPS + Ping (tab-list latency), all **RGB rainbow-cycling** text, bottom-left (clear of Xaero).
- **Totem corner pop** — renders the real totem sprite via `GuiGraphicsExtractor#item` with a
  scale-in/spin/shrink-out animation echoing the vanilla centre pop (the vertical-axis spin is
  faked in the 2D HUD layer by squashing horizontal scale with `|cos|`; the true vanilla effect
  is a 3D camera-space tumble the HUD can't drive). Both the pop and the centre-animation hide
  are driven from `GameRenderer#displayItemActivation`, which in 26.2 is called only from
  `ClientPacketListener#handleEntityEvent`'s totem branch and only for the local player, so it's
  an exact "our totem popped" signal.
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

## Utility page
- **Auto Totem** — no mixin; a client tick handler that, if offhand isn't already a
  totem and one exists anywhere in the hotbar/main inventory (`Inventory#getItem(0..35)`),
  performs the same 3-click sequence a real player performs dragging one stack onto
  another with no inventory screen required: `MultiPlayerGameMode#handleContainerInput`
  with `ContainerInput.PICKUP` on the totem's slot, then on offhand (menu slot 45), then
  back on the original slot. Uses the standard `InventoryMenu` slot numbering (9–35 main
  storage, 36–44 hotbar, 45 offhand) — protocol-stable since offhand was added, but
  flagged here as the one spot in this feature that's inferred rather than confirmed
  against actual `InventoryMenu` source, so re-verify if it ever seems to grab the wrong
  slot.
- **Auto Eat** — below the configured hunger threshold (`FoodData#getFoodLevel`),
  switches the selected hotbar slot to the first item with a `DataComponents.FOOD`
  component (sending `ServerboundSetCarriedItemPacket` to keep the server in sync) and
  calls `MultiPlayerGameMode#useItem` every tick — the same call a real held right-click
  drives — until hunger recovers. Hotbar-only by design (reaching into the backpack would
  need the same slot-swap machinery as Auto Totem, and both would fight over the offhand
  slot), and doesn't restore your previous hotbar slot afterward (switching mid-bite would
  cancel the bite, same as it would for a human).
- **Criticals** — rather than spoofing the server-computed fall-distance crit check (a real
  server computes that itself from your synced position, so a purely client-side flag
  wouldn't actually apply), this keeps you perpetually hopping while on the ground via
  `LivingEntity#jumpFromGround` — the same call Space itself triggers — so every attack
  naturally lands mid-air. Automates a completely legal, commonly hand-timed technique;
  doesn't touch damage values.
- **Module HUD** — a Meteor Client-style vertical list of this mod's own toggles that are
  currently on, top-right corner. Purely a status readout (reads `PvpKitClient`'s live
  flags + `NoCooldownConfig.get()`); doesn't do anything by itself. The only keybind in
  this mod bound by default (Right Shift) rather than shipping unbound.

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
- Totem: `GameRenderer#displayItemActivation` (drives both corner pop + centre hide). NOTE: do NOT
  use `LivingEntity#handleEntityEvent` byte-event 35 — in 26.2 totem event 35 is intercepted in
  `ClientPacketListener#handleEntityEvent(ClientboundEntityEventPacket)` and never reaches the
  entity's byte `handleEntityEvent`, so that route is dead (was the original corner-pop bug).
- Fullbright: `MobEffects.NIGHT_VISION`, `MobEffectInstance(Holder,int,int,bool,bool,bool)`, `player.addEffect`.
- Crystal explosion: `ClientLevel#addParticle(ParticleOptions,double x6)` overload, `ParticleTypes.EXPLOSION(_EMITTER)`,
  `ClientEntityEvents.ENTITY_UNLOAD`, `EndCrystal`.
- Cooldown flash: `player.getCooldowns()`, `ItemCooldowns#isOnCooldown(ItemStack)`.


## Keybinds
Options -> Controls -> Key Binds -> two sections, **"PVP"** and **"No Cooldown"**.
Every key starts UNBOUND; nothing fires until you set one.

Binding a second key to the same action (any action -- vanilla included, not just this
mod's) is handled by installing the separate **Multi Key Bindings** mod (by kennybc)
rather than by anything in this codebase. An earlier version of this mod tried to build
that itself two different ways -- per-action `_2` `KeyMapping` duplicates, then a
tick-based reflection layer driving vanilla `KeyMapping`s directly -- both ripped out in
favor of the existing, already-solved mod once it turned up. If you're looking at git
history and see either of those, they're gone; this is why.

**PVP** section: Fullbright, Locator Arrow, Hit Marker, Cooldown Flash,
Crystal-Only Explosion Removal, Totem Corner Pop, HUD, Clean View (bundles
slowness FOV/nausea/hurt tilt/darkness/blindness), Auto Totem, Auto Eat, Criticals,
Module HUD (bound to Right Shift by default -- every other keybind here starts unbound).
Plus:
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
Toggle No Damage, Toggle Flight, Toggle Infinite Hunger, Toggle Kill Aura. (No keybind for "All Off" --
that's a Mod Menu-only action, since it's designed as a checkbox-then-Save reset.)

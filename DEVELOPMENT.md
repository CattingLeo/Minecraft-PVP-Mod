# .PVP KIT — Technical Documentation

See [README.md](README.md) for user-facing setup and features. This file covers
exact mixin targets and version-sensitive implementation notes.

Client-side Fabric mod for Minecraft **26.2**.

## Configuration
Everything is toggled in-game: **Mod Menu -> .PVP KIT** (Cloth Config), with pages
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
  is a 3D camera-space tumble the HUD can't drive), plus a small gold/green spark burst
  radiating from it over the pop's first ~45% (seeded by the pop's own start time, so every
  frame of one pop draws identical spark paths rather than jittering -- vanilla's real totem
  particles already play in the world via `ParticleEngine#createTrackingEmitter` regardless of
  this HUD indicator; the HUD layer can't spawn real Level particles, so this fakes the same
  look in 2D). Also triggers a brief full-screen red flash (`Screen flash on pop`, default on).
  All three (pop, sparks, flash) and the centre-animation hide are driven from
  `GameRenderer#displayItemActivation`, which in 26.2 is called only from
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
- **Hotbar swap crosshair flash** — edge-detects `Inventory#getSelectedSlot()` changing
  tick-to-tick (no vanilla event for this) and reuses the exact same `blitSprite`/`hud/crosshair`
  mechanism as the cooldown flash, just green (`0xFF00FF00`) and its own start-time field so the
  two flashes don't fight over one timer if they ever overlap. (An earlier version of this also
  played a `NOTE_BLOCK_IRON_XYLOPHONE` sound on the same edge-detect; removed -- visual only now.)
- **Locator arrow** — fancy corner/edge arrow pointing toward your nearest other
  player, with a live distance readout. No name entry, one on/off toggle. Hides
  automatically once you actually have a clear line of sight to them (real raycast via
  `Level#clip`, not a mixin) or they're within your rough FOV. Off by default:
  Mod Menu -> .PVP KIT -> Locator -> "Enable Locator Arrow".

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

## Practice bot (`PracticeBotManager`, `/practicebot` / `/practicebot shield` / `/practicebot remove`)
- **Entity**: `net.fabricmc.fabric.api.entity.FakePlayer` (from `fabric-events-interaction-v0`,
  already a transitive dependency via the `fabric-api` bundle) — a real `ServerPlayer` subclass
  with a working stand-in network listener (`FakePlayerPacketListener`/`FakeConnection`), not
  something built from scratch and not a genuine connected player. `FakePlayer.get(ServerLevel,
  GameProfile)` caches instances keyed by (level, profile), so reusing the same profile across
  calls returns the same instance rather than creating duplicates.
- **Skin**: clones the local player's own `GameProfile` (`Minecraft#getGameProfile()`) onto a
  **fresh, fixed synthetic UUID** (`UUID.nameUUIDFromBytes("pvpkit:practicebot")`) with the same
  name and the same `PropertyMap` (skin texture properties) — `new GameProfile(BOT_UUID,
  real.name(), new PropertyMap(real.properties()))`. Never reuses the real player's own UUID:
  two entities can't share one.
- **Gear**: netherite helmet/chestplate/leggings/boots (Protection IV, Unbreaking III, Mending),
  netherite sword (Sharpness V, Unbreaking III, Mending), shield (Unbreaking III, Mending), via
  `LivingEntity#setItemSlot(EquipmentSlot, ItemStack)`. Enchantments are the modern (26.x)
  data-component system: `ItemEnchantments.Mutable` wrapping `ItemEnchantments.EMPTY`, with each
  `Enchantments.X` `ResourceKey<Enchantment>` resolved to a `Holder<Enchantment>` via
  `registryAccess.lookupOrThrow(Registries.ENCHANTMENT).get(key.identifier())` (note:
  `ResourceKey#identifier()`, not `.location()` — verify against the actual class before
  assuming a name here, it's changed before), then written to the stack via
  `stack.set(DataComponents.ENCHANTMENTS, ...)`.
- **"Unkillable"**: NOT `Entity#setInvulnerable(true)` (tried first, reverted) -- that skips
  vanilla's damage pipeline entirely, and since knockback is applied as part of the same hurt
  call, it also silently ate all knockback, making the bot useless for practice (no hit
  reactions, wind burst did nothing). Uses `MobEffectInstance(MobEffects.RESISTANCE,
  Integer.MAX_VALUE, 255, ...)` + `MobEffectInstance(MobEffects.REGENERATION,
  Integer.MAX_VALUE, 1, ...)` instead -- the classic "effectively unkillable" combo (amplifier
  255 is the well-known "resistance to everything" value from `/effect give`) that still runs
  through the real hurt/knockback pipeline, so hits, knockback, and wind burst all behave
  normally; the bot just never actually dies. `Integer.MAX_VALUE` duration rather than a
  tick-based top-up loop, since ticks-until-expiry at that value is on the order of centuries.
- **Shield mode**: each server tick, if not already `isUsingItem()`, calls
  `startUsingItem(InteractionHand.OFF_HAND)` — shield-blocking is a generic `LivingEntity`
  mechanic in vanilla, not player-specific, so this works on a non-player-controlled entity the
  same way it would on a real player holding right-click.
- **Threading — the one real bug this shipped with and had to be fixed**: the first version
  called `ServerLevel#addFreshEntity` directly inside the Brigadier command callback, which
  fires on the CLIENT thread (a client command, via `ClientCommandRegistrationCallback`) — NOT
  the server thread, even in singleplayer, where the integrated server still runs on its own
  thread despite sharing a JVM with the client. C2ME's `preventAsyncEntityLoad` mixin correctly
  caught this as an illegal cross-thread chunk/entity mutation and threw
  `ConcurrentModificationException: Async entity load`, so the bot never finished being added
  (and never rendered), while a partial/aborted registration left "UUID of added entity already
  exists" warnings on every retry. Fixed by capturing every client-only value needed (profile,
  exact spawn position via `Entity#position()`, yaw) as local finals BEFORE calling
  `server.execute(() -> { ... })`, doing all entity/world mutation inside that runnable, and
  hopping back via `mc.execute(() -> source.sendFeedback(...))` for the chat reply. Only calls
  `addFreshEntity` if `level.getEntity(BOT_UUID) == null` (not already tracked in that level) --
  repeat `/practicebot` calls just reposition/re-equip the existing tracked entity directly,
  since normal per-tick entity sync picks up the moved position on its own without needing to
  re-add it.
- **Second real bug, found after the threading fix**: the command ran without error and the
  entity WAS added server-side, but nothing appeared client-side. Confirmed via
  `latest.log`: `Server attempted to add player prior to sending player info` immediately
  followed by `Skipping Entity with id entity.minecraft.player` -- the client explicitly
  discards any player-type entity add if it hasn't already been told that UUID's GameProfile.
  A real player normally gets this via `PlayerList#placeNewPlayer`'s join sequence; a
  `FakePlayer` never goes through that. Fixed by broadcasting
  `ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(newBot))` via
  `server.getPlayerList().broadcastAll(...)` right before `addFreshEntity`, the first time
  only (not on reposition calls, since the client already has the profile by then) -- this
  is the same "here's a brand-new player: profile, skin, gamemode, listed" packet
  `placeNewPlayer` itself sends, just replicated manually since the FakePlayer bypasses that
  path entirely.
- **Scope**: singleplayer / world-host only. `Minecraft#getSingleplayerServer()` is null for
  anyone who only joined someone else's world (no server authority from the client side in that
  case) -- see the README's Commands section for the player-facing version of this limit.

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

package com.catting.pvpkit;

import com.catting.nocooldown.NoCooldownConfig;
import com.mojang.blaze3d.platform.InputConstants;
import com.catting.pvpkit.PvpKitConfig.TotemCorner;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

/** Mod Menu -> PVP -> full settings screen (Cloth Config). */
public class PvpKitModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            PvpKitConfig c = PvpKitConfig.get();
            NoCooldownConfig nc = NoCooldownConfig.get();
            ConfigBuilder b = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Component.literal(".PVP KIT"));
            b.setSavingRunnable(() -> {
                PvpKitConfig.save();
                PvpKitClient.applyConfig();
                NoCooldownConfig.save();
                MultiBindConfig.save();
            });
            ConfigEntryBuilder e = b.entryBuilder();

            // ---------------- HUD ----------------
            ConfigCategory hud = b.getOrCreateCategory(Component.literal("HUD"));
            hud.addEntry(e.startBooleanToggle(Component.literal("Show FPS"), c.showFps)
                    .setDefaultValue(true).setSaveConsumer(v -> c.showFps = v).build());
            hud.addEntry(e.startBooleanToggle(Component.literal("Show CPS"), c.showCps)
                    .setDefaultValue(true).setSaveConsumer(v -> c.showCps = v).build());
            hud.addEntry(e.startBooleanToggle(Component.literal("Show Ping"), c.showPing)
                    .setDefaultValue(true).setSaveConsumer(v -> c.showPing = v).build());
            hud.addEntry(e.startBooleanToggle(Component.literal("Rainbow (RGB) text"), c.rainbowText)
                    .setDefaultValue(true).setSaveConsumer(v -> c.rainbowText = v).build());
            hud.addEntry(e.startIntField(Component.literal("HUD X (negative = from right)"), c.hudX)
                    .setDefaultValue(6).setSaveConsumer(v -> c.hudX = v).build());
            hud.addEntry(e.startIntField(Component.literal("HUD Y (negative = from bottom)"), c.hudY)
                    .setDefaultValue(-28).setSaveConsumer(v -> c.hudY = v).build());

            // ---------------- Totem ----------------
            ConfigCategory totem = b.getOrCreateCategory(Component.literal("Totem"));
            totem.addEntry(e.startBooleanToggle(Component.literal("Corner pop indicator"), c.totemPop)
                    .setDefaultValue(true).setSaveConsumer(v -> c.totemPop = v).build());
            totem.addEntry(e.startBooleanToggle(Component.literal("Hide centre animation"), c.hideCenterTotem)
                    .setDefaultValue(true).setSaveConsumer(v -> c.hideCenterTotem = v).build());
            totem.addEntry(e.startEnumSelector(Component.literal("Corner"), TotemCorner.class, c.totemCorner)
                    .setDefaultValue(TotemCorner.TOP_RIGHT).setSaveConsumer(v -> c.totemCorner = v).build());
            totem.addEntry(e.startIntSlider(Component.literal("Size %"), c.totemSizePct, 100, 300)
                    .setDefaultValue(160).setSaveConsumer(v -> c.totemSizePct = v).build());
            totem.addEntry(e.startBooleanToggle(Component.literal("Screen flash on pop"), c.totemFlash)
                    .setDefaultValue(true)
                    .setTooltip(Component.literal("A brief full-screen red flash the instant your totem pops."))
                    .setSaveConsumer(v -> c.totemFlash = v).build());

            // ---------------- Clean view ----------------
            ConfigCategory clean = b.getOrCreateCategory(Component.literal("Clean View"));
            clean.addEntry(e.startBooleanToggle(Component.literal("No slowness FOV zoom (keeps speed/sprint)"), c.noSlownessFov)
                    .setDefaultValue(true).setSaveConsumer(v -> c.noSlownessFov = v).build());
            clean.addEntry(e.startBooleanToggle(Component.literal("No nausea / portal warp"), c.noNausea)
                    .setDefaultValue(true).setSaveConsumer(v -> c.noNausea = v).build());
            clean.addEntry(e.startBooleanToggle(Component.literal("No hurt tilt"), c.noHurtTilt)
                    .setDefaultValue(true).setSaveConsumer(v -> c.noHurtTilt = v).build());
            clean.addEntry(e.startBooleanToggle(Component.literal("No darkness"), c.noDarkness)
                    .setDefaultValue(true).setSaveConsumer(v -> c.noDarkness = v).build());
            clean.addEntry(e.startBooleanToggle(Component.literal("No blindness"), c.noBlindness)
                    .setDefaultValue(true).setSaveConsumer(v -> c.noBlindness = v).build());
            clean.addEntry(e.startBooleanToggle(Component.literal("Fullbright (night vision, shader-safe)"), c.fullbright)
                    .setDefaultValue(true).setSaveConsumer(v -> c.fullbright = v).build());

            // ---------------- Combat ----------------
            ConfigCategory combat = b.getOrCreateCategory(Component.literal("Combat"));
            combat.addEntry(e.startBooleanToggle(Component.literal("Crystal-only explosion removal"), c.noCrystalExplosion)
                    .setDefaultValue(true).setSaveConsumer(v -> c.noCrystalExplosion = v).build());
            combat.addEntry(e.startBooleanToggle(Component.literal("Cooldown red crosshair flash"), c.cooldownFlash)
                    .setDefaultValue(true).setSaveConsumer(v -> c.cooldownFlash = v).build());
            combat.addEntry(e.startBooleanToggle(Component.literal("Hit marker on landing a hit"), c.showHitMarker)
                    .setDefaultValue(true).setSaveConsumer(v -> c.showHitMarker = v).build());
            combat.addEntry(e.startBooleanToggle(Component.literal("Disable scroll-wheel hotbar switching"), c.disableScrollHotbar)
                    .setDefaultValue(false)
                    .setTooltip(Component.literal(
                            "Stops the scroll wheel from changing your hotbar slot, freeing it up for other uses. "
                            + "GUI/inventory/chat scrolling is unaffected."))
                    .setSaveConsumer(v -> c.disableScrollHotbar = v).build());
            combat.addEntry(e.startEnumSelector(Component.literal("Scroll UP action"),
                            PvpKitConfig.ScrollAction.class, c.scrollUpAction)
                    .setDefaultValue(PvpKitConfig.ScrollAction.NONE)
                    .setTooltip(Component.literal("What scrolling up does, like a keybind on the wheel. Only fires while actually playing (not in menus/chat)."))
                    .setSaveConsumer(v -> c.scrollUpAction = v).build());
            combat.addEntry(e.startEnumSelector(Component.literal("Scroll DOWN action"),
                            PvpKitConfig.ScrollAction.class, c.scrollDownAction)
                    .setDefaultValue(PvpKitConfig.ScrollAction.NONE)
                    .setTooltip(Component.literal("What scrolling down does. Pair with the toggle above to stop the wheel changing hotbar slots first."))
                    .setSaveConsumer(v -> c.scrollDownAction = v).build());
            combat.addEntry(e.startBooleanToggle(Component.literal("Hotbar swap crosshair flash"), c.hotbarSwapFlash)
                    .setDefaultValue(true)
                    .setTooltip(Component.literal("The crosshair flashes green every time you switch hotbar slots -- same mechanism as the red cooldown flash above."))
                    .setSaveConsumer(v -> c.hotbarSwapFlash = v).build());

            // ---------------- Locator ----------------
            // Points a fancy corner arrow at ONE named player. Intended for a friend/
            // teammate you want to find, not an opponent -- see PvpKitClient javadoc.
            ConfigCategory locator = b.getOrCreateCategory(Component.literal("Locator"));
            // Points to your nearest other player. Intended for private LAN play
            // with friends, not public servers -- see PvpKitClient javadoc.
            locator.addEntry(e.startBooleanToggle(Component.literal("Enable Locator Arrow"), c.locatorEnabled)
                    .setDefaultValue(false).setSaveConsumer(v -> c.locatorEnabled = v).build());
            locator.addEntry(e.startBooleanToggle(Component.literal("Disable in Spectator (best-effort lobby check)"), c.locatorDisableInSpectator)
                    .setDefaultValue(true).setSaveConsumer(v -> c.locatorDisableInSpectator = v).build());
            locator.addEntry(e.startBooleanToggle(Component.literal("Show target through walls (glow outline)"), c.locatorShowThroughWalls)
                    .setDefaultValue(false)
                    .setTooltip(Component.literal("Uses vanilla's glow outline (same as Spectator highlight), visible through walls while the target is hidden."))
                    .setSaveConsumer(v -> c.locatorShowThroughWalls = v).build());

            // ---------------- Utility ----------------
            ConfigCategory utility = b.getOrCreateCategory(Component.literal("Utility"));
            utility.addEntry(e.startBooleanToggle(Component.literal("Auto Totem"), c.autoTotem)
                    .setDefaultValue(false)
                    .setTooltip(Component.literal("Keeps a totem in your offhand whenever one is elsewhere in your inventory."))
                    .setSaveConsumer(v -> c.autoTotem = v).build());
            utility.addEntry(e.startBooleanToggle(Component.literal("Auto Eat"), c.autoEat)
                    .setDefaultValue(false)
                    .setTooltip(Component.literal("Switches to hotbar food and eats it once hunger drops below the threshold below."))
                    .setSaveConsumer(v -> c.autoEat = v).build());
            utility.addEntry(e.startIntSlider(Component.literal("Auto Eat hunger threshold"), c.autoEatHungerThreshold, 0, 19)
                    .setDefaultValue(14).setSaveConsumer(v -> c.autoEatHungerThreshold = v).build());
            utility.addEntry(e.startBooleanToggle(Component.literal("Criticals (auto bunny-hop)"), c.criticals)
                    .setDefaultValue(false)
                    .setTooltip(Component.literal("Keeps you hopping while on the ground so every hit lands while airborne -- the same legal timing good PvP players already use, just automated."))
                    .setSaveConsumer(v -> c.criticals = v).build());
            utility.addEntry(e.startBooleanToggle(Component.literal("Module HUD (Right Shift)"), c.moduleHud)
                    .setDefaultValue(true)
                    .setTooltip(Component.literal("Top-right list naming whichever toggles from this mod are currently on."))
                    .setSaveConsumer(v -> c.moduleHud = v).build());

            // ---------------- Xray ----------------
            // Full block transparency: non-whitelisted solid terrain becomes invisible
            // so ore/valuable blocks show through it. Works on any server (the client
            // already has the block data), so treat it like Kill Aura -- fine in your
            // own world, a bannable exploit on servers that don't allow it. See
            // README's Fair use section.
            ConfigCategory xray = b.getOrCreateCategory(Component.literal("Xray"));
            xray.addEntry(e.startBooleanToggle(Component.literal("Enable Xray"), c.xrayEnabled)
                    .setDefaultValue(false)
                    .setTooltip(Component.literal(
                            "Makes ordinary terrain invisible so the ore types below show through it. "
                            + "Works on any server since the block data is already sent to your client -- "
                            + "most servers ban this, see the README's Fair use section."))
                    .setSaveConsumer(v -> c.xrayEnabled = v).build());
            xray.addEntry(e.startBooleanToggle(Component.literal("Coal"), c.xrayCoal)
                    .setDefaultValue(true).setSaveConsumer(v -> c.xrayCoal = v).build());
            xray.addEntry(e.startBooleanToggle(Component.literal("Iron"), c.xrayIron)
                    .setDefaultValue(true).setSaveConsumer(v -> c.xrayIron = v).build());
            xray.addEntry(e.startBooleanToggle(Component.literal("Copper"), c.xrayCopper)
                    .setDefaultValue(true).setSaveConsumer(v -> c.xrayCopper = v).build());
            xray.addEntry(e.startBooleanToggle(Component.literal("Gold (incl. Nether Gold Ore)"), c.xrayGold)
                    .setDefaultValue(true).setSaveConsumer(v -> c.xrayGold = v).build());
            xray.addEntry(e.startBooleanToggle(Component.literal("Redstone"), c.xrayRedstone)
                    .setDefaultValue(true).setSaveConsumer(v -> c.xrayRedstone = v).build());
            xray.addEntry(e.startBooleanToggle(Component.literal("Lapis"), c.xrayLapis)
                    .setDefaultValue(true).setSaveConsumer(v -> c.xrayLapis = v).build());
            xray.addEntry(e.startBooleanToggle(Component.literal("Emerald"), c.xrayEmerald)
                    .setDefaultValue(true).setSaveConsumer(v -> c.xrayEmerald = v).build());
            xray.addEntry(e.startBooleanToggle(Component.literal("Diamond"), c.xrayDiamond)
                    .setDefaultValue(true).setSaveConsumer(v -> c.xrayDiamond = v).build());
            xray.addEntry(e.startBooleanToggle(Component.literal("Ancient Debris"), c.xrayAncientDebris)
                    .setDefaultValue(true).setSaveConsumer(v -> c.xrayAncientDebris = v).build());
            xray.addEntry(e.startBooleanToggle(Component.literal("Nether Quartz"), c.xrayNetherQuartz)
                    .setDefaultValue(true).setSaveConsumer(v -> c.xrayNetherQuartz = v).build());
            xray.addEntry(e.startBooleanToggle(Component.literal("Chests / Barrels / Shulker Boxes / Spawners"), c.xrayContainers)
                    .setDefaultValue(false).setSaveConsumer(v -> c.xrayContainers = v).build());

            // ---------------- Multi Bind ----------------
            // Ordered: slot 1 resolves before slot 2, etc. Numbered rows rather than an
            // add/remove list specifically so the firing order is visible and stable.
            ConfigCategory multi = b.getOrCreateCategory(Component.literal("Multi Bind"));
            MultiBindConfig mb = MultiBindConfig.get();
            for (int i = 0; i < MultiBindConfig.SLOT_COUNT; i++) {
                MultiBindConfig.Slot slot = mb.slots.get(i);
                int n = i + 1;
                multi.addEntry(e.startEnumSelector(Component.literal("Binding " + n + " - Action"),
                                MultiBindConfig.MultiAction.class, slot.action)
                        .setDefaultValue(MultiBindConfig.MultiAction.NONE)
                        .setTooltip(Component.literal(
                                "Actions fire in the order you ADD them, not by row number. Setting a row "
                                + "back to None and re-using it later puts it at the end of the order."))
                        .setSaveConsumer(v -> {
                            // Stamp the insertion number only on the None -> action transition,
                            // so editing an existing binding's action keeps its place in the order.
                            if (slot.action == MultiBindConfig.MultiAction.NONE
                                    && v != MultiBindConfig.MultiAction.NONE) {
                                slot.addedSeq = MultiBindConfig.nextSeq();
                            } else if (v == MultiBindConfig.MultiAction.NONE) {
                                slot.addedSeq = -1;
                            }
                            slot.action = v;
                        }).build());
                InputConstants.Key current = InputConstants.getKey(slot.key);
                multi.addEntry(e.startKeyCodeField(Component.literal(n + ". Key"),
                                current == null ? InputConstants.UNKNOWN : current)
                        .setDefaultValue(InputConstants.UNKNOWN)
                        .setKeySaveConsumer(k -> slot.key = k.getName())
                        .build());
            }

            // ---------------- No Cooldown ----------------
            // Private-world / LAN-with-friends sandbox toggles. Effective in worlds
            // you host; damage/durability/flight are server-authoritative elsewhere,
            // so this page does nothing meaningful (and would be rule-breaking) on a
            // public server -- see the mixin javadocs in com.catting.nocooldown.mixin.
            ConfigCategory noCooldown = b.getOrCreateCategory(Component.literal("No Cooldown"));
            noCooldown.addEntry(e.startEnumSelector(
                            Component.literal("Mode"),
                            NoCooldownConfig.Mode.class,
                            nc.mode)
                    .setDefaultValue(NoCooldownConfig.Mode.DISABLED)
                    .setTooltip(Component.literal("Click to cycle: Disabled -> No spear cooldown -> No cooldown"))
                    .setSaveConsumer(v -> nc.mode = v)
                    .build());
            noCooldown.addEntry(e.startBooleanToggle(Component.literal("Unlimited Durability"), nc.unlimitedDurability)
                    .setDefaultValue(false).setSaveConsumer(v -> nc.unlimitedDurability = v).build());
            noCooldown.addEntry(e.startBooleanToggle(Component.literal("Instant Use (eat/drink/bow/shield)"), nc.instantUse)
                    .setDefaultValue(false).setSaveConsumer(v -> nc.instantUse = v).build());
            noCooldown.addEntry(e.startBooleanToggle(Component.literal("No Damage"), nc.noDamage)
                    .setDefaultValue(false).setSaveConsumer(v -> nc.noDamage = v).build());
            noCooldown.addEntry(e.startBooleanToggle(Component.literal("Flight"), nc.flightEnabled)
                    .setDefaultValue(false).setSaveConsumer(v -> nc.flightEnabled = v).build());
            noCooldown.addEntry(e.startBooleanToggle(Component.literal("Infinite Hunger"), nc.infiniteHunger)
                    .setDefaultValue(false).setSaveConsumer(v -> nc.infiniteHunger = v).build());
            noCooldown.addEntry(e.startBooleanToggle(Component.literal("Kill Aura"), nc.killAura)
                    .setDefaultValue(false)
                    .setTooltip(Component.literal(
                            "Auto-attacks the nearest valid target once your attack meter is full "
                            + "(full-strength vanilla hits, line of sight required). Unlike the other "
                            + "toggles here, attacks are client-initiated and DO work on servers you "
                            + "don't host -- keep this to private worlds / LAN with friends."))
                    .setSaveConsumer(v -> nc.killAura = v).build());
            noCooldown.addEntry(e.startIntSlider(Component.literal("Kill Aura range (blocks)"), nc.killAuraRange, 2, 10)
                    .setDefaultValue(3)
                    .setTooltip(Component.literal(
                            "How far away a target can be picked. NOTE: this only widens target "
                            + "SELECTION -- the server independently validates attack distance "
                            + "against your interaction-range attribute (~3 blocks) and rejects "
                            + "anything past it, so values well above that won't actually land hits."))
                    .setSaveConsumer(v -> nc.killAuraRange = v).build());
            noCooldown.addEntry(e.startEnumSelector(Component.literal("Kill Aura targets"),
                            NoCooldownConfig.KillAuraTargets.class, nc.killAuraTargets)
                    .setDefaultValue(NoCooldownConfig.KillAuraTargets.PLAYERS)
                    .setSaveConsumer(v -> nc.killAuraTargets = v).build());
            // "All Off" button. Cloth Config has no plain action-button widget, so this
            // is a toggle that always displays unchecked (its default is never read
            // from persisted state) and, if checked when you save, resets everything
            // above it. Placed LAST so its save-consumer runs after the others,
            // guaranteeing it wins even if you changed something else in the same save.
            noCooldown.addEntry(e.startBooleanToggle(Component.literal("All Off (check + Save to reset everything above)"), false)
                    .setDefaultValue(false)
                    .setSaveConsumer(v -> {
                        if (v) {
                            nc.mode = NoCooldownConfig.Mode.DISABLED;
                            nc.unlimitedDurability = false;
                            nc.instantUse = false;
                            nc.noDamage = false;
                            nc.flightEnabled = false;
                            nc.infiniteHunger = false;
                            nc.killAura = false;
                        }
                    }).build());

            return b.build();
        };
    }
}

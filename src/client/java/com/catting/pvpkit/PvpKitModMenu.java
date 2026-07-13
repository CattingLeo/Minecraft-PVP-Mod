package com.catting.pvpkit;

import com.catting.nocooldown.NoCooldownConfig;
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
                    .setTitle(Component.literal("PVP"));
            b.setSavingRunnable(() -> {
                PvpKitConfig.save();
                PvpKitClient.applyConfig();
                NoCooldownConfig.save();
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
                        }
                    }).build());

            return b.build();
        };
    }
}

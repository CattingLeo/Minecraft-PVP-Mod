package com.catting.pvpkit;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.catting.nocooldown.NoCooldownConfig;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.phys.Vec3;

/**
 * PvP Kit (client-side). Configure in Mod Menu -> PvP Kit.
 * The public static flags below are read by the mixins; applyConfig() syncs them
 * from PvpKitConfig whenever the config screen saves. Colours are ARGB.
 */
@Environment(EnvType.CLIENT)
public class PvpKitClient implements ClientModInitializer {

    public static final String MOD_ID = "pvpkit";

    // ---- Live flags (synced from config; mixins read these) ----
    public static boolean SHOW_FPS = true;
    public static boolean SHOW_CPS = true;
    public static boolean SHOW_PING = true;
    public static boolean RGB_TEXT = true;
    public static int ANCHOR_X = 6;
    public static int ANCHOR_Y = -28;

    public static boolean TOTEM_POP = true;
    public static int TOTEM_CORNER = 1;     // 0 TL, 1 TR, 2 BL, 3 BR
    public static boolean HIDE_CENTER_TOTEM = true;
    public static float TOTEM_PEAK = 1.6f;
    public static boolean TOTEM_FLASH = true;

    public static boolean NO_SLOWNESS_FOV = true;
    public static boolean NO_NAUSEA = true;
    public static boolean NO_HURT_TILT = true;
    public static boolean NO_DARKNESS = true;
    public static boolean NO_BLINDNESS = true;
    public static boolean FULLBRIGHT = true;

    // ---- Locator arrow (tracks ONE named player; see README for fair-use note) ----
    public static boolean LOCATOR_ENABLED = false;
    public static boolean LOCATOR_DISABLE_IN_SPECTATOR = true;
    public static boolean LOCATOR_SHOW_THROUGH_WALLS = false;

    public static boolean NO_CRYSTAL_EXPLOSION = true;
    public static boolean COOLDOWN_FLASH = true;
    public static boolean SHOW_HIT_MARKER = true;
    public static boolean HOTBAR_SWAP_FLASH = true;
    public static boolean DISABLE_SCROLL_HOTBAR = false;

    // ---- Utility (Auto Totem / Auto Eat / Criticals / module HUD) ----
    public static boolean AUTO_TOTEM = false;
    public static boolean AUTO_EAT = false;
    public static int AUTO_EAT_HUNGER_THRESHOLD = 14;
    public static boolean CRITICALS = false;
    public static boolean MODULE_HUD = true;

    /** Copy persisted config -> live flags. Called on load and on config save. */
    public static void applyConfig() {
        PvpKitConfig c = PvpKitConfig.get();
        SHOW_FPS = c.showFps; SHOW_CPS = c.showCps; SHOW_PING = c.showPing; RGB_TEXT = c.rainbowText;
        ANCHOR_X = c.hudX; ANCHOR_Y = c.hudY;
        TOTEM_POP = c.totemPop; HIDE_CENTER_TOTEM = c.hideCenterTotem;
        TOTEM_CORNER = c.totemCorner.ordinal(); TOTEM_PEAK = c.totemSizePct / 100.0f;
        TOTEM_FLASH = c.totemFlash;
        NO_SLOWNESS_FOV = c.noSlownessFov; NO_NAUSEA = c.noNausea; NO_HURT_TILT = c.noHurtTilt;
        NO_DARKNESS = c.noDarkness; NO_BLINDNESS = c.noBlindness;
        FULLBRIGHT = c.fullbright;
        LOCATOR_ENABLED = c.locatorEnabled;
        LOCATOR_DISABLE_IN_SPECTATOR = c.locatorDisableInSpectator;
        LOCATOR_SHOW_THROUGH_WALLS = c.locatorShowThroughWalls;
        NO_CRYSTAL_EXPLOSION = c.noCrystalExplosion; COOLDOWN_FLASH = c.cooldownFlash;
        SHOW_HIT_MARKER = c.showHitMarker;
        HOTBAR_SWAP_FLASH = c.hotbarSwapFlash; DISABLE_SCROLL_HOTBAR = c.disableScrollHotbar;
        AUTO_TOTEM = c.autoTotem; AUTO_EAT = c.autoEat; AUTO_EAT_HUNGER_THRESHOLD = c.autoEatHungerThreshold;
        CRITICALS = c.criticals; MODULE_HUD = c.moduleHud;
    }

    // ---- Totem corner pop ----
    private static final long TOTEM_POP_MS = 1500L;
    private static long totemPopStart = -1L;
    private static final long TOTEM_FLASH_MS = 450L;
    private static long totemFlashStart = -1L;
    /**
     * The real item, so the corner pop shows the actual totem sprite (not a coloured
     * square). Built lazily on first render, NOT as a static field initializer --
     * constructing an ItemStack from a Holder<Item> that early crashes the game with
     * "Components not bound yet" (item registry components aren't bound until partway
     * through Minecraft's own bootstrap, but this mod's class -- and therefore this
     * field -- loads earlier than that, during Fabric's client-entrypoint invocation).
     * By the time renderTotemPop() actually runs, bootstrap is long finished.
     */
    private static ItemStack totemStack;

    private static ItemStack totemStack() {
        if (totemStack == null) totemStack = new ItemStack(Items.TOTEM_OF_UNDYING);
        return totemStack;
    }

    // ---- Crystal-only explosion removal ----
    private static final Map<Integer, double[]> presentCrystals = new HashMap<>();
    private static final ArrayDeque<double[]> recentRemovals = new ArrayDeque<>();
    private static final double CRYSTAL_RADIUS_SQ = 6.25;

    // ---- Cooldown crosshair flash ----
    private static final long COOLDOWN_FLASH_MS = 280L;
    private static final Identifier CROSSHAIR_SPRITE = Identifier.withDefaultNamespace("hud/crosshair");
    private static final int CROSSHAIR_SIZE = 15;
    private static long cooldownFlashStart = -1L;
    private boolean prevCooldown = false;

    // ---- Hotbar swap crosshair flash (green, reuses the crosshair-flash mechanism above) ----
    private static long hotbarFlashStart = -1L;

    private static final long HIT_MARKER_MS = 180L;
    private static long hitMarkerStart = -1L;
    private static Player lastGlowTarget = null;

    /** Called from HitMarkerMixin when the local player's attack swing lands. */
    public static void triggerHitMarker() {
        hitMarkerStart = System.currentTimeMillis();
    }

    // ---- clean-view option change tracking ----
    private Boolean lastNausea;
    private Boolean lastHurt;

    // ---- hotbar swap sound ----
    private int lastHotbarSlot = -1;

    // ---- CPS ----
    private static final long CPS_WINDOW_MS = 1000L;
    private final ArrayDeque<Long> leftClicks = new ArrayDeque<>();
    private final ArrayDeque<Long> rightClicks = new ArrayDeque<>();
    private boolean prevLeft;
    private boolean prevRight;

    public static void triggerTotemPop() {
        long now = System.currentTimeMillis();
        totemPopStart = now;
        totemFlashStart = now;
    }

    public static synchronized void recordCrystalLoad(int id, double x, double y, double z) {
        presentCrystals.put(id, new double[]{x, y, z});
    }

    public static synchronized void recordCrystalRemoval(int id, double x, double y, double z) {
        presentCrystals.remove(id);
        recentRemovals.addLast(new double[]{x, y, z, System.currentTimeMillis()});
        while (recentRemovals.size() > 64) recentRemovals.pollFirst();
    }

    public static synchronized boolean nearRecentCrystal(double x, double y, double z) {
        for (double[] c : presentCrystals.values()) {
            if (dist2(c, x, y, z) <= CRYSTAL_RADIUS_SQ) return true;
        }
        long now = System.currentTimeMillis();
        boolean found = false;
        var it = recentRemovals.iterator();
        while (it.hasNext()) {
            double[] c = it.next();
            if (now - (long) c[3] > 600L) { it.remove(); continue; }
            if (dist2(c, x, y, z) <= CRYSTAL_RADIUS_SQ) found = true;
        }
        return found;
    }

    private static double dist2(double[] c, double x, double y, double z) {
        double dx = c[0] - x, dy = c[1] - y, dz = c[2] - z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public void onInitializeClient() {
        PvpKitConfig.load();
        applyConfig();
        PvpKitKeybinds.register();
        PracticeBotManager.init();
        FreecamManager.init();
        ScrollKeybind.init();

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof EndCrystal) {
                recordCrystalLoad(entity.getId(), entity.getX(), entity.getY(), entity.getZ());
            }
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof EndCrystal) {
                recordCrystalRemoval(entity.getId(), entity.getX(), entity.getY(), entity.getZ());
            }
        });
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "pvp_kit_hud"),
                (graphics, delta) -> render(graphics, delta));
    }

    private void onClientTick(Minecraft mc) {
        // clean-view via vanilla effect scaling; re-applied whenever the toggle changes
        if (mc.options != null) {
            if (lastNausea == null || lastNausea != NO_NAUSEA) {
                mc.options.screenEffectScale().set(NO_NAUSEA ? 0.0 : 1.0);
                lastNausea = NO_NAUSEA;
            }
            if (lastHurt == null || lastHurt != NO_HURT_TILT) {
                mc.options.damageTiltStrength().set(NO_HURT_TILT ? 0.0 : 1.0);
                lastHurt = NO_HURT_TILT;
            }
        }
        if (mc.player == null) return;

        if (FULLBRIGHT) {
            MobEffectInstance cur = mc.player.getEffect(MobEffects.NIGHT_VISION);
            if (cur == null || cur.endsWithin(220)) {
                mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false, false));
            }
        }

        if (COOLDOWN_FLASH) {
            var cds = mc.player.getCooldowns();
            boolean nowCd = cds.isOnCooldown(mc.player.getMainHandItem())
                    || cds.isOnCooldown(mc.player.getOffhandItem());
            if (prevCooldown && !nowCd) cooldownFlashStart = System.currentTimeMillis();
            prevCooldown = nowCd;
        }

        if (mc.level != null) {
            int slot = mc.player.getInventory().getSelectedSlot();
            if (lastHotbarSlot != -1 && slot != lastHotbarSlot && HOTBAR_SWAP_FLASH) {
                hotbarFlashStart = System.currentTimeMillis();
            }
            lastHotbarSlot = slot;
        }

        if (CRITICALS) updateCriticals(mc);
        if (AUTO_TOTEM) updateAutoTotem(mc);
        if (AUTO_EAT) updateAutoEat(mc);
    }

    /**
     * Auto-crit: rather than faking the server-validated fall-distance check
     * (which a real server computes itself from your synced position, so a
     * purely client-side "pretend I fell" flag wouldn't actually apply),
     * this just keeps you continuously hopping while on the ground -- the
     * same legal, entirely-manual "bunny hop" technique good PvP players
     * already use to crit every hit, just automated so you don't have to
     * time it yourself. `LivingEntity#jumpFromGround` is the exact call the
     * space bar itself triggers.
     */
    private void updateCriticals(Minecraft mc) {
        if (mc.player == null || !mc.player.onGround() || mc.player.isPassenger()) return;
        mc.player.jumpFromGround();
    }

    /**
     * Auto Totem: keeps your offhand topped up with a totem whenever one is
     * anywhere else in your inventory. Implemented as the same 3-click
     * pickup/place/pickup sequence a real player performs when dragging one
     * item onto another in the inventory screen -- PICKUP the totem stack
     * (onto the cursor), PICKUP the offhand slot (places the totem, cursor
     * now holds whatever was in offhand), PICKUP the original slot again
     * (places that back where the totem was). No inventory screen needs to
     * be open for handleContainerInput to accept these.
     *
     * Slot numbers use the standard InventoryMenu layout (protocol-stable
     * since item offhand was added): 9-35 main storage, 36-44 hotbar, 45
     * offhand. Runs at most once per tick and only once offhand actually
     * needs it, so it doesn't fight a real player's own inventory clicks.
     */
    private static final int MENU_SLOT_OFFHAND = 45;

    private void updateAutoTotem(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null || mc.getConnection() == null) return;
        if (!mc.mouseHandler.isMouseGrabbed()) return; // don't fight an open inventory/GUI
        if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;

        var inv = mc.player.getInventory();
        int found = -1;
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                found = i;
                break;
            }
        }
        if (found < 0) return;

        int menuSlot = found < 9 ? 36 + found : found;
        int containerId = mc.player.inventoryMenu.containerId;
        mc.gameMode.handleContainerInput(containerId, menuSlot, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(containerId, MENU_SLOT_OFFHAND, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(containerId, menuSlot, 0, ContainerInput.PICKUP, mc.player);
    }

    /**
     * Auto Eat: below the configured hunger threshold, switches to the first
     * food item found in the hotbar and holds "use" on it every tick (the
     * same call a real held-right-click drives) until hunger recovers or the
     * food runs out. Deliberately hotbar-only -- reaching into the backpack
     * for food would need the same slot-swap machinery as Auto Totem, and
     * competing with Auto Totem for the offhand slot -- so keep a food item
     * on your hotbar for this to help. Does not restore your previous
     * hotbar selection afterward (switching slots mid-bite cancels the
     * bite), same as a real player choosing to swap by hand.
     */
    private void updateAutoEat(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null || mc.getConnection() == null) return;
        if (!mc.mouseHandler.isMouseGrabbed()) return;
        if (mc.player.isUsingItem()) return;
        if (mc.player.getFoodData().getFoodLevel() >= AUTO_EAT_HUNGER_THRESHOLD) return;

        var inv = mc.player.getInventory();
        int foodSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.has(DataComponents.FOOD)) {
                foodSlot = i;
                break;
            }
        }
        if (foodSlot < 0) return;

        if (inv.getSelectedSlot() != foodSlot) {
            inv.setSelectedSlot(foodSlot);
            mc.getConnection().getConnection().send(new ServerboundSetCarriedItemPacket(foodSlot));
        }
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
    }

    // ---- Toast messages (used by keybind actions like screenshot/recording) ----
    // Deliberately does NOT touch Minecraft's chat/Gui API -- two different guesses
    // at that (displayClientMessage, then gui.getChat()) both failed to compile in
    // this exact 26.2 build. This reuses ONLY graphics.text(), the same call that's
    // already confirmed working for FPS/CPS/Ping/hit-marker in your actual game.
    private static String toastText = null;
    private static long toastUntil = 0L;
    private static final long TOAST_MS = 2500L;

    /** Shows a short on-screen message near the top of the screen for ~2.5s. */
    public static void showToast(String text) {
        toastText = text;
        toastUntil = System.currentTimeMillis() + TOAST_MS;
    }

    private void renderToast(GuiGraphicsExtractor g, Minecraft mc) {
        if (toastText == null) return;
        long remaining = toastUntil - System.currentTimeMillis();
        if (remaining <= 0) {
            toastText = null;
            return;
        }
        float fade = remaining < 400 ? remaining / 400.0f : 1.0f;
        int alpha = Math.max(0, Math.min(255, (int) (fade * 255)));
        int color = (alpha << 24) | 0xFFFFFF;
        int w = mc.getWindow().getGuiScaledWidth();
        int tw = mc.font.width(toastText);
        g.text(mc.font, toastText, (w - tw) / 2, 20, color, true);
    }

    private void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        if (TOTEM_FLASH) renderTotemFlash(graphics, w, h); // full-screen wash, drawn first so HUD text sits on top of it
        if (COOLDOWN_FLASH) renderCrosshairFlash(graphics, mc);
        if (HOTBAR_SWAP_FLASH) renderHotbarSwapFlash(graphics, mc);
        if (SHOW_HIT_MARKER) renderHitMarker(graphics, mc);
        renderLocatorArrow(graphics, mc); // always called so it can self-clean any glow tag
        renderToast(graphics, mc);

        long now = System.currentTimeMillis();
        boolean leftDown = mc.options.keyAttack.isDown();
        boolean rightDown = mc.options.keyUse.isDown();
        if (leftDown && !prevLeft) leftClicks.addLast(now);
        if (rightDown && !prevRight) rightClicks.addLast(now);
        prevLeft = leftDown;
        prevRight = rightDown;
        purge(leftClicks, now);
        purge(rightClicks, now);

        int ox = ANCHOR_X >= 0 ? ANCHOR_X : w + ANCHOR_X;
        int oy = ANCHOR_Y >= 0 ? ANCHOR_Y : h + ANCHOR_Y;

        int col = RGB_TEXT ? rainbow() : 0xFFFFFFFF;
        int y = oy;
        if (SHOW_FPS) {
            graphics.text(mc.font, "FPS " + mc.getFps(), ox, y, col, true);
            y += 10;
        }
        if (SHOW_CPS) {
            graphics.text(mc.font, "CPS " + leftClicks.size() + "  R " + rightClicks.size(), ox, y, col, true);
            y += 10;
        }
        if (SHOW_PING) {
            graphics.text(mc.font, "Ping " + pingText(mc), ox, y, col, true);
        }

        if (TOTEM_POP) renderTotemPop(graphics, w, h);
        if (MODULE_HUD) renderModuleHud(graphics, mc, w);
    }

    /**
     * A Meteor Client-style "module list" -- a vertical, right-aligned list
     * naming whichever toggles from this mod are currently ON, top-right
     * corner. Purely a status readout of this mod's OWN existing settings
     * (nothing here does anything by itself); toggle with Right Shift
     * (Options -> Controls -> Key Binds -> PvP Kit -> "Toggle Module HUD").
     */
    private void renderModuleHud(GuiGraphicsExtractor g, Minecraft mc, int screenW) {
        NoCooldownConfig nc = NoCooldownConfig.get();
        java.util.List<String> active = new java.util.ArrayList<>();
        if (FULLBRIGHT) active.add("Fullbright");
        if (LOCATOR_ENABLED) active.add("Locator");
        if (COOLDOWN_FLASH) active.add("Cooldown Flash");
        if (AUTO_TOTEM) active.add("Auto Totem");
        if (AUTO_EAT) active.add("Auto Eat");
        if (CRITICALS) active.add("Criticals");
        if (FreecamManager.isActive()) active.add("Freecam");
        if (nc.mode != NoCooldownConfig.Mode.DISABLED) active.add(nc.mode.label);
        if (nc.unlimitedDurability) active.add("Unlimited Durability");
        if (nc.instantUse) active.add("Instant Use");
        if (nc.noDamage) active.add("No Damage");
        if (nc.flightEnabled) active.add("Flight");
        if (nc.infiniteHunger) active.add("Infinite Hunger");
        if (nc.killAura) active.add("Kill Aura");
        if (active.isEmpty()) return;

        int y = 4;
        for (String name : active) {
            int tw = mc.font.width(name);
            int x = screenW - tw - 6;
            g.fill(x - 3, y - 1, screenW - 2, y + 9, 0x60000000);
            g.text(mc.font, name, x, y, 0xFFE8B84B, true);
            y += 11;
        }
    }

    /** Reads the tab-list latency for the local player. Long-standing, stable vanilla API. */
    private static String pingText(Minecraft mc) {
        var conn = mc.getConnection();
        if (conn != null && mc.player != null) {
            var info = conn.getPlayerInfo(mc.player.getUUID());
            if (info != null) return info.getLatency() + "ms";
        }
        return "--";
    }

    private void renderCrosshairFlash(GuiGraphicsExtractor g, Minecraft mc) {
        if (cooldownFlashStart < 0) return;
        if (System.currentTimeMillis() - cooldownFlashStart > COOLDOWN_FLASH_MS) {
            cooldownFlashStart = -1L;
            return;
        }
        int x = (mc.getWindow().getGuiScaledWidth() - CROSSHAIR_SIZE) / 2;
        int y = (mc.getWindow().getGuiScaledHeight() - CROSSHAIR_SIZE) / 2;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, CROSSHAIR_SPRITE,
                x, y, CROSSHAIR_SIZE, CROSSHAIR_SIZE, 0xFFFF0000);
    }

    /** Same crosshair-flash mechanism as the cooldown one above, green, triggered on hotbar swap instead. */
    private void renderHotbarSwapFlash(GuiGraphicsExtractor g, Minecraft mc) {
        if (hotbarFlashStart < 0) return;
        if (System.currentTimeMillis() - hotbarFlashStart > COOLDOWN_FLASH_MS) {
            hotbarFlashStart = -1L;
            return;
        }
        int x = (mc.getWindow().getGuiScaledWidth() - CROSSHAIR_SIZE) / 2;
        int y = (mc.getWindow().getGuiScaledHeight() - CROSSHAIR_SIZE) / 2;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, CROSSHAIR_SPRITE,
                x, y, CROSSHAIR_SIZE, CROSSHAIR_SIZE, 0xFF00FF00);
    }

    /**
     * White corner brackets around the crosshair, fading out. Deliberately a
     * different shape/colour from the red cooldown flash so the two are never
     * confused. Axis-aligned only (fill), so it uses only the render API
     * already confirmed compiling elsewhere in this class.
     */
    private void renderHitMarker(GuiGraphicsExtractor g, Minecraft mc) {
        if (hitMarkerStart < 0) return;
        long elapsed = System.currentTimeMillis() - hitMarkerStart;
        if (elapsed > HIT_MARKER_MS) {
            hitMarkerStart = -1L;
            return;
        }
        float t = elapsed / (float) HIT_MARKER_MS;
        int alpha = Math.max(0, 255 - (int) (t * 255));
        int color = (alpha << 24) | 0xFFFFFF;

        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int cy = mc.getWindow().getGuiScaledHeight() / 2;
        int offset = 6;   // distance from centre to each bracket's corner
        int arm = 4;      // bracket arm length
        int thick = 2;    // bracket thickness

        // top-left
        g.fill(cx - offset - arm, cy - offset, cx - offset, cy - offset + thick, color);
        g.fill(cx - offset, cy - offset - arm, cx - offset + thick, cy - offset, color);
        // top-right
        g.fill(cx + offset, cy - offset, cx + offset + arm, cy - offset + thick, color);
        g.fill(cx + offset - thick, cy - offset - arm, cx + offset, cy - offset, color);
        // bottom-left
        g.fill(cx - offset - arm, cy + offset - thick, cx - offset, cy + offset, color);
        g.fill(cx - offset, cy + offset, cx - offset + thick, cy + offset + arm, color);
        // bottom-right
        g.fill(cx + offset, cy + offset - thick, cx + offset + arm, cy + offset, color);
        g.fill(cx + offset - thick, cy + offset, cx + offset, cy + offset + arm, color);
    }

    /**
     * Points a fancy arrow at the screen edge/corner toward your nearest other player.
     * No name entry, one on/off toggle. Hides automatically once they're both within
     * your rough FOV AND you have a clear line of sight (real wall-blocking raycast via
     * Level#clip — long-standing vanilla API, not a mixin, so this part has no
     * "wrong class name" risk).
     *
     * INTENDED USE: private LAN sessions with friends you've invited yourself, where
     * everyone present is someone you know and there's no server rule/anti-cheat this
     * conflicts with — comparable to a co-op friend-finder. On a public competitive
     * server against strangers who haven't agreed to it, this same feature is a generic
     * enemy tracer/ESP, the exact thing most PvP anti-cheats are built to catch — don't
     * use it there.
     *
     * "Lobby" can't be reliably detected client-side (that's server-side info), so the
     * one concrete auto-disable is Spectator mode (LOCATOR_DISABLE_IN_SPECTATOR).
     */
    private void renderLocatorArrow(GuiGraphicsExtractor g, Minecraft mc) {
        if (!LOCATOR_ENABLED || mc.player == null || mc.level == null
                || (LOCATOR_DISABLE_IN_SPECTATOR && mc.player.isSpectator())) {
            clearGlowTarget();
            return;
        }

        Player target = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            double d = p.distanceToSqr(mc.player);
            if (d < bestDist) {
                bestDist = d;
                target = p;
            }
        }
        if (target == null) {
            clearGlowTarget();
            return; // no other players loaded/nearby right now
        }

        Vec3 eye = mc.player.getEyePosition(1.0f);
        Vec3 targetEye = target.getEyePosition(1.0f);

        // Rough horizontal FOV check (fixed estimate rather than reading the live FOV
        // option, to avoid depending on another renameable API for a cosmetic check).
        double dx = targetEye.x - eye.x;
        double dz = targetEye.z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float playerYaw = mc.player.getViewYRot(1.0f);
        float angleDiff = wrapDegrees(targetYaw - playerYaw);
        boolean inFov = Math.abs(angleDiff) < 40.0f; // ~80 deg horizontal FOV estimate

        boolean losBlocked = true;
        if (inFov) {
            var clip = new ClipContext(eye, targetEye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player);
            var hit = mc.level.clip(clip);
            losBlocked = hit.getType() != HitResult.Type.MISS;
        }
        boolean hidden = !(inFov && !losBlocked);
        updateGlowTarget(hidden ? target : null);
        if (!hidden) return; // you can actually see them -> hide the arrow too

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int cx = w / 2, cy = h / 2;

        // Direction from screen centre, in screen space: 0 rad = up, clockwise.
        double angleRad = Math.toRadians(angleDiff);
        double dirX = Math.sin(angleRad);
        double dirY = -Math.cos(angleRad);

        // Clamp to the screen's edge rectangle (naturally lands in a corner for
        // diagonal directions) with a margin so it doesn't sit flush on the pixel edge.
        int margin = 22;
        double halfW = w / 2.0 - margin, halfH = h / 2.0 - margin;
        double scale = Math.min(
                dirX != 0 ? halfW / Math.abs(dirX) : Double.MAX_VALUE,
                dirY != 0 ? halfH / Math.abs(dirY) : Double.MAX_VALUE);
        int ax = cx + (int) Math.round(dirX * scale);
        int ay = cy + (int) Math.round(dirY * scale);

        // Gentle pulse so it reads as "fancy" rather than static.
        float pulse = 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 200.0);
        int alpha = (int) (255 * pulse);
        int gold = (alpha << 24) | 0xE8B84B;

        drawRotatedTriangle(g, ax, ay, angleRad, 9, 7, gold);

        String label = (int) dist + "m";
        int lw = mc.font.width(label);
        int tx = ax - lw / 2;
        int ty = ay + (dirY >= 0 ? 10 : -18);
        g.text(mc.font, label, tx, ty, 0xFFFFFFFF, true);
    }

    /** Wraps a degree value into the range -180 to 180. */
    private static float wrapDegrees(float deg) {
        float d = deg % 360.0f;
        if (d >= 180.0f) d -= 360.0f;
        if (d < -180.0f) d += 360.0f;
        return d;
    }

    /**
     * Rasterizes a small triangle "arrowhead" pointing along angleRad (0 = up,
     * clockwise), tip-first, using only axis-aligned fill() calls (scanline fill) —
     * deliberately avoids any pose-rotation API so this has zero new-API compile risk.
     */
    private void drawRotatedTriangle(GuiGraphicsExtractor g, int cx, int cy, double angleRad,
                                      int length, int halfWidth, int color) {
        double s = Math.sin(angleRad), c = Math.cos(angleRad);
        double[] lx = {0, -halfWidth, halfWidth};
        double[] ly = {-length, length * 0.6, length * 0.6};
        int[] px = new int[3], py = new int[3];
        for (int i = 0; i < 3; i++) {
            px[i] = cx + (int) Math.round(lx[i] * c + ly[i] * s);
            py[i] = cy + (int) Math.round(-lx[i] * s + ly[i] * c);
        }
        int minY = Math.min(py[0], Math.min(py[1], py[2]));
        int maxY = Math.max(py[0], Math.max(py[1], py[2]));
        for (int y = minY; y <= maxY; y++) {
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            for (int e = 0; e < 3; e++) {
                int x1 = px[e], y1 = py[e], x2 = px[(e + 1) % 3], y2 = py[(e + 1) % 3];
                if (y1 == y2) continue;
                if ((y >= y1 && y < y2) || (y >= y2 && y < y1)) {
                    double t = (y - y1) / (double) (y2 - y1);
                    double x = x1 + t * (x2 - x1);
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                }
            }
            if (minX <= maxX) {
                g.fill((int) Math.round(minX), y, (int) Math.round(maxX) + 1, y + 1, color);
            }
        }
    }

    /**
     * Uses vanilla's built-in "glowing tag" — the same client-only mechanic Spectator
     * mode uses to outline whatever entity you're looking at through walls — rather
     * than building custom translucent-through-wall rendering. This reuses proven,
     * already-working vanilla rendering instead of a new render mixin, so it carries
     * far less risk than most of today's other changes: if the method name below is
     * off, it's a plain compile error naming the exact symbol, not a runtime crash.
     *
     * Only active while LOCATOR_SHOW_THROUGH_WALLS is on AND the target is currently
     * hidden from you (same visibility check as the arrow). Purely a local visual
     * override — never touches the server, never affects anyone else's game.
     */
    private void updateGlowTarget(Player target) {
        if (!LOCATOR_SHOW_THROUGH_WALLS) {
            clearGlowTarget();
            return;
        }
        if (target == lastGlowTarget) return; // already correct, nothing to change
        clearGlowTarget();
        if (target != null) {
            target.setGlowingTag(true);
            lastGlowTarget = target;
        }
    }

    private void clearGlowTarget() {
        if (lastGlowTarget != null) {
            lastGlowTarget.setGlowingTag(false);
            lastGlowTarget = null;
        }
    }

    /**
     * Corner totem pop. Renders the ACTUAL totem-of-undying item sprite (via
     * GuiGraphicsExtractor#item) and animates it to echo the vanilla centre pop:
     * a punchy scale-in with a little overshoot, a couple of spins about the
     * vertical axis (faked in 2D by squashing horizontal scale with |cos| so the
     * flat sprite reads as turning edge-on), a gentle bob, then a shrink-out. The
     * true vanilla animation is a 3D tumble in camera space, which the 2D HUD
     * layer can't drive — this is the closest faithful match here, and a large
     * improvement over the old flat gold square.
     *
     * TOTEM_PEAK (from the "Size %" config) sets the settled on-screen size;
     * TOTEM_CORNER picks the corner (0 TL, 1 TR, 2 BL, 3 BR).
     */
    private void renderTotemPop(GuiGraphicsExtractor g, int w, int h) {
        if (totemPopStart < 0) return;
        long elapsed = System.currentTimeMillis() - totemPopStart;
        if (elapsed > TOTEM_POP_MS) {
            totemPopStart = -1L;
            return;
        }
        float t = elapsed / (float) TOTEM_POP_MS; // 0..1 over the pop's lifetime

        // Scale envelope: ease-out pop-in with overshoot (0..0.25), settle to 1
        // (0.25..0.8), shrink out (0.8..1).
        float grow;
        if (t < 0.25f) {
            float u = t / 0.25f;
            grow = (1.0f - (1.0f - u) * (1.0f - u)) * 1.18f; // ease-out * overshoot
        } else if (t < 0.8f) {
            float u = (t - 0.25f) / 0.55f;
            grow = 1.18f - 0.18f * u;                        // settle 1.18 -> 1.0
        } else {
            float u = (t - 0.8f) / 0.2f;
            grow = 1.0f - u;                                 // shrink out
        }
        if (grow <= 0.02f) return;

        // Fake Y-axis spin: ease-out so it whirls early and lands facing forward
        // (ends on a whole number of half-turns, cos = ±1 -> flip = 1).
        float spin = (1.0f - (1.0f - t) * (1.0f - t)) * (float) (Math.PI * 4);
        float flip = Math.max(0.06f, Math.abs((float) Math.cos(spin)));
        float bob = (float) Math.sin(t * Math.PI * 2) * 1.5f;

        float base = 16.0f * TOTEM_PEAK;      // settled pixel size of the icon
        float unit = base / 16.0f;            // item() draws at a fixed 16px
        float sx = unit * grow * flip;
        float sy = unit * grow;

        int pad = Math.round(6 + base * 0.6f);
        int ax = (TOTEM_CORNER == 1 || TOTEM_CORNER == 3) ? w - pad : pad;
        int ay = (TOTEM_CORNER == 2 || TOTEM_CORNER == 3) ? h - pad : pad;

        renderTotemSparks(g, ax, ay, t); // behind the icon

        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(ax, ay + bob);
        pose.scale(sx, sy);
        g.item(totemStack(), -8, -8); // centre the 16px icon on the origin
        pose.popMatrix();
    }

    private static final int SPARK_COUNT = 12;
    private static final float SPARK_BURST_FRACTION = 0.45f; // sparks only play over the pop's first 45%

    /**
     * Small gold/green "spark" burst radiating from the corner pop, echoing
     * vanilla's own totem particle effect (a real Level-space ParticleEngine
     * emitter, which already plays in the world regardless of this HUD
     * indicator -- see ClientPacketListener's totem branch). HUD rendering is
     * 2D screen space and can't spawn real particles, so this fakes the same
     * look with small drawn squares flying outward and fading.
     *
     * Seeded by totemPopStart (not a free-running clock), so every frame of
     * the same pop draws identical spark paths instead of jittering.
     */
    private void renderTotemSparks(GuiGraphicsExtractor g, int ax, int ay, float t) {
        if (t > SPARK_BURST_FRACTION) return;
        float burstT = t / SPARK_BURST_FRACTION; // 0..1 across the burst's own lifetime
        var rng = new Random(totemPopStart);
        for (int i = 0; i < SPARK_COUNT; i++) {
            double angle = (Math.PI * 2 * i / SPARK_COUNT) + (rng.nextDouble() - 0.5) * 0.5;
            float speed = 16.0f + rng.nextFloat() * 16.0f; // total px travelled over the burst
            boolean gold = rng.nextBoolean();
            float ease = 1.0f - (1.0f - burstT) * (1.0f - burstT); // ease-out
            float dist = speed * ease;
            int sx = ax + (int) Math.round(Math.cos(angle) * dist);
            int sy = ay + (int) Math.round(Math.sin(angle) * dist);
            int alpha = Math.max(0, 255 - (int) (burstT * 255));
            int color = (alpha << 24) | (gold ? 0xE8B84B : 0x2E9E4F);
            int size = burstT < 0.2f ? 2 : 1;
            g.fill(sx - size, sy - size, sx + size, sy + size, color);
        }
    }

    /** Full-screen red wash on totem pop, same fill()-only technique as the rest of this HUD. */
    private void renderTotemFlash(GuiGraphicsExtractor g, int w, int h) {
        if (totemFlashStart < 0) return;
        long elapsed = System.currentTimeMillis() - totemFlashStart;
        if (elapsed > TOTEM_FLASH_MS) {
            totemFlashStart = -1L;
            return;
        }
        float t = elapsed / (float) TOTEM_FLASH_MS;
        // Quick punch in, then fade out -- peaks around 15% of the flash's lifetime.
        float strength = t < 0.15f ? (t / 0.15f) : (1.0f - (t - 0.15f) / 0.85f);
        int alpha = Math.max(0, Math.min(130, (int) (strength * 130))); // capped well short of a full blackout
        int color = (alpha << 24) | 0xFF0000;
        g.fill(0, 0, w, h, color);
    }

    private static int rainbow() {
        float hue = (System.currentTimeMillis() % 3000L) / 3000.0f * 6.0f;
        int i = (int) hue;
        float f = hue - i;
        int v = 255, p = 40;
        int q = (int) (255 * (1 - 0.84f * f));
        int t = (int) (255 * (1 - 0.84f * (1 - f)));
        int r, gg, b;
        switch (i % 6) {
            case 0 -> { r = v; gg = t; b = p; }
            case 1 -> { r = q; gg = v; b = p; }
            case 2 -> { r = p; gg = v; b = t; }
            case 3 -> { r = p; gg = q; b = v; }
            case 4 -> { r = t; gg = p; b = v; }
            default -> { r = v; gg = p; b = q; }
        }
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }

    private static void purge(ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && q.peekFirst() < now - CPS_WINDOW_MS) q.pollFirst();
    }
}

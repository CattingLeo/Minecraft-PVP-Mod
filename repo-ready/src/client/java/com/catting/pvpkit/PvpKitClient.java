package com.catting.pvpkit;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

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
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
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

    /** Copy persisted config -> live flags. Called on load and on config save. */
    public static void applyConfig() {
        PvpKitConfig c = PvpKitConfig.get();
        SHOW_FPS = c.showFps; SHOW_CPS = c.showCps; SHOW_PING = c.showPing; RGB_TEXT = c.rainbowText;
        ANCHOR_X = c.hudX; ANCHOR_Y = c.hudY;
        TOTEM_POP = c.totemPop; HIDE_CENTER_TOTEM = c.hideCenterTotem;
        TOTEM_CORNER = c.totemCorner.ordinal(); TOTEM_PEAK = c.totemSizePct / 100.0f;
        NO_SLOWNESS_FOV = c.noSlownessFov; NO_NAUSEA = c.noNausea; NO_HURT_TILT = c.noHurtTilt;
        NO_DARKNESS = c.noDarkness; NO_BLINDNESS = c.noBlindness;
        FULLBRIGHT = c.fullbright;
        LOCATOR_ENABLED = c.locatorEnabled;
        LOCATOR_DISABLE_IN_SPECTATOR = c.locatorDisableInSpectator;
        LOCATOR_SHOW_THROUGH_WALLS = c.locatorShowThroughWalls;
        NO_CRYSTAL_EXPLOSION = c.noCrystalExplosion; COOLDOWN_FLASH = c.cooldownFlash;
        SHOW_HIT_MARKER = c.showHitMarker;
    }

    // ---- Totem corner pop ----
    private static final long TOTEM_POP_MS = 1000L;
    private static long totemPopStart = -1L;

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

    // ---- CPS ----
    private static final long CPS_WINDOW_MS = 1000L;
    private final ArrayDeque<Long> leftClicks = new ArrayDeque<>();
    private final ArrayDeque<Long> rightClicks = new ArrayDeque<>();
    private boolean prevLeft;
    private boolean prevRight;

    public static void triggerTotemPop() {
        totemPopStart = System.currentTimeMillis();
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

        if (COOLDOWN_FLASH) renderCrosshairFlash(graphics, mc);
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

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
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

    private void renderTotemPop(GuiGraphicsExtractor g, int w, int h) {
        if (totemPopStart < 0) return;
        long elapsed = System.currentTimeMillis() - totemPopStart;
        if (elapsed > TOTEM_POP_MS) {
            totemPopStart = -1L;
            return;
        }
        float t = elapsed / (float) TOTEM_POP_MS;
        float scale = (t < 0.3f) ? (t / 0.3f) * TOTEM_PEAK : TOTEM_PEAK * (1.0f - (t - 0.3f) / 0.7f);
        if (scale <= 0.01f) return;
        int pad = 12 + Math.round(8 * TOTEM_PEAK);
        int ax = (TOTEM_CORNER == 1 || TOTEM_CORNER == 3) ? w - pad : pad;
        int ay = (TOTEM_CORNER == 2 || TOTEM_CORNER == 3) ? h - pad : pad;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(ax, ay);
        pose.scale(scale, scale);
        g.fill(-9, -9, 9, 9, 0xFF3A2E10);
        g.fill(-8, -8, 8, 8, 0xFFE8B84B);
        pose.popMatrix();
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

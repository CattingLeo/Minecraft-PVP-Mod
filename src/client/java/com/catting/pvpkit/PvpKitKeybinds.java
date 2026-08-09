package com.catting.pvpkit;

import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * All PvP Kit keybinds, grouped under their own "PvP Kit" section in
 * Options > Controls > Key Binds. Every keybind starts UNBOUND
 * (GLFW_KEY_UNKNOWN) -- nothing fires until you bind a key yourself.
 *
 * Screenshot/recording use java.awt.Robot -- a decades-stable JDK screen
 * capture API -- instead of Minecraft's internal screenshot class. That was
 * a deliberate choice: research strongly suggested Minecraft's internal
 * screenshot API was restructured in the 26.x rendering rewrite in a way
 * that made me genuinely unsure I had the right class, and a wrong guess
 * there risks a silently-wrong capture rather than a clean compile error.
 * Robot is untouched by any of that. Trade-off: it captures your whole
 * primary monitor, not cropped exactly to the game window -- fine in
 * fullscreen/maximized play, worth knowing about otherwise.
 *
 * "Recording" is a PNG image-sequence (no video encoder ships with Java),
 * saved to <gamedir>/screen recording/<timestamp>/frame_NNNNNN.png at
 * roughly 10 frames/sec. Turn it into an actual video afterward with:
 *   ffmpeg -framerate 10 -i frame_%06d.png -c:v libx264 -pix_fmt yuv420p out.mp4
 * For real-time, reliable video recording, OBS Studio (free) remains the
 * genuinely better tool -- this is a lightweight in-mod fallback, not a
 * replacement for it.
 *
 * For a second bindable key per action (or per any action in the game,
 * vanilla included), install the "Multi Key Bindings" mod alongside this
 * one -- see README.md.
 */
public final class PvpKitKeybinds {

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(PvpKitClient.MOD_ID, "pvp_kit"));

    private static final KeyMapping TOGGLE_FULLBRIGHT = bind("toggle_fullbright");
    private static final KeyMapping TOGGLE_HIT_MARKER = bind("toggle_hit_marker");
    private static final KeyMapping TOGGLE_COOLDOWN_FLASH = bind("toggle_cooldown_flash");
    private static final KeyMapping TOGGLE_CRYSTAL_EXPLOSION = bind("toggle_crystal_explosion");
    private static final KeyMapping TOGGLE_TOTEM_POP = bind("toggle_totem_pop");
    private static final KeyMapping TOGGLE_HUD = bind("toggle_hud");
    private static final KeyMapping TOGGLE_CLEAN_VIEW = bind("toggle_clean_view");
    private static final KeyMapping SCREENSHOT = bind("screenshot");
    private static final KeyMapping TOGGLE_RECORDING = bind("toggle_recording");
    private static final KeyMapping TOGGLE_AUTO_TOTEM = bind("toggle_auto_totem");
    private static final KeyMapping TOGGLE_AUTO_EAT = bind("toggle_auto_eat");
    private static final KeyMapping TOGGLE_FREECAM = bind("toggle_freecam");
    private static final KeyMapping TOGGLE_XRAY = bind("toggle_xray");
    // Deliberately bound by default (every other keybind in this mod starts
    // unbound) -- this one was specifically requested as a Right Shift toggle.
    private static final KeyMapping TOGGLE_MODULE_HUD = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.pvpkit.toggle_module_hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));

    private static Robot robot;
    private static boolean recording = false;
    private static Path recordingDir = null;
    private static long lastFrameMs = 0L;
    private static int frameIndex = 0;
    private static final long FRAME_INTERVAL_MS = 100L; // ~10 fps image-sequence "recording"

    private PvpKitKeybinds() {
    }

    /**
     * Local-only status message. Deliberately avoids Minecraft's chat/Gui API --
     * two separate guesses at that (Player#displayClientMessage, then
     * Gui#getChat()) both failed to compile in this exact 26.2 build. Routes
     * through PvpKitClient's toast system instead, which uses ONLY graphics.text(),
     * the same call already confirmed working for the FPS/CPS/Ping/hit-marker HUD.
     */
    private static void chat(Minecraft mc, Component message) {
        PvpKitClient.showToast(message.getString());
    }

    private static KeyMapping bind(String path) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.pvpkit." + path, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
    }

    public static void register() {
        try {
            robot = new Robot();
        } catch (AWTException e) {
            robot = null; // capture keys simply no-op if this fails
        }
        ClientTickEvents.END_CLIENT_TICK.register(PvpKitKeybinds::onTick);
    }

    private static void onTick(Minecraft mc) {
        while (TOGGLE_FULLBRIGHT.consumeClick()) toggle(c -> c.fullbright = !c.fullbright);
        while (TOGGLE_HIT_MARKER.consumeClick()) toggle(c -> c.showHitMarker = !c.showHitMarker);
        while (TOGGLE_COOLDOWN_FLASH.consumeClick()) toggle(c -> c.cooldownFlash = !c.cooldownFlash);
        while (TOGGLE_CRYSTAL_EXPLOSION.consumeClick()) toggle(c -> c.noCrystalExplosion = !c.noCrystalExplosion);
        while (TOGGLE_TOTEM_POP.consumeClick()) toggle(c -> c.totemPop = !c.totemPop);
        while (TOGGLE_HUD.consumeClick()) toggle(c -> {
            boolean v = !c.showFps;
            c.showFps = v;
            c.showCps = v;
            c.showPing = v;
        });
        while (TOGGLE_CLEAN_VIEW.consumeClick()) toggle(c -> {
            boolean v = !c.noSlownessFov;
            c.noSlownessFov = v;
            c.noNausea = v;
            c.noHurtTilt = v;
            c.noDarkness = v;
            c.noBlindness = v;
        });
        while (SCREENSHOT.consumeClick()) takeScreenshot(mc);
        while (TOGGLE_RECORDING.consumeClick()) toggleRecording(mc);
        while (TOGGLE_AUTO_TOTEM.consumeClick()) toggle(c -> c.autoTotem = !c.autoTotem);
        while (TOGGLE_AUTO_EAT.consumeClick()) toggle(c -> c.autoEat = !c.autoEat);
        while (TOGGLE_FREECAM.consumeClick()) FreecamManager.toggle();
        while (TOGGLE_XRAY.consumeClick()) toggle(c -> c.xrayEnabled = !c.xrayEnabled);
        while (TOGGLE_MODULE_HUD.consumeClick()) toggleModuleScreen(mc);

        if (recording) {
            long now = System.currentTimeMillis();
            if (now - lastFrameMs >= FRAME_INTERVAL_MS) {
                lastFrameMs = now;
                saveFrame(mc, recordingDir, String.format("frame_%06d", frameIndex++));
            }
        }
    }

    /** Right Shift: open the clickable module list, or close it if it's already open. */
    private static void toggleModuleScreen(Minecraft mc) {
        if (mc.gui.screen() instanceof ModuleListScreen) {
            mc.gui.setScreen(null);
        } else {
            mc.gui.setScreen(new ModuleListScreen());
        }
    }

    private interface ConfigEdit {
        void apply(PvpKitConfig c);
    }

    private static void toggle(ConfigEdit edit) {
        PvpKitConfig c = PvpKitConfig.get();
        edit.apply(c);
        PvpKitConfig.save();
        PvpKitClient.applyConfig();
    }

    private static void takeScreenshot(Minecraft mc) {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("files").resolve("screenshots");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
            return;
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"));
        boolean ok = saveFrame(mc, dir, stamp);
        if (ok) {
            chat(mc, Component.literal("Saved screenshot: " + stamp + ".png"));
        }
    }

    private static void toggleRecording(Minecraft mc) {
        recording = !recording;
        if (recording) {
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"));
            recordingDir = FabricLoader.getInstance().getGameDir().resolve("files").resolve("screen recording").resolve(stamp);
            try {
                Files.createDirectories(recordingDir);
            } catch (IOException e) {
                recording = false;
                return;
            }
            frameIndex = 0;
            lastFrameMs = 0L;
            chat(mc, Component.literal("Recording started -> files/screen recording/" + stamp));
        } else {
            chat(mc, Component.literal("Recording stopped (" + frameIndex + " frames saved)"));
        }
    }

    /** Captures the primary monitor via java.awt.Robot and writes a PNG. Returns success. */
    private static boolean saveFrame(Minecraft mc, Path dir, String name) {
        if (robot == null) {
            chat(mc, Component.literal("Screen capture unavailable on this system."));
            return false;
        }
        try {
            Rectangle bounds = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage img = robot.createScreenCapture(bounds);
            File out = dir.resolve(name + ".png").toFile();
            return ImageIO.write(img, "png", out);
        } catch (Exception e) {
            return false;
        }
    }
}

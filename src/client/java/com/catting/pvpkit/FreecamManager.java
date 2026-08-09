package com.catting.pvpkit;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/**
 * Freecam -- detaches the view from your body so you can fly the camera
 * around while the player stands still.
 *
 * Implementation avoids mixins entirely: `Minecraft#setCameraEntity` is
 * public (it's what Spectator mode uses to possess entities), so the camera
 * is just an invisible, no-physics ArmorStand added client-side via
 * `ClientLevel#addEntity` and possessed. Rotation is copied from the player
 * each tick, so mouse look works normally with no input plumbing of its own.
 *
 * The real player would otherwise still walk around on WASD while you fly,
 * so each tick their position is snapped back to where freecam started.
 * That's deliberately a "freeze", not a movement block -- there's no
 * mixin-free way to swallow the movement keys, and snapping is both simpler
 * and self-correcting if anything does nudge them.
 *
 * FAIR USE: this is a see-through-walls tool -- it can show you players and
 * terrain your body has no line of sight to. Private-world / LAN-with-friends
 * territory, NOT something to take onto a server you don't host. See
 * README's Fair use.
 */
public final class FreecamManager {

    private static final double SPEED = 0.5;          // blocks per tick
    private static final double VERTICAL_SPEED = 0.4;

    private static boolean active;
    private static ArmorStand camera;
    private static Vec3 frozenPos;

    private FreecamManager() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(FreecamManager::onTick);
    }

    public static boolean isActive() {
        return active;
    }

    public static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (active) {
            disable(mc);
        } else {
            enable(mc);
        }
    }

    private static void enable(Minecraft mc) {
        frozenPos = mc.player.position();
        Vec3 eye = mc.player.getEyePosition();
        camera = new ArmorStand(mc.level, eye.x, eye.y, eye.z);
        camera.setInvisible(true);
        camera.noPhysics = true;
        camera.setNoGravity(true);
        mc.level.addEntity(camera);
        mc.setCameraEntity(camera);
        active = true;
        PvpKitClient.showToast("Freecam ON");
    }

    private static void disable(Minecraft mc) {
        mc.setCameraEntity(mc.player);
        if (camera != null) {
            camera.discard();
            camera = null;
        }
        // Put the body back exactly where it was frozen, so toggling off never
        // leaves you drifted from where you actually stood.
        if (frozenPos != null && mc.player != null) {
            mc.player.setPos(frozenPos.x, frozenPos.y, frozenPos.z);
        }
        frozenPos = null;
        active = false;
        PvpKitClient.showToast("Freecam OFF");
    }

    private static void onTick(Minecraft mc) {
        if (!active) return;
        // Any of these going away (disconnect, death, dimension change) means the
        // camera entity is no longer valid -- bail out rather than fly a ghost.
        if (mc.player == null || mc.level == null || camera == null) {
            if (mc.player != null) mc.setCameraEntity(mc.player);
            active = false;
            camera = null;
            frozenPos = null;
            return;
        }

        // Keep the body parked where freecam began.
        if (frozenPos != null) {
            mc.player.setPos(frozenPos.x, frozenPos.y, frozenPos.z);
            mc.player.setDeltaMovement(Vec3.ZERO);
        }

        // Mouse look drives the player's rotation as normal; mirror it onto the camera.
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        camera.setYRot(yaw);
        camera.setXRot(pitch);

        double forward = 0.0;
        double strafe = 0.0;
        double vertical = 0.0;
        if (mc.options.keyUp.isDown()) forward += 1.0;
        if (mc.options.keyDown.isDown()) forward -= 1.0;
        if (mc.options.keyLeft.isDown()) strafe += 1.0;
        if (mc.options.keyRight.isDown()) strafe -= 1.0;
        if (mc.options.keyJump.isDown()) vertical += 1.0;
        if (mc.options.keyShift.isDown()) vertical -= 1.0;

        double speed = mc.options.keySprint.isDown() ? SPEED * 2.5 : SPEED;
        double yawRad = Math.toRadians(yaw);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        // Standard MC movement basis: forward is (-sin, cos), strafe is (cos, sin).
        double dx = (forward * -sin + strafe * cos) * speed;
        double dz = (forward * cos + strafe * sin) * speed;
        double dy = vertical * VERTICAL_SPEED;

        Vec3 pos = camera.position();
        camera.setPos(pos.x + dx, pos.y + dy, pos.z + dz);
    }
}

package com.catting.pvpkit.mixin;

import java.util.List;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * THE reason the practice bot kept being invisible (and therefore unhittable -- you
 * cannot hit an entity your client never created).
 *
 * Vanilla's client REFUSES to spawn a PLAYER-type entity whose UUID has no player-info
 * (tab list) entry yet, logging "Server attempted to add player prior to sending player
 * info" and returning null. A FakePlayer never goes through a real player's join flow,
 * so it has no such entry unless one is sent explicitly.
 *
 * PracticeBotManager already sent that info -- but on a 100-tick timer, which is a RACE,
 * not a fix: vanilla's per-player entity tracker re-sends an "add entity" packet every
 * time you move out of and back into tracking range, at an arbitrary moment. Any re-add
 * landing in the gap between broadcasts gets rejected and the bot silently disappears.
 * That's exactly the warning found in the user's log.
 *
 * ServerEntity#addPairing(ServerPlayer) is the single function that sends the spawn
 * packet to one specific player, so injecting at its HEAD guarantees the player-info
 * arrives immediately before the spawn packet, for that player, on every single add --
 * first spawn and every subsequent re-track alike. No timer, no window, nothing to lose
 * a race against.
 *
 * (Fabric's own EntityTrackingEvents.START_TRACKING is NOT usable here: it's injected at
 * TAIL of this same method -- verified in fabric-networking-api-v1's bytecode -- so it
 * only fires after the spawn packet has already been sent and rejected.)
 */
@Mixin(ServerEntity.class)
public abstract class BotTrackingMixin {

    @Shadow
    @Final
    private Entity entity;

    @Inject(method = "addPairing", at = @At("HEAD"))
    private void pvpkit$sendBotPlayerInfoFirst(ServerPlayer player, CallbackInfo ci) {
        if (!(this.entity instanceof FakePlayer fake)) return;
        player.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(fake)));
    }
}

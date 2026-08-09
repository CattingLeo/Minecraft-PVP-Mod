package com.catting.pvpkit;

import com.catting.nocooldown.NoCooldownConfig;
import com.catting.pvpkit.PvpKitConfig.ScrollAction;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;

/**
 * Runs whatever the scroll wheel is configured to do (see
 * PvpKitConfig.ScrollAction, and ScrollHotbarMixin for where these fire).
 *
 * Called from the client thread inside MouseHandler#onScroll, so it's safe to
 * touch client state and options directly here.
 */
public final class ScrollActions {

    private ScrollActions() {
    }

    public static void run(ScrollAction action) {
        if (action == null || action == ScrollAction.NONE) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        switch (action) {
            case HOTBAR_NEXT -> shiftHotbar(mc, 1);
            case HOTBAR_PREV -> shiftHotbar(mc, -1);
            case FULLBRIGHT -> toggle(c -> c.fullbright = !c.fullbright);
            case FREECAM -> FreecamManager.toggle();
            case HUD -> toggle(c -> {
                boolean v = !c.showFps;
                c.showFps = v;
                c.showCps = v;
                c.showPing = v;
            });
            case MODULE_HUD -> {
                if (mc.gui.screen() instanceof ModuleListScreen) {
                    mc.gui.setScreen(null);
                } else {
                    mc.gui.setScreen(new ModuleListScreen());
                }
            }
            case AUTO_TOTEM -> toggle(c -> c.autoTotem = !c.autoTotem);
            case AUTO_EAT -> toggle(c -> c.autoEat = !c.autoEat);
            case KILL_AURA -> toggleNoCooldown(c -> c.killAura = !c.killAura);
            case FLIGHT -> toggleNoCooldown(c -> c.flightEnabled = !c.flightEnabled);
            case NO_DAMAGE -> toggleNoCooldown(c -> c.noDamage = !c.noDamage);
            case XRAY -> toggle(c -> c.xrayEnabled = !c.xrayEnabled);
            default -> {
            }
        }
    }

    /**
     * Manual hotbar step, kept available so you can put slot switching back on one
     * wheel direction even with the vanilla scroll-to-switch behaviour disabled.
     * Sends ServerboundSetCarriedItemPacket for the same reason Auto Eat does --
     * setSelectedSlot alone is client-only and the server would keep believing you
     * are holding the old item.
     */
    private static void shiftHotbar(Minecraft mc, int delta) {
        Inventory inv = mc.player.getInventory();
        int size = Inventory.getSelectionSize();
        int slot = Math.floorMod(inv.getSelectedSlot() + delta, size);
        inv.setSelectedSlot(slot);
        if (mc.getConnection() != null) {
            mc.getConnection().getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        }
    }

    private interface Edit {
        void apply(PvpKitConfig c);
    }

    private static void toggle(Edit edit) {
        PvpKitConfig c = PvpKitConfig.get();
        edit.apply(c);
        PvpKitConfig.save();
        PvpKitClient.applyConfig();
    }

    private interface NcEdit {
        void apply(NoCooldownConfig c);
    }

    /** No Cooldown's mixins read the config live each time they fire, so mutate + save is enough. */
    private static void toggleNoCooldown(NcEdit edit) {
        NoCooldownConfig c = NoCooldownConfig.get();
        edit.apply(c);
        NoCooldownConfig.save();
    }
}

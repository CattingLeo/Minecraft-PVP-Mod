package com.catting.pvpkit;

import java.util.function.BooleanSupplier;

import com.catting.nocooldown.NoCooldownConfig;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Right Shift opens this: every module in the mod as a clickable button, top-right
 * corner, click one to toggle it right there -- no sub-menus or sliders (those stay in
 * Mod Menu -> .PVP KIT). Doesn't pause the game (isPauseScreen() false) and shows the
 * world behind it (extractTransparentBackground) instead of the usual dark/blurred menu
 * background, so it reads as a lightweight overlay rather than a full pause screen.
 *
 * In this MC version Screen has no simple render() override point (rendering is split
 * out via extractRenderState) and Minecraft has no public screen field/setScreen method
 * -- both verified via javap. Sticking to addRenderableWidget(Button) for everything
 * means Screen's own (already-working, used by Cloth Config) default extractRenderState
 * handles drawing and click dispatch, so none of that needs touching here. Opening/
 * closing goes through Minecraft#gui.screen()/setScreen(Screen), the actual current
 * home of that state in this version (moved off Minecraft itself).
 */
public class ModuleListScreen extends Screen {

    private static final int ROW_W = 170;
    private static final int ROW_H = 16;
    private static final int GAP = 2;
    private static final int MARGIN = 6;

    public ModuleListScreen() {
        super(Component.literal("Modules"));
    }

    @Override
    protected void init() {
        PvpKitConfig c = PvpKitConfig.get();
        NoCooldownConfig nc = NoCooldownConfig.get();
        int x = this.width - ROW_W - MARGIN;
        int y = MARGIN;

        y = row(x, y, "Fullbright", () -> c.fullbright, v -> { c.fullbright = v; save(); });
        y = row(x, y, "Cooldown Flash", () -> c.cooldownFlash, v -> { c.cooldownFlash = v; save(); });
        y = row(x, y, "Auto Totem", () -> c.autoTotem, v -> { c.autoTotem = v; save(); });
        y = row(x, y, "Auto Eat", () -> c.autoEat, v -> { c.autoEat = v; save(); });
        y = row(x, y, "Xray", () -> c.xrayEnabled, v -> { c.xrayEnabled = v; save(); });
        y = row(x, y, "Freecam", FreecamManager::isActive, v -> FreecamManager.toggle());
        y += GAP * 2;
        y = row(x, y, "Unlimited Durability", () -> nc.unlimitedDurability, v -> { nc.unlimitedDurability = v; NoCooldownConfig.save(); });
        y = row(x, y, "Instant Use", () -> nc.instantUse, v -> { nc.instantUse = v; NoCooldownConfig.save(); });
        y = row(x, y, "No Damage", () -> nc.noDamage, v -> { nc.noDamage = v; NoCooldownConfig.save(); });
        y = row(x, y, "Flight", () -> nc.flightEnabled, v -> { nc.flightEnabled = v; NoCooldownConfig.save(); });
        y = row(x, y, "Infinite Hunger", () -> nc.infiniteHunger, v -> { nc.infiniteHunger = v; NoCooldownConfig.save(); });
        y = row(x, y, "Kill Aura", () -> nc.killAura, v -> { nc.killAura = v; NoCooldownConfig.save(); });
        cooldownModeRow(x, y, nc);
    }

    private interface BoolSetter {
        void set(boolean value);
    }

    /** Adds one toggle row and returns the Y for the next one. */
    private int row(int x, int y, String name, BooleanSupplier getter, BoolSetter setter) {
        Button button = Button.builder(label(name, getter.getAsBoolean()), b -> {
            boolean next = !getter.getAsBoolean();
            setter.set(next);
            b.setMessage(label(name, next));
        }).bounds(x, y, ROW_W, ROW_H).build();
        addRenderableWidget(button);
        return y + ROW_H + GAP;
    }

    /** No Cooldown's Mode is a 3-way cycle rather than a plain on/off, so it gets its own row. */
    private void cooldownModeRow(int x, int y, NoCooldownConfig nc) {
        Button button = Button.builder(Component.literal("Mode: " + nc.mode.label), b -> {
            NoCooldownConfig.Mode[] values = NoCooldownConfig.Mode.values();
            nc.mode = values[(nc.mode.ordinal() + 1) % values.length];
            NoCooldownConfig.save();
            b.setMessage(Component.literal("Mode: " + nc.mode.label));
        }).bounds(x, y, ROW_W, ROW_H).build();
        addRenderableWidget(button);
    }

    private static Component label(String name, boolean on) {
        return Component.literal(name + (on ? " [ON]" : " [off]"));
    }

    private static void save() {
        PvpKitConfig.save();
        PvpKitClient.applyConfig();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(graphics);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

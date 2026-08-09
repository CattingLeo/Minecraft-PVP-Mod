package com.catting.pvpkit;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

/**
 * Adds the two macro tab buttons -- One Tick and Step by Step -- to the real vanilla
 * Key Binds screen, top-left, clear of the centred title. Which tab is active decides
 * which macro's duplicate rows are shown and which one a "+" appends to.
 *
 * Added via Fabric's ScreenEvents.AFTER_INIT rather than a mixin because these are
 * plain extra widgets on top of an existing screen -- no vanilla behaviour to
 * intercept -- and AFTER_INIT is the supported way to do exactly that.
 */
public final class MacroTabsHook {

    private static final int W = 90;
    private static final int H = 20;
    private static final int MARGIN = 6;

    private MacroTabsHook() {
    }

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof KeyBindsScreen)) return;
            Screens.getWidgets(screen).add(tab(MacroRows.Tab.ONE_TICK, MARGIN));
            Screens.getWidgets(screen).add(tab(MacroRows.Tab.STEP_BY_STEP, MARGIN + W + 4));
        });
    }

    private static Button tab(MacroRows.Tab tab, int x) {
        boolean active = MacroRows.activeTab() == tab;
        Component label = Component.literal(active ? "[" + tab.label + "]" : tab.label);
        return Button.builder(label, b -> MacroRows.setActiveTab(tab))
                .bounds(x, MARGIN, W, H)
                .build();
    }
}

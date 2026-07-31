package com.catting.pvpkit;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Decides which block states stay visible while Xray is on. Read by XrayMixin;
 * kept here rather than inline in the mixin so the whitelist is a single, easy
 * to scan list of Blocks constants instead of mixin bytecode.
 *
 * Ore pairs (regular + deepslate) are checked individually rather than via a
 * BlockTags entry -- in this MC version BlockTags only has GOLD_ORES,
 * IRON_ORES and COPPER_ORES (verified via javap on the mapped 26.2 jar; no
 * COAL_ORES/DIAMOND_ORES/EMERALD_ORES/REDSTONE_ORES/LAPIS_ORES tag exists),
 * so direct Block identity checks are the only reliable option for the rest.
 */
public final class XrayBlocks {

    private XrayBlocks() {
    }

    /** True if this block state should render normally; false if Xray should hide it. */
    public static boolean isVisible(BlockBehaviour.BlockStateBase state) {
        if (!PvpKitClient.XRAY_ENABLED) return true;
        if (!state.isSolidRender()) return true; // never touch non-solid blocks (torches, doors, plants, panes...)

        Block b = state.getBlock();
        if (PvpKitClient.XRAY_COAL && (b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE)) return true;
        if (PvpKitClient.XRAY_IRON && (b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE)) return true;
        if (PvpKitClient.XRAY_COPPER && (b == Blocks.COPPER_ORE || b == Blocks.DEEPSLATE_COPPER_ORE)) return true;
        if (PvpKitClient.XRAY_GOLD && (b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE || b == Blocks.NETHER_GOLD_ORE)) return true;
        if (PvpKitClient.XRAY_REDSTONE && (b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE)) return true;
        if (PvpKitClient.XRAY_LAPIS && (b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE)) return true;
        if (PvpKitClient.XRAY_EMERALD && (b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE)) return true;
        if (PvpKitClient.XRAY_DIAMOND && (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE)) return true;
        if (PvpKitClient.XRAY_ANCIENT_DEBRIS && b == Blocks.ANCIENT_DEBRIS) return true;
        if (PvpKitClient.XRAY_NETHER_QUARTZ && b == Blocks.NETHER_QUARTZ_ORE) return true;
        if (PvpKitClient.XRAY_CONTAINERS && (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST
                || b == Blocks.ENDER_CHEST || b == Blocks.BARREL || b == Blocks.SPAWNER
                || state.is(BlockTags.SHULKER_BOXES, s -> true))) return true;

        return false;
    }
}

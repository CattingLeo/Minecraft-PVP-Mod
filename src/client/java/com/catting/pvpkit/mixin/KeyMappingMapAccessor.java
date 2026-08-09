package com.catting.pvpkit.mixin;

import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes vanilla's private static KeyMapping.MAP -- the key-to-mappings dispatch table.
 *
 * In 26.2 this is Map<Key, List<KeyMapping>>: one key can already drive SEVERAL mappings
 * (that's what powers the "This key is also used for" conflict warning). KeyMapping.click()
 * and KeyMapping.set() both dispatch through it via forAllKeyMappings().
 *
 * That makes it the correct place to add an extra key for an existing action: register the
 * target mapping under the additional key and vanilla's own dispatch does the rest --
 * isDown, clickCount, consumeClick, release, everything -- with no behaviour reimplemented.
 * Multi Key Bindings widens exactly this same field for exactly this reason.
 */
@Mixin(KeyMapping.class)
public interface KeyMappingMapAccessor {
    @Accessor("MAP")
    static Map<InputConstants.Key, List<KeyMapping>> pvpkit$map() {
        throw new AssertionError();
    }
}

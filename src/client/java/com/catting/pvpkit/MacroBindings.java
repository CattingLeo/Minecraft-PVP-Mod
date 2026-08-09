package com.catting.pvpkit;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.catting.pvpkit.MacroConfig.Step;
import com.catting.pvpkit.mixin.KeyMappingMapAccessor;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

/**
 * Makes the extra keys added with "+" ACTUALLY FIRE.
 *
 * The previous approach polled GLFW every tick (InputConstants#isKeyDown) and tried to
 * re-implement what pressing the key should do. That never drove the real KeyMapping, so
 * the duplicated bindings simply didn't trigger the action they were duplicating.
 *
 * The fix is to stop reimplementing dispatch and join it instead. Vanilla's
 * KeyMapping.MAP is Map&lt;Key, List&lt;KeyMapping&gt;&gt; -- a key may already drive several
 * mappings -- and both KeyMapping.click() and KeyMapping.set() dispatch through it. So
 * registering the target mapping under the extra key is enough: vanilla then handles
 * isDown, clickCount, consumeClick and release natively, and the extra key behaves
 * indistinguishably from the original. This is the same mechanism Multi Key Bindings
 * uses (it widens the identical field).
 *
 * Registrations are tracked so they can be withdrawn cleanly, and re-applied after
 * KeyMapping.resetMapping() rebuilds MAP from scratch.
 */
public final class MacroBindings {

    /** Everything this class has put into vanilla's MAP, so it can be taken back out again. */
    private static final Map<KeyMapping, List<InputConstants.Key>> registered = new IdentityHashMap<>();

    private MacroBindings() {
    }

    /**
     * Rebuilds vanilla's dispatch entries from the current config. Safe to call repeatedly --
     * it withdraws its previous entries first, so it never double-registers or leaks a
     * binding that has since been removed or rebound.
     */
    public static void sync() {
        Map<InputConstants.Key, List<KeyMapping>> map = KeyMappingMapAccessor.pvpkit$map();
        if (map == null) return;

        // Withdraw what we added last time, leaving vanilla's own entries untouched.
        for (var entry : registered.entrySet()) {
            for (InputConstants.Key key : entry.getValue()) {
                List<KeyMapping> list = map.get(key);
                if (list != null) list.remove(entry.getKey());
            }
        }
        registered.clear();

        MacroConfig cfg = MacroConfig.get();
        register(map, cfg.oneTick);
        register(map, cfg.stepByStep);
    }

    private static void register(Map<InputConstants.Key, List<KeyMapping>> map, List<Step> steps) {
        for (Step step : steps) {
            if (step.keyName == null || step.boundKey == null) continue;
            InputConstants.Key key = InputConstants.getKey(step.boundKey);
            if (key == null || key == InputConstants.UNKNOWN) continue;
            KeyMapping target = KeyMapping.get(step.keyName);
            if (target == null) continue;

            List<KeyMapping> list = map.computeIfAbsent(key, k -> new ArrayList<>());
            if (!list.contains(target)) {
                list.add(target);
                registered.computeIfAbsent(target, m -> new ArrayList<>()).add(key);
            }
        }
    }
}

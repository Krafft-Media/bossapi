package com.danielkkrafft.bossapi;

import com.danielkkrafft.bossapi.registry.BossApiItems;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Boss API: a reusable framework and in-game editor for building and deterministically testing
 * GeckoLib bosses. Extend {@link com.danielkkrafft.bossapi.entity.BEBoss} for a boss whose attacks
 * can be forced individually via the {@code /boss} commands or the Boss Wand + editor screen.
 */
@Mod(BossApi.MODID)
public final class BossApi {
    public static final String MODID = "bossapi";

    public BossApi(IEventBus modEventBus, ModContainer container) {
        BossApiItems.ITEMS.register(modEventBus);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}

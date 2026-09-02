package com.danielkkrafft.bossapi.registry;

import com.danielkkrafft.bossapi.BossApi;
import com.danielkkrafft.bossapi.item.BossWand;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = BossApi.MODID)
public final class BossApiItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BossApi.MODID);

    public static final DeferredItem<Item> BOSS_WAND = ITEMS.registerItem("boss_wand", BossWand::new);

    private BossApiItems() {}

    @SubscribeEvent
    public static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS || event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(BOSS_WAND.get());
        }
    }
}

package com.danielkkrafft.bossapi.item;

import com.danielkkrafft.bossapi.BossSelection;
import com.danielkkrafft.bossapi.entity.BEBoss;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/**
 * Selects a {@link BEBoss} and sets its editor target. Left-click a boss to select it (shift-left-
 * click the selected boss to deselect); right-click a block or entity to aim the selected boss at
 * it. Open the editor screen with the Boss Editor keybind (default B).
 */
public class BossWand extends Item {
    public BossWand(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (!(player instanceof ServerPlayer)) return true;

        if (!(entity instanceof BEBoss<?> boss)) {
            player.sendSystemMessage(Component.literal("That entity is not a boss").withStyle(ChatFormatting.RED));
            return true;
        }

        if (player.isShiftKeyDown() && BossSelection.resolve(player.level().getServer()) == boss) {
            boss.setManualControl(false);
            BossSelection.clear();
            player.sendSystemMessage(Component.literal("Released " + boss.getDisplayName().getString()).withStyle(ChatFormatting.YELLOW));
            return true;
        }

        BossSelection.select(boss);
        player.sendSystemMessage(Component.literal("Selected " + boss.getDisplayName().getString()).withStyle(ChatFormatting.GREEN));
        return true;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.PASS;

        BEBoss<?> boss = BossSelection.resolve(level.getServer());
        if (boss == null) {
            player.sendSystemMessage(Component.literal("No boss selected").withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }

        Vec3 target = context.getClickLocation();
        boss.setEditorTarget(target);
        level.sendParticles(ParticleTypes.END_ROD, target.x, target.y, target.z, 12, 0.15, 0.15, 0.15, 0.02);
        player.sendSystemMessage(Component.literal("Target: " + format(target)).withStyle(ChatFormatting.AQUA));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;

        if (target instanceof BEBoss<?> clickedBoss) {
            if (player.isShiftKeyDown() && BossSelection.resolve(player.level().getServer()) == clickedBoss) {
                clickedBoss.setManualControl(false);
                BossSelection.clear();
                player.sendSystemMessage(Component.literal("Released " + clickedBoss.getDisplayName().getString()).withStyle(ChatFormatting.YELLOW));
                return InteractionResult.SUCCESS;
            }
            BossSelection.select(clickedBoss);
            player.sendSystemMessage(Component.literal("Selected " + clickedBoss.getDisplayName().getString()).withStyle(ChatFormatting.GREEN));
            return InteractionResult.SUCCESS;
        }

        BEBoss<?> boss = BossSelection.resolve(serverPlayer.level().getServer());
        if (boss == null) {
            player.sendSystemMessage(Component.literal("No boss selected").withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }

        boss.setEditorTarget(target);
        player.sendSystemMessage(Component.literal("Tracking " + target.getDisplayName().getString()).withStyle(ChatFormatting.AQUA));
        return InteractionResult.SUCCESS;
    }

    private static String format(Vec3 position) {
        return String.format("%.1f, %.1f, %.1f", position.x, position.y, position.z);
    }
}

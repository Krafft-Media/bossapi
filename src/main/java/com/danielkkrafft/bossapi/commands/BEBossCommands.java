package com.danielkkrafft.bossapi.commands;

import com.danielkkrafft.bossapi.BossSelection;
import com.danielkkrafft.bossapi.entity.BEBoss;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;

/**
 * The {@code /boss} command tree that drives the selected {@link BEBoss} for deterministic testing:
 * select a boss, then force individual goals or states at chosen targets, freeze it, or set its
 * health. Selection is global (see {@link BossSelection}) so these work with no player context.
 */
@EventBusSubscriber
public final class BEBossCommands {
    private static final SimpleCommandExceptionType NO_BOSS_SELECTED =
            new SimpleCommandExceptionType(Component.literal("No boss selected. Use /boss select <entity> or the Boss Wand."));

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("boss")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))

                        .then(Commands.literal("select")
                                .then(Commands.argument("entity", EntityArgument.entity()).executes(BEBossCommands::selectBoss)))

                        .then(Commands.literal("inspect").executes(BEBossCommands::inspect))
                        .then(Commands.literal("release").executes(BEBossCommands::releaseBoss))

                        .then(Commands.literal("goal")
                                .then(Commands.literal("clear").executes(BEBossCommands::clearGoal))
                                .then(Commands.argument("goal", StringArgumentType.word()).suggests(BEBossCommands::suggestGoals)
                                        .executes(BEBossCommands::forceGoal)
                                        .then(Commands.argument("delay", IntegerArgumentType.integer(0, 100)).executes(BEBossCommands::forceGoalDelayed))))

                        .then(Commands.literal("state")
                                .then(Commands.argument("state", StringArgumentType.word()).suggests(BEBossCommands::suggestStates)
                                        .executes(BEBossCommands::setState)
                                        .then(Commands.argument("delay", IntegerArgumentType.integer(0, 100)).executes(BEBossCommands::setStateDelayed))))

                        .then(Commands.literal("health")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("health", FloatArgumentType.floatArg(1.0F)).executes(BEBossCommands::setHealth)))
                                .then(Commands.literal("percent")
                                        .then(Commands.argument("percent", FloatArgumentType.floatArg(0.0F, 100.0F)).executes(BEBossCommands::setHealthPercent))))

                        .then(Commands.literal("freeze")
                                .executes(BEBossCommands::toggleFreeze)
                                .then(Commands.argument("frozen", BoolArgumentType.bool()).executes(BEBossCommands::setFreeze)))

                        .then(Commands.literal("target")
                                .then(Commands.literal("clear").executes(BEBossCommands::clearTarget))
                                .then(Commands.literal("pos")
                                        .then(Commands.argument("pos", Vec3Argument.vec3()).executes(BEBossCommands::setTargetPos)))
                                .then(Commands.argument("target", EntityArgument.entity()).executes(BEBossCommands::setTarget)))

                        .then(Commands.literal("move")
                                .executes(BEBossCommands::move)
                                .then(Commands.literal("stop").executes(BEBossCommands::stopMove)))
        );
    }

    private static int selectBoss(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(context, "entity");
        if (!(entity instanceof BEBoss<?> boss)) {
            context.getSource().sendFailure(Component.literal("That entity is not a BEBoss"));
            return 0;
        }
        BossSelection.select(boss);
        context.getSource().sendSuccess(() -> Component.literal("Selected " + boss.getDisplayName().getString()), false);
        return 1;
    }

    private static int inspect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BEBoss<?> boss = getBoss(context);
        String forcedGoal = boss.getForcedGoalId().isEmpty() ? "none" : boss.getForcedGoalId();

        context.getSource().sendSuccess(() -> Component.literal(
                boss.getDisplayName().getString()
                        + "\nHealth: " + format(boss.getHealth()) + " / " + format(boss.getMaxHealth())
                        + "\nState: " + boss.getState().name().toLowerCase(Locale.ROOT)
                        + "\nManual control: " + boss.isManualControl()
                        + "\nForced goal: " + forcedGoal
                        + "\nFrozen: " + boss.isBossFrozen()
                        + "\nTarget: " + (boss.getControlTarget() == null ? "none" : boss.getControlTarget().getDisplayName().getString())
                        + "\nStates: " + String.join(", ", boss.getStateNames())
                        + "\nGoals: " + String.join(", ", boss.getControlledGoalIds())
        ), false);

        return 1;
    }

    private static int forceGoal(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return forceGoal(context, 0);
    }

    private static int forceGoalDelayed(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return forceGoal(context, IntegerArgumentType.getInteger(context, "delay"));
    }

    private static int forceGoal(CommandContext<CommandSourceStack> context, int delay) throws CommandSyntaxException {
        BEBoss<?> boss = getBoss(context);
        String goalId = StringArgumentType.getString(context, "goal").toLowerCase(Locale.ROOT);

        if (!boss.getControlledGoalIds().contains(goalId)) {
            context.getSource().sendFailure(Component.literal("Unknown goal '" + goalId + "'. Available goals: " + String.join(", ", boss.getControlledGoalIds())));
            return 0;
        }

        boolean ok = delay > 0 ? boss.forceEditorGoal(goalId, delay) : boss.forceGoal(goalId);
        if (!ok) {
            context.getSource().sendFailure(Component.literal("Could not force '" + goalId + "'. It may need a valid target."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Forced " + boss.getDisplayName().getString() + " to use " + goalId
                + (delay > 0 ? " in " + delay + " ticks" : "")), false);
        return 1;
    }

    private static int clearGoal(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BEBoss<?> boss = getBoss(context);
        boss.clearBossControl();
        context.getSource().sendSuccess(() -> Component.literal("Cleared control for " + boss.getDisplayName().getString()), false);
        return 1;
    }

    private static int setState(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setState(context, 0);
    }

    private static int setStateDelayed(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setState(context, IntegerArgumentType.getInteger(context, "delay"));
    }

    private static int setState(CommandContext<CommandSourceStack> context, int delay) throws CommandSyntaxException {
        BEBoss<?> boss = getBoss(context);
        String state = StringArgumentType.getString(context, "state");

        if (!boss.setEditorState(state, delay)) {
            context.getSource().sendFailure(Component.literal("Unknown state '" + state + "'. States: " + String.join(", ", boss.getStateNames())));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Set " + boss.getDisplayName().getString() + " state to " + state.toLowerCase(Locale.ROOT)
                + (delay > 0 ? " in " + delay + " ticks" : "")), false);
        return 1;
    }

    private static int setHealth(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BEBoss<?> boss = getBoss(context);
        boss.setBossHealth(FloatArgumentType.getFloat(context, "health"));
        context.getSource().sendSuccess(() -> Component.literal("Set " + boss.getDisplayName().getString() + " health to " + format(boss.getHealth())), false);
        return 1;
    }

    private static int setHealthPercent(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BEBoss<?> boss = getBoss(context);
        float percent = FloatArgumentType.getFloat(context, "percent");
        boss.setBossHealthPercent(percent / 100.0F);
        context.getSource().sendSuccess(() -> Component.literal("Set " + boss.getDisplayName().getString() + " health to " + format(percent) + "%"), false);
        return 1;
    }

    private static int toggleFreeze(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BEBoss<?> boss = getBoss(context);
        boss.toggleBossFrozen();
        context.getSource().sendSuccess(() -> Component.literal(boss.getDisplayName().getString() + " frozen: " + boss.isBossFrozen()), false);
        return 1;
    }

    private static int setFreeze(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BEBoss<?> boss = getBoss(context);
        boolean frozen = BoolArgumentType.getBool(context, "frozen");
        boss.setBossFrozen(frozen);
        context.getSource().sendSuccess(() -> Component.literal(boss.getDisplayName().getString() + " frozen: " + frozen), false);
        return 1;
    }

    private static int setTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BEBoss<?> boss = getBoss(context);
        Entity target = EntityArgument.getEntity(context, "target");
        if (target == boss) {
            context.getSource().sendFailure(Component.literal("The boss cannot target itself"));
            return 0;
        }
        boss.setEditorTarget(target);
        context.getSource().sendSuccess(() -> Component.literal("Set target to " + target.getDisplayName().getString()), false);
        return 1;
    }

    private static int setTargetPos(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BEBoss<?> boss = getBoss(context);
        Vec3 pos = Vec3Argument.getVec3(context, "pos");
        boss.setEditorTarget(pos);
        context.getSource().sendSuccess(() -> Component.literal("Set target to " + format(pos)), false);
        return 1;
    }

    private static int clearTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BEBoss<?> boss = getBoss(context);
        boss.clearEditorTarget();
        context.getSource().sendSuccess(() -> Component.literal("Cleared target"), false);
        return 1;
    }

    private static int releaseBoss(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BEBoss<?> boss = getBoss(context);
        String name = boss.getDisplayName().getString();
        boss.setManualControl(false);
        BossSelection.clear();
        context.getSource().sendSuccess(() -> Component.literal("Released " + name), false);
        return 1;
    }

    private static int move(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BEBoss<?> boss = getBoss(context);
        if (!boss.hasEditorTarget()) {
            context.getSource().sendFailure(Component.literal("No target selected."));
            return 0;
        }
        if (boss.isBossFrozen()) {
            context.getSource().sendFailure(Component.literal("The selected boss is frozen."));
            return 0;
        }
        if (!boss.startEditorMove()) {
            context.getSource().sendFailure(Component.literal("Could not start editor movement."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Moving " + boss.getDisplayName().getString()), false);
        return 1;
    }

    private static int stopMove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BEBoss<?> boss = getBoss(context);
        boss.stopEditorMove();
        context.getSource().sendSuccess(() -> Component.literal("Stopped " + boss.getDisplayName().getString()), false);
        return 1;
    }

    private static java.util.concurrent.CompletableFuture<Suggestions> suggestGoals(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        BEBoss<?> boss = BossSelection.resolve(context.getSource().getServer());
        return boss == null ? Suggestions.empty() : SharedSuggestionProvider.suggest(boss.getControlledGoalIds(), builder);
    }

    private static java.util.concurrent.CompletableFuture<Suggestions> suggestStates(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        BEBoss<?> boss = BossSelection.resolve(context.getSource().getServer());
        return boss == null ? Suggestions.empty() : SharedSuggestionProvider.suggest(boss.getStateNames(), builder);
    }

    private static BEBoss<?> getBoss(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BEBoss<?> boss = BossSelection.resolve(context.getSource().getServer());
        if (boss == null) throw NO_BOSS_SELECTED.create();
        return boss;
    }

    private static String format(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String format(Vec3 pos) {
        return String.format(Locale.ROOT, "%.1f, %.1f, %.1f", pos.x, pos.y, pos.z);
    }
}

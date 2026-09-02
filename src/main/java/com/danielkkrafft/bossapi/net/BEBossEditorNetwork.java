package com.danielkkrafft.bossapi.net;

import com.danielkkrafft.bossapi.BossApi;
import com.danielkkrafft.bossapi.BossSelection;
import com.danielkkrafft.bossapi.entity.BEBoss;
import net.minecraft.commands.Commands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Server/client sync for the boss editor screen. Drives the globally-selected {@link BEBoss}. */
@EventBusSubscriber
public final class BEBossEditorNetwork {
    private static final int MAX_LIST_SIZE = 128;

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(RequestSnapshotPayload.TYPE, RequestSnapshotPayload.STREAM_CODEC, BEBossEditorNetwork::handleSnapshotRequest);
        registrar.playToServer(ActionPayload.TYPE, ActionPayload.STREAM_CODEC, BEBossEditorNetwork::handleAction);
        registrar.playToClient(SnapshotPayload.TYPE, SnapshotPayload.STREAM_CODEC);
    }

    private static void handleSnapshotRequest(RequestSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            sendSnapshot(player, "");
        });
    }

    private static void handleAction(ActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            if (!canUseEditor(player)) {
                sendSnapshot(player, "You do not have permission to use the boss editor.");
                return;
            }

            BEBoss<?> boss = BossSelection.resolve(((ServerLevel) player.level()).getServer());
            if (boss == null) {
                sendSnapshot(player, "No boss selected.");
                return;
            }

            String status = "";

            switch (payload.action()) {
                case SET_HEALTH -> boss.setBossHealth(payload.number());
                case SET_FROZEN -> boss.setBossFrozen(payload.number() > 0.5F);
                case RESUME_AI -> boss.setManualControl(false);
                case FORCE_GOAL -> {
                    if (!boss.forceEditorGoal(payload.value(), Math.round(payload.number()))) status = "Could not force " + payload.value() + ".";
                }
                case CLEAR_GOAL -> boss.clearBossControl();
                case SET_STATE -> {
                    if (!boss.setEditorState(payload.value(), Math.round(payload.number()))) status = "Unknown state: " + payload.value();
                }
                case START_MOVE -> {
                    if (!boss.startEditorMove()) status = "Could not move to the editor target.";
                }
                case STOP_MOVE -> boss.stopEditorMove();
                case CLEAR_TARGET -> boss.clearEditorTarget();
                case DESELECT -> {
                    boss.setManualControl(false);
                    BossSelection.clear();
                    sendSnapshot(player, "Boss deselected.");
                    return;
                }
            }

            sendSnapshot(player, status);
        });
    }

    private static boolean canUseEditor(ServerPlayer player) {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack());
    }

    private static void sendSnapshot(ServerPlayer player, String status) {
        if (!canUseEditor(player)) {
            PacketDistributor.sendToPlayer(player, SnapshotPayload.empty("You do not have permission to use the boss editor."));
            return;
        }

        BEBoss<?> boss = BossSelection.resolve(((ServerLevel) player.level()).getServer());
        if (boss == null) {
            PacketDistributor.sendToPlayer(player, SnapshotPayload.empty(status.isEmpty() ? "Select a boss" : status));
            return;
        }

        Entity targetEntity = boss.getControlTarget();
        Vec3 targetPosition = boss.getEditorTargetPosition();
        boolean hasTarget = targetPosition != null;

        String targetName = targetEntity != null ? targetEntity.getDisplayName().getString() : (hasTarget ? "Position" : "");
        double targetDistance = hasTarget ? boss.position().distanceTo(targetPosition) : 0.0;

        List<GoalInfo> goals = boss.getControlledGoalIds().stream().map(id -> new GoalInfo(id, boss.canForceGoal(id))).toList();

        SnapshotPayload snapshot = new SnapshotPayload(
                true,
                boss.getDisplayName().getString(),
                boss.getId(),
                boss.getHealth(),
                boss.getMaxHealth(),
                boss.isBossFrozen(),
                boss.isManualControl(),
                boss.isEditorMoving(),
                boss.getState().name().toLowerCase(Locale.ROOT),
                boss.getForcedGoalId(),
                hasTarget,
                targetName,
                hasTarget ? targetPosition.x : 0.0,
                hasTarget ? targetPosition.y : 0.0,
                hasTarget ? targetPosition.z : 0.0,
                targetDistance,
                List.copyOf(boss.getStateNames()),
                goals,
                status
        );

        PacketDistributor.sendToPlayer(player, snapshot);
    }

    public enum Action {
        SET_HEALTH, SET_FROZEN, RESUME_AI, FORCE_GOAL, CLEAR_GOAL, SET_STATE, START_MOVE, STOP_MOVE, CLEAR_TARGET, DESELECT
    }

    public record GoalInfo(String id, boolean available) {
        private GoalInfo(RegistryFriendlyByteBuf buffer) {
            this(buffer.readUtf(64), buffer.readBoolean());
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(this.id);
            buffer.writeBoolean(this.available);
        }
    }

    public record RequestSnapshotPayload() implements CustomPacketPayload {
        public static final RequestSnapshotPayload INSTANCE = new RequestSnapshotPayload();
        public static final Type<RequestSnapshotPayload> TYPE = new Type<>(BossApi.id("boss_editor_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestSnapshotPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ActionPayload(Action action, String value, float number) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE = new Type<>(BossApi.id("boss_editor_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> STREAM_CODEC = StreamCodec.ofMember(ActionPayload::write, ActionPayload::new);

        private ActionPayload(RegistryFriendlyByteBuf buffer) {
            this(readAction(buffer), buffer.readUtf(64), buffer.readFloat());
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(this.action.ordinal());
            buffer.writeUtf(this.value);
            buffer.writeFloat(this.number);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static ActionPayload setHealth(float health) { return new ActionPayload(Action.SET_HEALTH, "", health); }
        public static ActionPayload setFrozen(boolean frozen) { return new ActionPayload(Action.SET_FROZEN, "", frozen ? 1.0F : 0.0F); }
        public static ActionPayload resumeAi() { return new ActionPayload(Action.RESUME_AI, "", 0.0F); }
        public static ActionPayload forceGoal(String goal, int delayTicks) { return new ActionPayload(Action.FORCE_GOAL, goal, delayTicks); }
        public static ActionPayload clearGoal() { return new ActionPayload(Action.CLEAR_GOAL, "", 0.0F); }
        public static ActionPayload setState(String state, int delayTicks) { return new ActionPayload(Action.SET_STATE, state, delayTicks); }
        public static ActionPayload startMove() { return new ActionPayload(Action.START_MOVE, "", 0.0F); }
        public static ActionPayload stopMove() { return new ActionPayload(Action.STOP_MOVE, "", 0.0F); }
        public static ActionPayload clearTarget() { return new ActionPayload(Action.CLEAR_TARGET, "", 0.0F); }
        public static ActionPayload deselect() { return new ActionPayload(Action.DESELECT, "", 0.0F); }

        private static Action readAction(RegistryFriendlyByteBuf buffer) {
            int index = buffer.readVarInt();
            if (index < 0 || index >= Action.values().length) throw new IllegalArgumentException("Unknown boss editor action index: " + index);
            return Action.values()[index];
        }
    }

    public record SnapshotPayload(boolean selected, String bossName, int entityId, float health, float maxHealth, boolean frozen, boolean manualControl, boolean moving, String state, String forcedGoal, boolean hasTarget, String targetName, double targetX, double targetY, double targetZ, double targetDistance, List<String> states, List<GoalInfo> goals, String status) implements CustomPacketPayload {
        public static final Type<SnapshotPayload> TYPE = new Type<>(BossApi.id("boss_editor_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SnapshotPayload> STREAM_CODEC = StreamCodec.ofMember(SnapshotPayload::write, SnapshotPayload::new);

        public SnapshotPayload {
            states = List.copyOf(states);
            goals = List.copyOf(goals);
        }

        private SnapshotPayload(RegistryFriendlyByteBuf buffer) {
            this(buffer.readBoolean(), buffer.readUtf(128), buffer.readVarInt(), buffer.readFloat(), buffer.readFloat(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(64), buffer.readUtf(64), buffer.readBoolean(), buffer.readUtf(128), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), readStrings(buffer), readGoals(buffer), buffer.readUtf(256));
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeBoolean(this.selected);
            buffer.writeUtf(this.bossName);
            buffer.writeVarInt(this.entityId);
            buffer.writeFloat(this.health);
            buffer.writeFloat(this.maxHealth);
            buffer.writeBoolean(this.frozen);
            buffer.writeBoolean(this.manualControl);
            buffer.writeBoolean(this.moving);
            buffer.writeUtf(this.state);
            buffer.writeUtf(this.forcedGoal);
            buffer.writeBoolean(this.hasTarget);
            buffer.writeUtf(this.targetName);
            buffer.writeDouble(this.targetX);
            buffer.writeDouble(this.targetY);
            buffer.writeDouble(this.targetZ);
            buffer.writeDouble(this.targetDistance);
            writeStrings(buffer, this.states);
            writeGoals(buffer, this.goals);
            buffer.writeUtf(this.status);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static SnapshotPayload empty(String status) {
            return new SnapshotPayload(false, "No boss selected", -1, 0.0F, 1.0F, false, false, false, "", "", false, "", 0.0, 0.0, 0.0, 0.0, List.of(), List.of(), status);
        }
    }

    private static void writeStrings(RegistryFriendlyByteBuf buffer, List<String> values) {
        buffer.writeVarInt(values.size());
        for (String value : values) buffer.writeUtf(value);
    }

    private static List<String> readStrings(RegistryFriendlyByteBuf buffer) {
        int size = readListSize(buffer);
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) values.add(buffer.readUtf(64));
        return List.copyOf(values);
    }

    private static void writeGoals(RegistryFriendlyByteBuf buffer, List<GoalInfo> goals) {
        buffer.writeVarInt(goals.size());
        for (GoalInfo goal : goals) goal.write(buffer);
    }

    private static List<GoalInfo> readGoals(RegistryFriendlyByteBuf buffer) {
        int size = readListSize(buffer);
        List<GoalInfo> goals = new ArrayList<>(size);
        for (int i = 0; i < size; i++) goals.add(new GoalInfo(buffer));
        return List.copyOf(goals);
    }

    private static int readListSize(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_LIST_SIZE) throw new IllegalArgumentException("Invalid boss editor list size: " + size);
        return size;
    }
}

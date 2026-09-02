package com.danielkkrafft.bossapi.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Base for the mod's bosses, carrying an in-game editor/testing harness alongside the normal
 * autonomous AI. A boss defines an enum of animation states and registers {@link ControlledGoal}s;
 * each goal can be driven autonomously (its {@code canUseNormally}) or forced on demand from the
 * {@code /boss} commands and the boss editor UI ({@code canUseForced}). Manual control freezes the
 * autonomous target/goal selection so a single attack can be fired at a chosen block or entity and
 * observed deterministically. Ported from the piranha-bat branch.
 */
public abstract class BEBoss<S extends Enum<S>> extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Boolean> DATA_MANUAL_CONTROL = SynchedEntityData.defineId(BEBoss.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_FORCED_GOAL = SynchedEntityData.defineId(BEBoss.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_FROZEN = SynchedEntityData.defineId(BEBoss.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(BEBoss.class, EntityDataSerializers.INT);
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private Map<String, ControlledGoal<S, ?>> controlledGoals;
    private int pendingEditorStateTicks;
    private Vec3 editorTargetPosition;
    private UUID editorTargetEntity;
    private boolean editorMoving;
    private S pendingEditorState;
    private String pendingForcedGoal;

    protected BEBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.goalSelector.addGoal(0, new EditorMoveGoal());
        this.goalSelector.addGoal(0, new ManualIdleGoal());
    }

    protected abstract S[] getBossStates();

    protected boolean automaticallyTracksBossBar() { return true; }
    protected boolean shouldShowBossBar() { return true; }
    protected void tickBoss() {}
    protected void tickBossServer(ServerLevel level) {}

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, 0);
        builder.define(DATA_FROZEN, false);
        builder.define(DATA_MANUAL_CONTROL, false);
        builder.define(DATA_FORCED_GOAL, "");
    }

    public final Collection<String> getStateNames() {
        return Arrays.stream(this.getBossStates()).map(state -> state.name().toLowerCase(Locale.ROOT)).toList();
    }

    public final S getState() {
        S[] states = this.getBossStates();
        int index = this.entityData.get(DATA_STATE);
        return index >= 0 && index < states.length ? states[index] : states[0];
    }

    public final void setState(S state) { this.entityData.set(DATA_STATE, state.ordinal()); }

    public final boolean setState(String name) {
        for (S state : this.getBossStates()) {
            if (!state.name().equalsIgnoreCase(name)) continue;
            this.setState(state);
            return true;
        }

        return false;
    }

    protected final <G extends ControlledGoal<S, ?>> G addControlledGoal(int priority, G goal) {
        String id = normalizeGoalId(goal.getControlId());
        if (this.getControlledGoals().putIfAbsent(id, goal) != null) throw new IllegalStateException("Duplicate boss goal ID '" + id + "' on " + this.getClass().getSimpleName());

        this.goalSelector.addGoal(priority, goal);
        return goal;
    }

    public final Collection<String> getControlledGoalIds() { return List.copyOf(this.getControlledGoals().keySet()); }
    public final String getForcedGoalId() { return this.entityData.get(DATA_FORCED_GOAL); }
    public final boolean isGoalForced(String id) { return normalizeGoalId(id).equals(this.getForcedGoalId()); }
    public final void clearForcedGoal() { this.entityData.set(DATA_FORCED_GOAL, ""); }

    public final boolean forceGoal(String id) {
        id = normalizeGoalId(id);
        ControlledGoal<S, ?> goal = this.getControlledGoals().get(id);

        if (goal == null || !goal.canForceNow()) return false;

        this.stopEditorMove();
        this.entityData.set(DATA_FORCED_GOAL, id);

        if (this.isManualControl()) {
            this.stopMovement();
            this.setNoAi(false);
        }

        return true;
    }

    public final boolean forceEditorGoal(String id, int delayTicks) {
        id = normalizeGoalId(id);
        ControlledGoal<S, ?> goal = this.getControlledGoals().get(id);

        if (goal == null || !goal.canForceNow()) return false;

        delayTicks = Mth.clamp(delayTicks, 0, 100);

        if (delayTicks == 0) {
            this.applyEditorGoal(id);
        } else {
            this.pendingEditorState = null;
            this.pendingForcedGoal = id;
            this.pendingEditorStateTicks = delayTicks;
        }

        return true;
    }

    private void applyEditorGoal(String id) {
        this.pendingEditorState = null;
        this.pendingForcedGoal = null;
        this.pendingEditorStateTicks = 0;

        if (!this.isManualControl()) this.setManualControl(true);

        this.forceGoal(id);
    }

    public final void clearBossControl() {
        this.resetControlledAction();
        this.setNoAi(false);
    }

    public final boolean isManualControl() { return this.entityData.get(DATA_MANUAL_CONTROL); }

    public final void setManualControl(boolean manual) {
        if (this.isManualControl() == manual) return;

        this.entityData.set(DATA_MANUAL_CONTROL, manual);
        this.resetControlledAction();
        this.setNoAi(false);

        if (manual) {
            super.setTarget(null);
        } else {
            this.clearEditorTarget();
        }
    }

    @Override
    public void setTarget(LivingEntity target) {
        if (target != null && this.isManualControl()) return;
        super.setTarget(target);
    }

    public final boolean canForceGoal(String id) {
        ControlledGoal<S, ?> goal = this.getControlledGoals().get(normalizeGoalId(id));
        return goal != null && goal.canForceNow();
    }

    public final boolean isEditorMoving() {
        return this.editorMoving;
    }

    public final Entity getControlTarget() {
        return this.getEditorTargetEntity();
    }

    public final boolean setEditorState(String name) {
        return this.setEditorState(name, 0);
    }

    public final boolean setEditorState(String name, int delayTicks) {
        S selectedState = null;

        for (S state : this.getBossStates()) {
            if (!state.name().equalsIgnoreCase(name)) continue;
            selectedState = state;
            break;
        }

        if (selectedState == null) return false;

        delayTicks = Mth.clamp(delayTicks, 0, 100);

        if (delayTicks == 0) {
            this.applyEditorState(selectedState);
        } else {
            this.pendingEditorState = selectedState;
            this.pendingForcedGoal = null;
            this.pendingEditorStateTicks = delayTicks;
        }

        return true;
    }

    private void tickPendingEditorState() {
        if (this.pendingEditorState == null && this.pendingForcedGoal == null) return;
        if (--this.pendingEditorStateTicks > 0) return;

        S state = this.pendingEditorState;
        String goalId = this.pendingForcedGoal;

        if (state != null) {
            this.applyEditorState(state);
        } else {
            this.applyEditorGoal(goalId);
        }
    }

    private void applyEditorState(S state) {
        this.pendingEditorState = null;
        this.pendingForcedGoal = null;
        this.pendingEditorStateTicks = 0;

        if (!this.isManualControl()) this.setManualControl(true);

        this.editorMoving = false;
        this.clearForcedGoal();
        this.stopMovement();
        this.setNoAi(true);
        this.setState(state);
    }

    public final void setEditorTarget(Vec3 position) {
        if (!this.isManualControl()) this.setManualControl(true);
        this.editorTargetPosition = position;
        this.editorTargetEntity = null;
    }

    public final void setEditorTarget(Entity entity) {
        if (!this.isManualControl()) this.setManualControl(true);
        this.editorTargetPosition = null;
        this.editorTargetEntity = entity.getUUID();
    }

    public final void clearEditorTarget() {
        this.editorTargetPosition = null;
        this.editorTargetEntity = null;
    }

    public final boolean hasEditorTarget() { return this.getEditorTargetPosition() != null; }

    public final Vec3 getEditorTargetPosition() {
        Entity entity = this.getEditorTargetEntity();

        if (entity instanceof LivingEntity living && entity.isAlive()) return living.getEyePosition();
        if (entity != null && entity.isAlive()) return entity.position().add(0, entity.getBbHeight() * 0.5, 0);

        return this.editorTargetPosition;
    }

    public final Vec3 getEditorMoveTargetPosition() {
        Entity entity = this.getEditorTargetEntity();
        return entity != null && entity.isAlive() ? entity.position() : this.editorTargetPosition;
    }

    private Entity getEditorTargetEntity() {
        if (this.editorTargetEntity == null || !(this.level() instanceof ServerLevel level)) return null;
        return level.getEntity(this.editorTargetEntity);
    }

    public final boolean startEditorMove() {
        if (!this.isManualControl() || this.isBossFrozen() || this.getEditorMoveTargetPosition() == null) return false;

        this.clearForcedGoal();
        this.setState(this.getDefaultState());
        super.setTarget(null);

        this.editorMoving = true;
        this.stopMovement();
        this.setNoAi(false);
        return true;
    }

    public final void stopEditorMove() {
        this.editorMoving = false;
        this.stopManualMovement();
        this.setNoAi(false);
    }

    public final boolean isBossFrozen() { return this.entityData.get(DATA_FROZEN); }

    public final void setBossFrozen(boolean frozen) {
        this.entityData.set(DATA_FROZEN, frozen);

        if (frozen) {
            this.editorMoving = false;
            this.stopMovement();
        }
    }

    public final void toggleBossFrozen() { this.setBossFrozen(!this.isBossFrozen()); }
    public final void setBossHealth(float health) { this.setHealth(Mth.clamp(health, 1.0F, this.getMaxHealth())); }
    public final void setBossHealthPercent(float percent) { this.setBossHealth(this.getMaxHealth() * Mth.clamp(percent, 0.0F, 1.0F)); }

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel) this.tickPendingEditorState();

        if (this.isBossFrozen()) {
            this.stopMovement();
            return;
        }

        super.tick();
        this.tickBoss();

        if (this.level() instanceof ServerLevel level) this.tickBossServer(level);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("BEBossFrozen", this.isBossFrozen());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setBossFrozen(input.getBooleanOr("BEBossFrozen", false));
    }

    @Override
    public final AnimatableInstanceCache getAnimatableInstanceCache() { return this.geoCache; }

    protected final S getDefaultState() { return this.getBossStates()[0]; }

    private Map<String, ControlledGoal<S, ?>> getControlledGoals() {
        if (this.controlledGoals == null) this.controlledGoals = new LinkedHashMap<>();
        return this.controlledGoals;
    }

    private void resetControlledAction() {
        this.editorMoving = false;
        this.clearForcedGoal();
        this.setState(this.getDefaultState());
        this.stopMovement();
    }

    private void stopMovement() {
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
    }

    protected boolean usesCustomEditorMovement() { return false; }
    protected void tickCustomEditorMovement(Vec3 target) {}

    protected boolean hasReachedEditorMoveTarget(Vec3 target) {
        Vec3 horizontalPosition = this.position().multiply(1, 0, 1);
        Vec3 horizontalTarget = target.multiply(1, 0, 1);
        return horizontalPosition.distanceToSqr(horizontalTarget) <= 1.0 && Math.abs(this.getY() - target.y) <= 2.0;
    }

    protected void stopManualMovement() {
        this.getNavigation().stop();

        Vec3 movement = this.getDeltaMovement();
        this.setDeltaMovement(0.0, Math.min(movement.y, 0.0), 0.0);
    }

    private static String normalizeGoalId(String id) { return id.trim().toLowerCase(Locale.ROOT); }

    public abstract static class ControlledGoal<S extends Enum<S>, B extends BEBoss<S>> extends Goal {
        private final String controlId;
        private final S entryState;
        protected final B boss;

        protected ControlledGoal(B boss, String controlId, S entryState, EnumSet<Flag> flags) {
            this.boss = boss;
            this.controlId = normalizeGoalId(controlId);
            this.entryState = entryState;
            this.setFlags(flags);
        }

        public final String getControlId() { return this.controlId; }
        public final boolean canForceNow() { return !this.boss.isBossFrozen() && this.canUseForced(); }

        @Override
        public final boolean canUse() {
            if (this.boss.isBossFrozen()) return false;

            String forcedGoal = this.boss.getForcedGoalId();

            if (this.boss.isManualControl()) return forcedGoal.equals(this.controlId) && this.canUseForced();
            if (!forcedGoal.isEmpty()) return forcedGoal.equals(this.controlId) && this.canUseForced();

            return this.canUseNormally();
        }

        @Override
        public final boolean canContinueToUse() {
            String forcedGoal = this.boss.getForcedGoalId();

            if (!forcedGoal.isEmpty()) {
                if (!forcedGoal.equals(this.controlId)) return false;
                return this.canContinueForced();
            }

            return this.canContinueNormally();
        }

        @Override
        public final void start() {
            this.boss.setState(this.entryState);
            this.startControlled();
        }

        @Override
        public final void stop() {
            this.stopControlled();

            if (this.boss.isGoalForced(this.controlId)) this.boss.clearForcedGoal();

            if (this.boss.isManualControl()) {
                this.boss.setNoAi(false);
                this.boss.stopManualMovement();
            }
        }

        protected boolean canUseForced() { return true; }
        protected boolean canContinueForced() { return this.canContinueNormally(); }
        protected abstract boolean canUseNormally();
        protected abstract boolean canContinueNormally();
        protected void startControlled() {}
        protected void stopControlled() {}
    }

    private final class EditorMoveGoal extends Goal {
        private int repathTicks;

        private EditorMoveGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return this.canMove();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canMove();
        }

        @Override
        public void start() {
            this.repathTicks = 0;

            if (!BEBoss.this.usesCustomEditorMovement()) {
                this.moveToTarget();
            }
        }

        @Override
        public void tick() {
            Vec3 target = BEBoss.this.getEditorMoveTargetPosition();

            if (target == null) {
                BEBoss.this.editorMoving = false;
                BEBoss.this.stopManualMovement();
                return;
            }

            if (BEBoss.this.hasReachedEditorMoveTarget(target)) {
                BEBoss.this.editorMoving = false;
                BEBoss.this.stopManualMovement();
                return;
            }

            if (BEBoss.this.usesCustomEditorMovement()) {
                BEBoss.this.tickCustomEditorMovement(target);
            } else if (--this.repathTicks <= 0 || BEBoss.this.getNavigation().isDone()) {
                this.moveToTarget();
                this.repathTicks = 10;
            }
        }

        @Override
        public void stop() {
            BEBoss.this.editorMoving = false;
            BEBoss.this.stopManualMovement();
            BEBoss.this.setNoAi(false);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        private boolean canMove() {
            return BEBoss.this.isManualControl() && BEBoss.this.editorMoving && !BEBoss.this.isBossFrozen() && BEBoss.this.getEditorMoveTargetPosition() != null;
        }

        private void moveToTarget() {
            Vec3 target = BEBoss.this.getEditorMoveTargetPosition();
            if (target != null) BEBoss.this.getNavigation().moveTo(target.x, target.y, target.z, 1);
        }
    }

    private final class ManualIdleGoal extends Goal {
        private ManualIdleGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return this.shouldIdle();
        }

        @Override
        public boolean canContinueToUse() {
            return this.shouldIdle();
        }

        @Override
        public void start() {
            BEBoss.this.stopManualMovement();
        }

        @Override
        public void tick() {
            BEBoss.this.stopManualMovement();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        private boolean shouldIdle() {
            return BEBoss.this.isManualControl() && !BEBoss.this.editorMoving && BEBoss.this.getForcedGoalId().isEmpty() && !BEBoss.this.isBossFrozen();
        }
    }
}

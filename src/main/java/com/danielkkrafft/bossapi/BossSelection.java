package com.danielkkrafft.bossapi;

import com.danielkkrafft.bossapi.entity.BEBoss;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * The single globally-selected boss that the {@code /boss} commands, the Boss Wand, and the editor
 * screen all act on. Global (not per-player) on purpose: this is a dev/test tool, and a global
 * selection lets the commands work from {@code /execute} and command blocks with no player context.
 */
public final class BossSelection {
    private static UUID selectedBossId;

    private BossSelection() {}

    public static void select(BEBoss<?> boss) {
        MinecraftServer server = boss.level().getServer();
        BEBoss<?> previous = resolve(server);
        if (previous != null && previous != boss) previous.setManualControl(false);
        selectedBossId = boss.getUUID();
        boss.setManualControl(true);
    }

    public static void clear() {
        selectedBossId = null;
    }

    /** The selected boss if it is still alive and loaded, else null (clearing a stale selection). */
    public static BEBoss<?> resolve(MinecraftServer server) {
        if (selectedBossId == null || server == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(selectedBossId) instanceof BEBoss<?> boss && boss.isAlive()) return boss;
        }
        selectedBossId = null;
        return null;
    }
}

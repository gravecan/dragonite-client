package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public final class HealthEstimate {

    private static final Map<UUID, Float> TRACKED = new HashMap<>();
    private static final Map<UUID, Integer> LAST_HURT = new HashMap<>();
    private static final float DEFAULT_MAX = 20f;

    private HealthEstimate() {}

    
    public static float resolveHealth(LivingEntity entity) {
        if (!(entity instanceof PlayerEntity player)) {
            return entity.getHealth();
        }
        float reported = player.getHealth();
        float max = Math.max(1f, player.getMaxHealth());

        if (reported < max - 0.05f) {
            TRACKED.put(player.getUuid(), reported);
            return reported;
        }

        if (!isSpoofed(player)) {
            return reported;
        }

        tickEntity(player);
        Float est = TRACKED.get(player.getUuid());
        if (est != null) {
            return est;
        }
        Float scoreboard = readScoreboardHealth(player);
        if (scoreboard != null) {
            return scoreboard;
        }
        return reported;
    }

    public static float resolveMaxHealth(LivingEntity entity) {
        return Math.max(1f, entity.getMaxHealth());
    }

    public static boolean isSpoofed(PlayerEntity player) {
        float reported = player.getHealth();
        float max = Math.max(1f, player.getMaxHealth());
        if (reported < max - 0.05f) {
            return false;
        }
        Float est = TRACKED.get(player.getUuid());
        if (est != null && est < max - 0.25f) {
            return true;
        }
        if (player.hurtTime > 0) {
            return true;
        }
        Float scoreboard = readScoreboardHealth(player);
        return scoreboard != null && scoreboard < max - 0.05f;
    }

    public static void tickEntity(PlayerEntity player) {
        if (!isSpoofed(player) && !TRACKED.containsKey(player.getUuid())) {
            return;
        }

        UUID id = player.getUuid();
        TRACKED.putIfAbsent(id, DEFAULT_MAX);

        int hurt = player.hurtTime;
        Integer prev = LAST_HURT.get(id);
        LAST_HURT.put(id, hurt);

        float reported = player.getHealth();
        float max = Math.max(1f, player.getMaxHealth());
        boolean looksSpoofed = reported >= max - 0.05f && hurt > 0;

        if (looksSpoofed && prev != null && hurt > prev) {
            float est = TRACKED.getOrDefault(id, DEFAULT_MAX);
            float drop = 1.5f + (10 - hurt) * 0.45f;
            TRACKED.put(id, Math.max(0f, est - drop));
        }

        Float scoreboard = readScoreboardHealth(player);
        if (scoreboard != null && scoreboard < max - 0.05f) {
            TRACKED.put(id, scoreboard);
        }
    }

    public static void onDamagePacket(int entityId) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            return;
        }
        if (mc.world.getEntityById(entityId) instanceof PlayerEntity player) {
            if (!isSpoofed(player)) {
                return;
            }
            UUID id = player.getUuid();
            float est = TRACKED.getOrDefault(id, DEFAULT_MAX);
            float drop = 2.5f;
            if (mc.player != null && HitRegistration.getLastTarget() == player) {
                drop = estimateWeaponDamage(mc);
            }
            TRACKED.put(id, Math.max(0f, est - drop));
        }
    }

    public static void onOurAttack(PlayerEntity victim) {
        if (!isSpoofed(victim)) {
            return;
        }
        UUID id = victim.getUuid();
        float est = TRACKED.getOrDefault(id, DEFAULT_MAX);
        TRACKED.put(id, Math.max(0f, est - estimateWeaponDamage(MinecraftClient.getInstance())));
    }

    public static void reset(PlayerEntity player) {
        TRACKED.put(player.getUuid(), DEFAULT_MAX);
        LAST_HURT.remove(player.getUuid());
    }

    public static void remove(UUID id) {
        TRACKED.remove(id);
        LAST_HURT.remove(id);
    }

    private static Float readScoreboardHealth(PlayerEntity player) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            return null;
        }
        Scoreboard board = mc.world.getScoreboard();
        ScoreboardObjective objective = board.getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME);
        if (objective == null) {
            for (ScoreboardObjective obj : board.getObjectives()) {
                if ("health".equals(obj.getName())) {
                    objective = obj;
                    break;
                }
            }
        }
        if (objective == null) {
            return null;
        }
        try {
            ReadableScoreboardScore score = board.getScore(
                    ScoreHolder.fromName(player.getNameForScoreboard()),
                    objective
            );
            if (score == null) {
                return null;
            }
            int v = score.getScore();
            if (v <= 0) {
                return null;
            }
            if (v <= 40) {
                return (float) v;
            }
            if (v <= 200) {
                return v / 2f;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static float estimateWeaponDamage(MinecraftClient mc) {
        if (mc == null || mc.player == null) {
            return 3f;
        }
        float cooldown = mc.player.getAttackCooldownProgress(0.5f);
        float base = 3f + cooldown * 4f;
        return Math.min(12f, base);
    }
}

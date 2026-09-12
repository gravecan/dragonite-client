package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.Vec3d;


public final class CombatMovementSync {
    private static final int SUPPRESS_TICKS = 20;
    
    private static final int POST_SUPPRESS_COOLDOWN = 8;
    private static final int POST_HIT_UNSTABLE_TICKS = 8;
    
    private static final int ATTACK_SLOW_TICKS = 6;
    private static final double ATTACK_SLOW_FACTOR = 0.6;

    private static int suppressAutoSprintTicks;
    private static int attackSlowTicks;
    private static double postAttackSpeedCap;
    
    private static int unstableTicks;
    private static int postSuppressCooldown;
    
    private static int friendAttackSlowTicks;
    private static double friendPostHitSpeedCap;

    private CombatMovementSync() {}

    
    public static void prepareMeleeAttack(MinecraftClient mc, boolean sprintReset) {
        if (mc.player == null || !sprintReset) {
            return;
        }

        boolean onGround = mc.player.isOnGround();
        boolean moving = isHorizontallyMoving(mc);
        boolean sprinting = mc.player.isSprinting();

        
        if (onGround && sprinting) {
            mc.getNetworkHandler().sendPacket(
                    new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        }

        if (!moving && !(onGround && sprinting)) {
            return;
        }

        
        if (onGround || mc.player.isFallFlying()) {
            applyMeleeSlowdown(mc);
            if (onGround && moving) {
                suppressAutoSprintTicks = SUPPRESS_TICKS;
                postAttackSpeedCap = mc.player.getVelocity().horizontalLength() * ATTACK_SLOW_FACTOR;
            }
        }
    }

    public static void prepareMeleeAttack(MinecraftClient mc) {
        prepareMeleeAttack(mc, true);
    }

    
    public static boolean prepareFriendSprintReset(MinecraftClient mc, boolean sprintReset) {
        if (mc.player == null || !sprintReset) {
            return false;
        }
        if (mc.player.isOnGround() && mc.player.isSprinting()) {
            mc.getNetworkHandler().sendPacket(
                    new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            return true;
        }
        return false;
    }

    private static void applyMeleeSlowdown(MinecraftClient mc) {
        applyClientAttackSlowdown(mc);
        attackSlowTicks = Math.max(attackSlowTicks, ATTACK_SLOW_TICKS);
        friendAttackSlowTicks = Math.max(friendAttackSlowTicks, ATTACK_SLOW_TICKS);
        friendPostHitSpeedCap = mc.player.getVelocity().horizontalLength();
    }

    
    public static void onFriendMeleeHit(MinecraftClient mc) {
        if (mc.player == null || attackSlowTicks > 0) {
            return;
        }
        if (mc.player.isOnGround() || mc.player.isFallFlying()) {
            applyMeleeSlowdown(mc);
        }
    }

    
    public static void onAuraMeleeHit(MinecraftClient mc) {
        if (mc.player == null) {
            return;
        }
        unstableTicks = Math.max(unstableTicks, POST_HIT_UNSTABLE_TICKS);
        postSuppressCooldown = Math.max(postSuppressCooldown, POST_SUPPRESS_COOLDOWN);
        if (isHorizontallyMoving(mc)) {
            attackSlowTicks = Math.max(attackSlowTicks, ATTACK_SLOW_TICKS);
            enforceAttackSlowCap(mc);
        }
    }

    @Deprecated
    public static void onMeleeAttack(MinecraftClient mc) {
        prepareMeleeAttack(mc);
    }

    private static void applyClientAttackSlowdown(MinecraftClient mc) {
        Vec3d vel = mc.player.getVelocity();
        mc.player.setVelocity(vel.x * ATTACK_SLOW_FACTOR, vel.y, vel.z * ATTACK_SLOW_FACTOR);
    }

    
    private static void enforceAttackSlowCap(MinecraftClient mc) {
        if (mc.player == null || attackSlowTicks <= 0 || postAttackSpeedCap <= 0 || unstableTicks > 0) {
            return;
        }
        Vec3d vel = mc.player.getVelocity();
        double h = Math.hypot(vel.x, vel.z);
        if (h <= postAttackSpeedCap + 1e-4) {
            return;
        }
        double scale = postAttackSpeedCap / h;
        mc.player.setVelocity(vel.x * scale, vel.y, vel.z * scale);
    }

    public static boolean shouldSuppressAutoSprint() {
        return suppressAutoSprintTicks > 0;
    }

    public static boolean shouldDelayAuraHit() {
        return suppressAutoSprintTicks > 0 || postSuppressCooldown > 0;
    }

    public static boolean blocksMovingMelee(MinecraftClient mc, boolean allowSprintReset) {
        if (mc.player == null) {
            return false;
        }
        if (allowSprintReset) {
            return false;
        }
        return isHorizontallyMoving(mc) && mc.player.isSprinting();
    }

    public static boolean blocksMovingMelee(MinecraftClient mc) {
        return blocksMovingMelee(mc, false);
    }

    
    public static boolean shouldSkipAuraStrike(MinecraftClient mc) {
        if (mc.player == null) {
            return true;
        }
        if (unstableTicks > 0) {
            return true;
        }
        if (mc.player.hurtTime > 0) {
            return true;
        }
        if (mc.player.isFallFlying()) {
            return false;
        }
        if (!mc.player.isOnGround()) {
            double vy = mc.player.getVelocity().y;
            if (mc.player.fallDistance > 0.55f || Math.abs(vy) > 0.55) {
                return true;
            }
        }
        return false;
    }

    
    public static void clearMeleeState() {
        suppressAutoSprintTicks = 0;
        attackSlowTicks = 0;
        postSuppressCooldown = 0;
        postAttackSpeedCap = 0;
        friendAttackSlowTicks = 0;
        friendPostHitSpeedCap = 0;
    }

    private static void enforceFriendAttackSlowCap(MinecraftClient mc) {
        if (mc.player == null || friendAttackSlowTicks <= 0 || friendPostHitSpeedCap <= 0) {
            return;
        }
        Vec3d vel = mc.player.getVelocity();
        double h = Math.hypot(vel.x, vel.z);
        if (h <= friendPostHitSpeedCap + 1.0E-4) {
            return;
        }
        double scale = friendPostHitSpeedCap / h;
        mc.player.setVelocity(vel.x * scale, vel.y, vel.z * scale);
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            if (mc.player.hurtTime > 0) {
                unstableTicks = 10;
                attackSlowTicks = 0;
            }
        }
        if (unstableTicks > 0) {
            unstableTicks--;
        }
        if (suppressAutoSprintTicks > 0) {
            suppressAutoSprintTicks--;
            if (suppressAutoSprintTicks == 0 && isHorizontallyMoving(mc)) {
                postSuppressCooldown = POST_SUPPRESS_COOLDOWN;
            }
        } else if (postSuppressCooldown > 0) {
            postSuppressCooldown--;
        }
        if (attackSlowTicks > 0) {
            attackSlowTicks--;
            if (suppressAutoSprintTicks > 0 && mc.player != null && isHorizontallyMoving(mc)) {
                enforceAttackSlowCap(mc);
            }
        }
        if (friendAttackSlowTicks > 0) {
            friendAttackSlowTicks--;
            if (mc.player != null) {
                enforceFriendAttackSlowCap(mc);
            }
        }
    }

    public static boolean isHorizontallyMoving(MinecraftClient mc) {
        if (mc.player == null) {
            return false;
        }
        if (mc.player.input != null) {
            if (Math.abs(mc.player.input.movementForward) > 1e-3f
                    || Math.abs(mc.player.input.movementSideways) > 1e-3f) {
                return true;
            }
        }
        return mc.player.getVelocity().horizontalLengthSquared() > 1e-4;
    }
}

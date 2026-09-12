package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Silently throws an ender pearl toward the predicted position of a nearby
 * opponent so you can land on them instantly.
 */
public class Config_PearlTracking extends ConfigCategoryImpl {

    public static Config_PearlTracking INSTANCE;

    private final DoubleFieldBuilder range;
    private final DoubleFieldBuilder cooldown;
    private final DoubleFieldBuilder predictTicks;
    private final BooleanToggleBuilder onlyWhenLooking;
    private final BooleanToggleBuilder requireHotbar;

    private long nextThrowMs;

    public Config_PearlTracking() {
        super("Pearl Tracking", "Silently pearls to a predicted opponent position", Cat.COMBAT);
        INSTANCE = this;
        setTooltip("Finds a nearby player, predicts where they'll be, then silently throws a pearl there.");

        range = new DoubleFieldBuilder("Range", "Max target distance", 48, 8, 80, 1);
        cooldown = new DoubleFieldBuilder("Cooldown", "Ms between pearls", 650, 200, 3000, 25);
        predictTicks = new DoubleFieldBuilder("Predict", "Ticks of movement to lead", 8, 0, 30, 1);
        onlyWhenLooking = new BooleanToggleBuilder("Only When Looking", "Require target roughly in front of you", false);
        requireHotbar = new BooleanToggleBuilder("Hotbar Only", "Pearl must be on the hotbar", true);

        addSetting(range);
        addSetting(cooldown);
        addSetting(predictTicks);
        addSetting(onlyWhenLooking);
        addSetting(requireHotbar);
    }

    public void tick(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null || mc.world == null || mc.currentScreen != null) {
            return;
        }
        if (mc.getNetworkHandler() == null || mc.interactionManager == null) {
            return;
        }
        if (mc.player.isUsingItem() || mc.player.isSpectator()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextThrowMs) {
            return;
        }

        int pearlSlot = findPearl(mc);
        if (pearlSlot < 0) {
            return;
        }

        PlayerEntity target = findTarget(mc);
        if (target == null) {
            return;
        }

        Vec3d aimPoint = predictAimPoint(mc, target);
        float[] look = lookAt(mc.player.getEyePos(), aimPoint);

        // Silent look so the pearl flies toward the predicted point.
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                look[0], look[1], mc.player.isOnGround()));

        if (!HotbarSilentUse.useItemFromHotbar(mc, pearlSlot)) {
            return;
        }

        nextThrowMs = now + (long) cooldown.get();
    }

    private PlayerEntity findTarget(MinecraftClient mc) {
        double max = range.get();
        double maxSq = max * max;
        PlayerEntity best = null;
        double bestSq = maxSq;

        float yaw = mc.player.getYaw();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator() || player.isInvisible()) {
                continue;
            }
            if (FriendManager.isCombatExempt(player)) {
                continue;
            }
            ConfigBuilderImpl mgr = HudConfigInit.getManager();
            ListEntryImpl ab = mgr != null ? mgr.getModuleByClass(ListEntryImpl.class) : null;
            if (ab != null && ab.isEnabled() && ab.isBot(player)) {
                continue;
            }

            double distSq = mc.player.squaredDistanceTo(player);
            if (distSq > bestSq || distSq < 2.25) {
                continue;
            }

            if (onlyWhenLooking.get()) {
                Vec3d to = player.getPos().subtract(mc.player.getEyePos()).normalize();
                Vec3d look = mc.player.getRotationVec(1f);
                if (look.dotProduct(to) < 0.15) {
                    continue;
                }
            }

            best = player;
            bestSq = distSq;
        }
        return best;
    }

    private Vec3d predictAimPoint(MinecraftClient mc, PlayerEntity target) {
        Vec3d pos = target.getPos().add(0, target.getHeight() * 0.45, 0);
        Vec3d vel = target.getVelocity();
        double lead = predictTicks.get();
        Vec3d predicted = pos.add(vel.x * lead, vel.y * lead * 0.55, vel.z * lead);

        // Lead a bit more for pearl flight time over distance.
        double dist = mc.player.getEyePos().distanceTo(predicted);
        double flightTicks = MathHelper.clamp(dist / 1.35, 4.0, 28.0);
        return predicted.add(vel.x * flightTicks * 0.35, Math.max(0, vel.y) * flightTicks * 0.15, vel.z * flightTicks * 0.35);
    }

    private static float[] lookAt(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90f, 90f)};
    }

    private int findPearl(MinecraftClient mc) {
        int end = requireHotbar.get() ? 9 : Math.min(36, mc.player.getInventory().size());
        for (int i = 0; i < end; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.ENDER_PEARL)) {
                if (i <= 8) {
                    return i;
                }
            }
        }
        return InventoryHelper.findHotbarItem(mc, Items.ENDER_PEARL);
    }

    @Override
    public void onEnable() {
        nextThrowMs = 0;
    }
}

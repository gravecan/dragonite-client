package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;


public class Config_AirStuck extends ConfigCategoryImpl {

    public static Config_AirStuck INSTANCE;

    private static final double ELYTRA_SYNC_DROP = 0.01;
    private static final ThreadLocal<Integer> ANCHOR_SEND_DEPTH = ThreadLocal.withInitial(() -> 0);

    private final BooleanToggleBuilder antiKick;
    private final DoubleFieldBuilder freezeDuration;
    private final DoubleFieldBuilder releaseDuration;

    private Vec3d anchor = Vec3d.ZERO;
    private float lockedYaw;
    private float lockedPitch;
    private int syncTicks;
    private int cycleTicks;
    private boolean isCurrentlyFrozen = true;

    public Config_AirStuck() {
        super("Air Stuck", "Freeze in midair (bind a key)", Cat.MOVEMENT);
        INSTANCE = this;
        setTooltip("Toggle only. Locks position and syncs anchor packets to the server.");

        antiKick = new BooleanToggleBuilder("Anti Kick", "Cyclic freeze/release bypass", true);
        freezeDuration = new DoubleFieldBuilder("Freeze Ticks", "Ticks of position lock", 10.0, 1.0, 250.0, 1.0);
        releaseDuration = new DoubleFieldBuilder("Release Ticks", "Ticks of normal movement", 10.0, 1.0, 50.0, 1.0);

        addSetting(antiKick);
        addSetting(freezeDuration);
        addSetting(releaseDuration);

        freezeDuration.setVisibleWhen(antiKick::get);
        releaseDuration.setVisibleWhen(antiKick::get);
    }

    public static boolean isAnchorSend() {
        return ANCHOR_SEND_DEPTH.get() > 0;
    }

    @Override
    public void onEnable() {
        captureAnchor(MinecraftClient.getInstance());
        syncTicks = 0;
        cycleTicks = 0;
        isCurrentlyFrozen = true;
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.getNetworkHandler() != null && anchor != Vec3d.ZERO) {
            pushAnchorPacket(mc);
        }
        anchor = Vec3d.ZERO;
        syncTicks = 0;
        cycleTicks = 0;
    }

    public void tick(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null) {
            return;
        }

        if (antiKick.get()) {
            int freezeLimit = (int) freezeDuration.get();
            int releaseLimit = (int) releaseDuration.get();
            int totalCycle = freezeLimit + releaseLimit;

            int currentTick = cycleTicks % totalCycle;
            cycleTicks++;

            if (currentTick < freezeLimit) {
                isCurrentlyFrozen = true;
                if (anchor == Vec3d.ZERO) {
                    captureAnchor(mc);
                }
            } else {
                isCurrentlyFrozen = false;
                // Release: update anchor to player's current falling/moving position
                anchor = mc.player.getPos();
                return;
            }
        } else {
            isCurrentlyFrozen = true;
            if (anchor == Vec3d.ZERO) {
                captureAnchor(mc);
            }
        }

        mc.player.setVelocity(Vec3d.ZERO);
        mc.player.fallDistance = 0f;
        mc.player.setPosition(anchor);

        if (mc.player.input != null) {
            mc.player.input.movementForward = 0f;
            mc.player.input.movementSideways = 0f;
            mc.player.input.jumping = false;
            mc.player.input.sneaking = false;
        }

        if (mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }

        lockedYaw = mc.player.getYaw();
        lockedPitch = mc.player.getPitch();

        syncTicks++;
        if (syncTicks >= 10) {
            if (isElytraGliding(mc)) {
                anchor = anchor.subtract(0.0, ELYTRA_SYNC_DROP, 0.0);
                mc.player.setPosition(anchor);
            }
            pushAnchorPacket(mc);
            syncTicks = 0;
        }
    }

    private static boolean isElytraGliding(MinecraftClient mc) {
        return mc.player.isFallFlying()
                && mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    
    public boolean handleOutbound(Packet<?> packet) {
        if (isAnchorSend() || !isEnabled() || !isCurrentlyFrozen || !(packet instanceof PlayerMoveC2SPacket)) {
            return false;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null || anchor == Vec3d.ZERO) {
            return false;
        }
        pushAnchorPacket(mc);
        return true;
    }

    private void pushAnchorPacket(MinecraftClient mc) {
        if (mc.getNetworkHandler() == null || mc.player == null || anchor == Vec3d.ZERO) {
            return;
        }
        int depth = ANCHOR_SEND_DEPTH.get();
        ANCHOR_SEND_DEPTH.set(depth + 1);
        try {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                    anchor.x,
                    anchor.y,
                    anchor.z,
                    lockedYaw,
                    lockedPitch,
                    mc.player.isOnGround()
            ));
        } finally {
            if (depth == 0) {
                ANCHOR_SEND_DEPTH.remove();
            } else {
                ANCHOR_SEND_DEPTH.set(depth);
            }
        }
    }

    private void captureAnchor(MinecraftClient mc) {
        if (mc != null && mc.player != null) {
            anchor = mc.player.getPos();
            lockedYaw = mc.player.getYaw();
            lockedPitch = mc.player.getPitch();
        }
    }
}

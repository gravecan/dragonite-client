package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;


public class Config_NoFall extends ConfigCategoryImpl {

    public static Config_NoFall INSTANCE;

    private static final float MIN_FALL = 2.5f;

    public Config_NoFall() {
        super("No Fall", "Prevents fall damage by spoofing on-ground while falling from lethal height.", Cat.MOVEMENT);
        INSTANCE = this;
        setTooltip("Spoofs onGround when falling 2.5+ blocks. Elytra glide keeps fallDistance at 0.");
    }

    public void tick(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null) {
            return;
        }
        ClientPlayerEntity player = mc.player;
        if (player.isFallFlying()) {
            player.fallDistance = 0f;
        }
    }

    public Packet<?> handleOutbound(Packet<?> packet) {
        if (!isEnabled() || !(packet instanceof PlayerMoveC2SPacket move)) {
            return packet;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || !shouldProcess(player)) {
            return packet;
        }

        if (player.isFallFlying()) {
            player.fallDistance = 0f;
            return packet;
        }

        if (!shouldSpoofGround(player)) {
            return packet;
        }

        player.fallDistance = 0f;
        if (player.isOnGround()) {
            player.onLanding();
        }
        return rebuildWithGround(move, player);
    }

    private static boolean shouldSpoofGround(ClientPlayerEntity player) {
        if (player.isOnGround()) {
            return false;
        }
        if (player.fallDistance >= MIN_FALL) {
            return true;
        }
        // Force ground spoof if velocity downwards is high (like fast fly/glide fall)
        if (player.getVelocity().y < -0.45) {
            return true;
        }
        return false;
    }

    private static boolean shouldProcess(ClientPlayerEntity player) {
        if (player.hasVehicle()) {
            return false;
        }
        if (player.isTouchingWater() || player.isSubmergedInWater() || player.isClimbing()) {
            return false;
        }
        return !player.isSpectator();
    }

    private static boolean hasElytra(ClientPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private static Packet<?> rebuildWithGround(PlayerMoveC2SPacket packet, ClientPlayerEntity player) {
        if (!packet.changesPosition() && !packet.changesLook()) {
            return new PlayerMoveC2SPacket.OnGroundOnly(true);
        }
        if (packet.changesPosition() && packet.changesLook()) {
            return new PlayerMoveC2SPacket.Full(
                    packet.getX(player.getX()),
                    packet.getY(player.getY()),
                    packet.getZ(player.getZ()),
                    packet.getYaw(player.getYaw()),
                    packet.getPitch(player.getPitch()),
                    true);
        }
        if (packet.changesPosition()) {
            return new PlayerMoveC2SPacket.PositionAndOnGround(
                    packet.getX(player.getX()),
                    packet.getY(player.getY()),
                    packet.getZ(player.getZ()),
                    true);
        }
        return new PlayerMoveC2SPacket.LookAndOnGround(
                packet.getYaw(player.getYaw()),
                packet.getPitch(player.getPitch()),
                true);
    }
}

package me.shedaniel.clothconfig2.impl;

import me.shedaniel.math.impl.mixin.PlayerInteractEntityC2SPacketAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


public class Config_Criticals extends ConfigCategoryImpl {

    public static Config_Criticals INSTANCE;

    private final me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder modeSetting;

    public Config_Criticals() {
        super("Criticals", "Turns hits on players into critical hits", Cat.COMBAT);
        INSTANCE = this;

        modeSetting = new me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder(
                "Mode", "Critical attack method", "On Jump", "On Jump", "Packet");
        addSetting(modeSetting);
    }

    public boolean handleOutbound(Packet<?> packet, CallbackInfo ci) {
        if (!isEnabled()) {
            return false;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return false;
        }

        if (!(packet instanceof PlayerInteractEntityC2SPacket attackPacket)) {
            return false;
        }

        if (!isAttackPacket(attackPacket)) {
            return false;
        }

        PlayerInteractEntityC2SPacketAccessor access = (PlayerInteractEntityC2SPacketAccessor) (Object) attackPacket;
        Entity entity = mc.world != null ? mc.world.getEntityById(access.cloth$getEntityId()) : null;
        if (!(entity instanceof PlayerEntity)) {
            return false;
        }

        String mode = modeSetting.get();
        if (mode.equals("Packet")) {
            if (canCrit(mc)) {
                sendGrimCritPackets(mc);
            }
        } else if (mode.equals("On Jump")) {
            if (!mc.player.isOnGround() && mc.player.fallDistance == 0.0f && !mc.player.isSubmergedInWater()) {
                mc.player.fallDistance = (float) (0.00001 + Math.random() * 0.00009);
            }
        }
        return false;
    }

    private static boolean canCrit(MinecraftClient mc) {
        return mc.player.isOnGround()
                && !mc.player.isSubmergedInWater()
                && !mc.player.isInLava()
                && !mc.player.isClimbing();
    }

    
    private static void sendGrimCritPackets(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.getNetworkHandler() == null) {
            return;
        }

        if (player.isSprinting()) {
            mc.getNetworkHandler().sendPacket(
                    new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        }

        float yaw = resolvePacketYaw(mc);
        float pitch = resolvePacketPitch(mc);
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        player.fallDistance = 0.001f;

        sendFull(mc, x, y + 0.0625, z, yaw, pitch, false);
        sendFull(mc, x, y + 0.0625001, z, yaw, pitch, false);
        sendFull(mc, x, y + 1.0E-6, z, yaw, pitch, false);
    }

    private static float resolvePacketYaw(MinecraftClient mc) {
        return mc.player.getYaw();
    }

    private static float resolvePacketPitch(MinecraftClient mc) {
        return mc.player.getPitch();
    }

    private static void sendFull(MinecraftClient mc, double x, double y, double z,
                                 float yaw, float pitch, boolean onGround) {
        mc.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.Full(x, y, z, yaw, pitch, onGround));
    }

    private static boolean isAttackPacket(PlayerInteractEntityC2SPacket packet) {
        boolean[] attack = {false};
        packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
            @Override
            public void attack() {
                attack[0] = true;
            }

            @Override
            public void interact(net.minecraft.util.Hand hand) {
            }

            @Override
            public void interactAt(net.minecraft.util.Hand hand, Vec3d pos) {
            }
        });
        return attack[0];
    }
}

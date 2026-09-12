package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Config_VClip extends ConfigCategoryImpl {
    
    private static Config_VClip INSTANCE;

    private final EnumSelectorBuilder direction;
    private final DoubleFieldBuilder blocks;
    private final BooleanToggleBuilder requireBlocks;

    public Config_VClip() {
        super("VClip", "Instant teleportation through block boundaries", Cat.MOVEMENT);
        INSTANCE = this;
        setEnabled(false);

        direction = new EnumSelectorBuilder("Direction", "Teleport direction", "Up", "Up", "Down", "Left", "Right");
        blocks = new DoubleFieldBuilder("Blocks", "Teleport distance in blocks", 2.0, 1.0, 10.0, 1.0);
        requireBlocks = new BooleanToggleBuilder("Require Blocks", "Prevent teleporting into midair", false);

        addSetting(direction);
        addSetting(blocks);
        addSetting(requireBlocks);
    }

    public static Config_VClip getInstance() {
        return INSTANCE;
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) {
            setEnabled(false);
            return;
        }

        double distance = blocks.get();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        double targetX = x;
        double targetY = y;
        double targetZ = z;

        String dir = direction.get();
        switch (dir) {
            case "Up":
                targetY = y + distance;
                break;
            case "Down":
                targetY = y - distance;
                break;
            case "Left":
            case "Right":
                float yaw = player.getYaw();
                float angle = yaw + (dir.equals("Left") ? -90.0f : 90.0f);
                double rad = Math.toRadians(angle);
                targetX = x + (-Math.sin(rad) * distance);
                targetZ = z + (Math.cos(rad) * distance);
                break;
        }

        // Perform Require Blocks midair check if enabled
        if (requireBlocks.get()) {
            BlockPos pos = BlockPos.ofFloored(targetX, targetY - 0.5, targetZ);
            if (mc.world.getBlockState(pos).isAir()) {
                player.sendMessage(net.minecraft.text.Text.literal("§c[VClip] Teleport canceled: Target location is in midair."), true);
                setEnabled(false);
                return;
            }
        }

        player.setPosition(targetX, targetY, targetZ);

        // Auto disable after one-time clip execution
        setEnabled(false);
    }
}

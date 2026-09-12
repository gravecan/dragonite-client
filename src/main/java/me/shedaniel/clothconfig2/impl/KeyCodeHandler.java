package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class KeyCodeHandler extends ConfigCategoryImpl {
    public static KeyCodeHandler INSTANCE;

    private final EnumSelectorBuilder mode = new EnumSelectorBuilder("Mode", "Clutch mode", "Auto", "Auto", "Fall", "Manual");
    private final EnumSelectorBuilder item = new EnumSelectorBuilder("Item", "Clutch item", "Block", "Block", "Ender Pearl", "Water Bucket");
    private final DoubleFieldBuilder fallDistance = new DoubleFieldBuilder("Fall Distance", "Trigger distance", 5.0, 3.0, 20.0, 0.5);
    private final DoubleFieldBuilder aimSpeed = new DoubleFieldBuilder("Aim Speed", "Rotation smoothness", 10.0, 1.0, 20.0, 0.5);
    private final BooleanToggleBuilder silentAim = new BooleanToggleBuilder("Silent Aim", "Use silent rotation", true);
    private final BooleanToggleBuilder autoSwap = new BooleanToggleBuilder("Auto Swap", "Auto swap to item", true);
    private final BooleanToggleBuilder onlyVoid = new BooleanToggleBuilder("Only Void", "Only clutch over void", true);
    private final BooleanToggleBuilder randomize = new BooleanToggleBuilder("Randomize", "Randomize timing", true);

    private final Random random = new Random();
    private float silentYaw = 0f;
    private float silentPitch = 0f;
    private boolean hasSilentRotation = false;
    private boolean hasClutched = false;
    private int lastSlot = -1;

    public KeyCodeHandler() {
        super("Clutch", "Auto clutch to prevent fall damage", Cat.MOVEMENT);
        INSTANCE = this;
        addSetting(mode);
        addSetting(item);
        addSetting(fallDistance);
        addSetting(aimSpeed);
        addSetting(silentAim);
        addSetting(autoSwap);
        addSetting(onlyVoid);
        addSetting(randomize);
    }

    @Override
    public void onEnable() {
        hasSilentRotation = false;
        hasClutched = false;
        lastSlot = -1;
    }

    @Override
    public void onDisable() {
        if (lastSlot != -1 && autoSwap.get()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (lastSlot >= 0 && lastSlot < 9) {
                InventoryHelper.setSelectedSlot(mc, lastSlot);
            }
        }
        hasSilentRotation = false;
    }

    public boolean hasSilentRotation() { return hasSilentRotation; }
    public float getSilentYaw() { return silentYaw; }
    public float getSilentPitch() { return silentPitch; }

    public void tick(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null || mc.world == null) return;
        
        if (mode.get().equals("Manual")) return;
        
        if (mc.player.isOnGround()) {
            hasClutched = false;
            hasSilentRotation = false;
            return;
        }

        double fallDist = mc.player.fallDistance;
        if (fallDist < fallDistance.get()) return;
        
        if (onlyVoid.get() && isOverGround(mc)) return;
        
        if (hasClutched) return;

        String itemType = item.get();
        int slot = findItemSlot(mc, itemType);
        if (slot == -1) return;

        if (autoSwap.get() && lastSlot == -1) {
            lastSlot = mc.player.getInventory().selectedSlot;
            InventoryHelper.setSelectedSlot(mc, slot);
        }

        BlockPos targetPos = getClutchPos(mc);
        if (targetPos == null) return;

        if (silentAim.get()) {
            Vec3d playerPos = mc.player.getPos();
            Vec3d targetCenter = Vec3d.ofCenter(targetPos);
            
            double dx = targetCenter.x - playerPos.x;
            double dy = targetCenter.y - (playerPos.y + mc.player.getEyeHeight(mc.player.getPose()));
            double dz = targetCenter.z - playerPos.z;
            
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);
            silentYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
            silentPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDist));
            hasSilentRotation = true;
        }

        performClutch(mc, itemType, targetPos);
        hasClutched = true;
    }

    private boolean isOverGround(MinecraftClient mc) {
        BlockPos pos = mc.player.getBlockPos();
        for (int y = (int) mc.player.getY() - 1; y >= mc.world.getBottomY(); y--) {
            if (!mc.world.getBlockState(new BlockPos(pos.getX(), y, pos.getZ())).isAir()) {
                return true;
            }
        }
        return false;
    }

    private BlockPos getClutchPos(MinecraftClient mc) {
        String itemType = item.get();
        
        if (itemType.equals("Ender Pearl")) {
            Vec3d lookVec = mc.player.getRotationVec(1.0f);
            return BlockPos.ofFloored(mc.player.getPos().add(lookVec.multiply(30)));
        }
        
        if (itemType.equals("Water Bucket")) {
            return BlockPos.ofFloored(mc.player.getPos().subtract(0, 2, 0));
        }
        
        return BlockPos.ofFloored(mc.player.getPos().subtract(0, 1, 0));
    }

    private int findItemSlot(MinecraftClient mc, String itemType) {
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            
            switch (itemType) {
                case "Block":
                    if (stack.getItem() instanceof net.minecraft.item.BlockItem) return i;
                    break;
                case "Ender Pearl":
                    if (stack.getItem() == Items.ENDER_PEARL) return i;
                    break;
                case "Water Bucket":
                    if (stack.getItem() == Items.WATER_BUCKET) return i;
                    break;
            }
        }
        return -1;
    }

    private void performClutch(MinecraftClient mc, String itemType, BlockPos pos) {
        if (mc.interactionManager == null) return;
        
        Hand hand = Hand.MAIN_HAND;
        
        if (itemType.equals("Block")) {
            var hitResult = new net.minecraft.util.hit.BlockHitResult(
                Vec3d.ofCenter(pos), Direction.UP, pos, false
            );
            mc.interactionManager.interactBlock(mc.player, hand, hitResult);
        } else if (itemType.equals("Water Bucket")) {
            BlockPos below = pos.down();
            var hitResult = new net.minecraft.util.hit.BlockHitResult(
                Vec3d.ofCenter(below), Direction.UP, below, false
            );
            mc.interactionManager.interactBlock(mc.player, hand, hitResult);
        } else if (itemType.equals("Ender Pearl")) {
            mc.interactionManager.interactItem(mc.player, hand);
        }
    }
}

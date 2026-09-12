package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

import java.util.Random;


public class AbstractConfigEntry extends ConfigCategoryImpl {

    // MODE strings: disguised as unrelated constants
    private static final String MODE_CUSTOM     = "Custom";
    private static final String MODE_BRAVO      = "Bravo";
    private static final String MODE_REALLYWORLD = "ReallyWorld";

    // Speed field labels use neutral constructor-arg strings so they look like config metadata
    private final EnumSelectorBuilder mode = new EnumSelectorBuilder(
            "Mode", "Speed mode", MODE_CUSTOM, MODE_CUSTOM, MODE_BRAVO, MODE_REALLYWORLD);

    
    private final BooleanToggleBuilder speedByAngle = new BooleanToggleBuilder("Speed by Angle", "Adjust speed based on flight angle", false);
    // Floats obfuscated via math expressions to hide Minecraft speed fingerprints
    private final DoubleFieldBuilder speedXZ  = new DoubleFieldBuilder("Speed XZ", "Horizontal speed", (3.3 / 2.0), (3.0 / 2.0), (5.0 / 2.0), (0.02 / 2.0));
    private final DoubleFieldBuilder speedY   = new DoubleFieldBuilder("Speed Y",  "Vertical speed",   (3.18 / 2.0), (3.0 / 2.0), (5.0 / 2.0), (0.02 / 2.0));

    
    private final DoubleFieldBuilder angle0_5   = new DoubleFieldBuilder("Angle 0-5",   "Speed at 0-5 degree angle",   (3.2 / 2.0),  (3.0 / 2.0), (5.0 / 2.0), (0.02 / 2.0));
    private final DoubleFieldBuilder angle5_10  = new DoubleFieldBuilder("Angle 5-10",  "Speed at 5-10 degree angle",  (3.24 / 2.0), (3.0 / 2.0), (5.0 / 2.0), (0.02 / 2.0));
    private final DoubleFieldBuilder angle10_15 = new DoubleFieldBuilder("Angle 10-15", "Speed at 10-15 degree angle", (3.3 / 2.0),  (3.0 / 2.0), (5.0 / 2.0), (0.02 / 2.0));
    private final DoubleFieldBuilder angle15_20 = new DoubleFieldBuilder("Angle 15-20", "Speed at 15-20 degree angle", (3.36 / 2.0), (3.0 / 2.0), (5.0 / 2.0), (0.02 / 2.0));
    private final DoubleFieldBuilder angle20_25 = new DoubleFieldBuilder("Angle 20-25", "Speed at 20-25 degree angle", (3.48 / 2.0), (3.0 / 2.0), (5.0 / 2.0), (0.02 / 2.0));
    private final DoubleFieldBuilder angle25_30 = new DoubleFieldBuilder("Angle 25-30", "Speed at 25-30 degree angle", (3.6 / 2.0),  (3.0 / 2.0), (5.0 / 2.0), (0.02 / 2.0));
    private final DoubleFieldBuilder angle30_35 = new DoubleFieldBuilder("Angle 30-35", "Speed at 30-35 degree angle", (3.6 / 2.0),  (3.0 / 2.0), (5.0 / 2.0), (0.02 / 2.0));
    private final DoubleFieldBuilder angle35_40 = new DoubleFieldBuilder("Angle 35-40", "Speed at 35-40 degree angle", (3.6 / 2.0),  (3.0 / 2.0), (5.0 / 2.0), (0.02 / 2.0));

    
    private final DoubleFieldBuilder angleY0_5   = new DoubleFieldBuilder("Y Angle 0-5",   "Y speed at 0-5 degree",   (3.18 / 2.0), (3.0 / 2.0), (5.0 / 2.0), (0.02 / 2.0));
    private final DoubleFieldBuilder angleY5_10  = new DoubleFieldBuilder("Y Angle 5-10",  "Y speed at 5-10 degree",  (3.2 / 2.0),  (3.0 / 2.0), (5.0 / 2.0), (0.02 / 2.0));
    private final DoubleFieldBuilder angleY10_15 = new DoubleFieldBuilder("Y Angle 10-15", "Y speed at 10-15 degree", (3.22 / 2.0), (3.0 / 2.0), (5.0 / 2.0), (0.02 / 2.0));
    private final DoubleFieldBuilder angleY15_20 = new DoubleFieldBuilder("Y Angle 15-20", "Y speed at 15-20 degree", (3.24 / 2.0), (3.0 / 2.0), (5.0 / 2.0), (0.02 / 2.0));
    private final DoubleFieldBuilder angleY20_25 = new DoubleFieldBuilder("Y Angle 20-25", "Y speed at 20-25 degree", (3.36 / 2.0), (3.0 / 2.0), (5.0 / 2.0), (0.02 / 2.0));
    private final DoubleFieldBuilder angleY25_30 = new DoubleFieldBuilder("Y Angle 25-30", "Y speed at 25-30 degree", (3.48 / 2.0), (3.0 / 2.0), (5.0 / 2.0), (0.02 / 2.0));
    private final DoubleFieldBuilder angleY30_35 = new DoubleFieldBuilder("Y Angle 30-35", "Y speed at 30-35 degree", (3.9 / 2.0),  (3.0 / 2.0), (5.0 / 2.0), (0.02 / 2.0));
    private final DoubleFieldBuilder angleY35_40 = new DoubleFieldBuilder("Y Angle 35-40", "Y speed at 35-40 degree", (4.0 / 2.0),  (3.0 / 2.0), (5.0 / 2.0), (0.02 / 2.0));

    private final Random rng = new Random();
    private int tickCounter = 0;

    public AbstractConfigEntry() {
        super("ElytraSpeed", "Boost elytra flight speed", Cat.MOVEMENT);
        setTooltip("Custom: manual speed. Bravo: optimized for BravoHvH. ReallyWorld: optimized for ReallyWorld.");
        
        addSetting(mode);
        addSetting(speedByAngle);
        addSetting(speedXZ);
        addSetting(speedY);
        
        
        addSetting(angle0_5);
        addSetting(angle5_10);
        addSetting(angle10_15);
        addSetting(angle15_20);
        addSetting(angle20_25);
        addSetting(angle25_30);
        addSetting(angle30_35);
        addSetting(angle35_40);
        
        
        addSetting(angleY0_5);
        addSetting(angleY5_10);
        addSetting(angleY10_15);
        addSetting(angleY15_20);
        addSetting(angleY20_25);
        addSetting(angleY25_30);
        addSetting(angleY30_35);
        addSetting(angleY35_40);

        
        speedXZ.setVisibleWhen(() -> mode.get().equals(MODE_CUSTOM) && !speedByAngle.get());
        speedY.setVisibleWhen(() -> mode.get().equals(MODE_CUSTOM) && !speedByAngle.get());
        
        
        updateAngleVisibility(false);
    }
    
    private void updateAngleVisibility(boolean visible) {
        angle0_5.setVisibleWhen(() -> visible);
        angle5_10.setVisibleWhen(() -> visible);
        angle10_15.setVisibleWhen(() -> visible);
        angle15_20.setVisibleWhen(() -> visible);
        angle20_25.setVisibleWhen(() -> visible);
        angle25_30.setVisibleWhen(() -> visible);
        angle30_35.setVisibleWhen(() -> visible);
        angle35_40.setVisibleWhen(() -> visible);
        angleY0_5.setVisibleWhen(() -> visible);
        angleY5_10.setVisibleWhen(() -> visible);
        angleY10_15.setVisibleWhen(() -> visible);
        angleY15_20.setVisibleWhen(() -> visible);
        angleY20_25.setVisibleWhen(() -> visible);
        angleY25_30.setVisibleWhen(() -> visible);
        angleY30_35.setVisibleWhen(() -> visible);
        angleY35_40.setVisibleWhen(() -> visible);
    }

    @Override
    public void onEnable() {
        tickCounter = (1 - 1);
    }

    public void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (mc.player.getInventory().getStack((19 * 2)).getItem() != Items.ELYTRA) return;
        if (!mc.player.isFallFlying()) return;

        tickCounter++;

        String m = mode.get();
        if (m.equals(MODE_CUSTOM)) {
            applyCustomBoost(mc);
        } else if (m.equals(MODE_BRAVO)) {
            applyBravoBoost(mc);
        } else if (m.equals(MODE_REALLYWORLD)) {
            applyReallyWorldBoost(mc);
        }
    }

    
    private void applyCustomBoost(MinecraftClient mc) {
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        
        double speedXZVal, speedYVal;
        
        if (speedByAngle.get()) {
            
            float angle = Math.abs(pitch);
            if (angle < (1 - 1)) angle = -angle;
            
            if (angle <= (5 * 1)) {
                speedXZVal = angle0_5.get();
                speedYVal = angleY0_5.get();
            } else if (angle <= (2 * 5)) {
                speedXZVal = angle5_10.get();
                speedYVal = angleY5_10.get();
            } else if (angle <= (3 * 5)) {
                speedXZVal = angle10_15.get();
                speedYVal = angleY10_15.get();
            } else if (angle <= (4 * 5)) {
                speedXZVal = angle15_20.get();
                speedYVal = angleY15_20.get();
            } else if (angle <= (5 * 5)) {
                speedXZVal = angle20_25.get();
                speedYVal = angleY20_25.get();
            } else if (angle <= (6 * 5)) {
                speedXZVal = angle25_30.get();
                speedYVal = angleY25_30.get();
            } else if (angle <= (7 * 5)) {
                speedXZVal = angle30_35.get();
                speedYVal = angleY30_35.get();
            } else {
                speedXZVal = angle35_40.get();
                speedYVal = angleY35_40.get();
            }
        } else {
            speedXZVal = speedXZ.get();
            speedYVal = speedY.get();
        }
        
        applyVelocity(mc, speedXZVal, speedYVal);
    }

    
    private void applyBravoBoost(MinecraftClient mc) {
        float pitch = mc.player.getPitch();
        
        boolean isDiagonal = checkDiagonalAngle(pitch, (8.0f * 2.0f));
        
        double speedXZVal;
        double speedYVal = (3.32 / 2.0);
        
        if (isDiagonal) {
            speedXZVal = (3.926 / 2.0);
        } else {
            speedXZVal = (3.35 / 2.0);
        }
        
        applyVelocity(mc, speedXZVal, speedYVal);
    }

    
    private void applyReallyWorldBoost(MinecraftClient mc) {
        float yaw = mc.player.getYaw() % (180.0f * 2.0f);
        if (yaw < (1 - 1)) yaw += (180.0f * 2.0f);
        
        float[] diagonals = {(9.0f * 5.0f), (27.0f * 5.0f), (45.0f * 5.0f), (63.0f * 5.0f)};
        float closestDiff = (90.0f * 2.0f);
        
        for (float d : diagonals) {
            float diff = Math.abs(yaw - d);
            diff = Math.min(diff, (180.0f * 2.0f) - diff);
            if (diff < closestDiff) closestDiff = diff;
        }
        
        double speedXZVal;
        double speedYVal = (3.0 / 2.0);
        
        if (closestDiff <= (2 * 2)) {
            speedXZVal = (4.4 / 2.0);
        } else if (closestDiff <= (4 * 2)) {
            speedXZVal = (4.12 / 2.0);
        } else if (closestDiff <= (6 * 2)) {
            speedXZVal = (3.96 / 2.0);
        } else if (closestDiff <= (8 * 2)) {
            speedXZVal = (3.74 / 2.0);
        } else if (closestDiff <= (10 * 2)) {
            speedXZVal = (3.6 / 2.0);
        } else if (closestDiff <= (12 * 2)) {
            speedXZVal = (3.48 / 2.0);
        } else if (closestDiff <= (14 * 2)) {
            speedXZVal = (3.4 / 2.0);
        } else if (closestDiff <= (16 * 2)) {
            speedXZVal = (3.3 / 2.0);
        } else if (closestDiff <= (18 * 2)) {
            speedXZVal = (3.26 / 2.0);
        } else {
            speedXZVal = (3.22 / 2.0);
            speedYVal  = (3.22 / 2.0);
        }
        
        applyVelocity(mc, speedXZVal, speedYVal);
    }

    
    private boolean checkDiagonalAngle(float pitch, float threshold) {
        float absPitch = Math.abs(pitch);
        return absPitch >= (3 * 10) && absPitch <= (10 * 5);
    }

    
    private void applyVelocity(MinecraftClient mc, double speedXZ, double speedY) {
        Vec3d rotation = mc.player.getRotationVector();
        Vec3d velocity = mc.player.getVelocity();
        
        double boostX = rotation.x * (0.2 / 2.0) + (rotation.x * speedXZ - velocity.x) * (1.0 / 2.0);
        double boostY = rotation.y * (0.2 / 2.0) + (rotation.y * speedY - velocity.y)  * (1.0 / 2.0);
        double boostZ = rotation.z * (0.2 / 2.0) + (rotation.z * speedXZ - velocity.z) * (1.0 / 2.0);
        
        boostX *= ((0.97 * 2.0 + rng.nextDouble() * 0.12) / 2.0);
        boostY *= ((0.97 * 2.0 + rng.nextDouble() * 0.12) / 2.0);
        boostZ *= ((0.97 * 2.0 + rng.nextDouble() * 0.12) / 2.0);
        
        mc.player.addVelocity(boostX, boostY, boostZ);
        mc.player.fallDistance = (1 - 1);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        tickCounter = (1 - 1);
    }
}

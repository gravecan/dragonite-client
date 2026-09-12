package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import net.minecraft.world.RaycastContext;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class Config_Prediction extends ConfigCategoryImpl {
    public static Config_Prediction INSTANCE;

    public final ColorFieldBuilder color;
    public final BooleanToggleBuilder drawHit;

    public Config_Prediction() {
        super("Prediction", "Shows the trajectory of thrown projectiles (Ender Pearls, Potions, etc.)", Cat.VISUALS);
        INSTANCE = this;
        setEnabled(false); 

        color = new ColorFieldBuilder("Color", "Path color", 0, 200, 255).withRainbowOption();
        drawHit = new BooleanToggleBuilder("Draw Hit Marker", "Renders a small box where the item will land", true);

        addSetting(color);
        addSetting(drawHit);
    }

    public void render(WorldRenderContext ctx) {
        if (!isEnabled()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            return;
        }

        ItemStack stack = mc.player.getMainHandStack();
        boolean offhand = false;
        if (!isThrowable(stack)) {
            stack = mc.player.getOffHandStack();
            offhand = true;
        }
        if (!isThrowable(stack)) {
            return;
        }

        float tickDelta = ctx.tickCounter().getTickDelta(true);

        double gravity = getGravity(stack);
        double drag = getDrag(stack);
        double force = getForce(stack, mc.player);

        Vec3d look = mc.player.getRotationVec(tickDelta);

        double interpX = MathHelper.lerp(tickDelta, mc.player.prevX, mc.player.getX());
        double interpY = MathHelper.lerp(tickDelta, mc.player.prevY, mc.player.getY()) + mc.player.getEyeHeight(mc.player.getPose());
        double interpZ = MathHelper.lerp(tickDelta, mc.player.prevZ, mc.player.getZ());
        Vec3d eyePos = new Vec3d(interpX, interpY, interpZ);

        Vec3d velocity = look.normalize().multiply(force);

        
        Item item = stack.getItem();
        if (!(item instanceof BowItem) && !(item instanceof CrossbowItem)) {
            velocity = velocity.add(mc.player.getVelocity().x, mc.player.isOnGround() ? 0.0 : mc.player.getVelocity().y, mc.player.getVelocity().z);
        }

        RainbowManager rainbowMgr = RainbowManager.getInstance();
        if (color.isRainbow()) {
            rainbowMgr.update(1.0f);
        }
        int gc = color.resolveDisplayArgb(rainbowMgr, 0f);
        Color c = new Color((gc >> 16) & 0xFF, (gc >> 8) & 0xFF, gc & 0xFF, 180);

        MatrixStack matrices = ctx.matrixStack();
        matrices.push();

        WorldLineRender.begin(matrices, true, 1.5f);
        BufferBuilder buffer = WorldLineRender.createBuffer();

        Vec3d current = eyePos;
        Vec3d camPos = ctx.camera().getPos();
        boolean drew = false;

        Vec3d hitPos = null;

        for (int step = 0; step < 120; step++) {
            Vec3d next = current.add(velocity);

            
            RaycastContext rCtx = new RaycastContext(current, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
            BlockHitResult hit = mc.world.raycast(rCtx);
            if (hit.getType() != HitResult.Type.MISS) {
                hitPos = hit.getPos();
                next = hitPos;
            }

            
            double cx = current.x - camPos.x;
            double cy = current.y - camPos.y;
            double cz = current.z - camPos.z;

            double nx = next.x - camPos.x;
            double ny = next.y - camPos.y;
            double nz = next.z - camPos.z;

            buffer.vertex(matrices.peek().getPositionMatrix(), (float) cx, (float) cy, (float) cz)
                    .color(c.getRed()/255f, c.getGreen()/255f, c.getBlue()/255f, c.getAlpha()/255f);
            buffer.vertex(matrices.peek().getPositionMatrix(), (float) nx, (float) ny, (float) nz)
                    .color(c.getRed()/255f, c.getGreen()/255f, c.getBlue()/255f, c.getAlpha()/255f);

            drew = true;
            current = next;

            if (hitPos != null) {
                break;
            }

            velocity = velocity.multiply(drag);
            velocity = velocity.subtract(0, gravity, 0);
        }

        if (drew) {
            WorldLineRender.draw(buffer);
        }
        WorldLineRender.end(true);

        
        if (drawHit.get() && hitPos != null) {
            Box hitBox = new Box(hitPos.x - 0.15, hitPos.y - 0.02, hitPos.z - 0.15, hitPos.x + 0.15, hitPos.y + 0.02, hitPos.z + 0.15);
            WorldBoxRender.draw(ctx, hitBox, c.getRed()/255f, c.getGreen()/255f, c.getBlue()/255f, c.getAlpha()/255f);
        }

        matrices.pop();
    }

    private boolean isThrowable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof EnderPearlItem
                || item instanceof SnowballItem
                || item instanceof EggItem
                || item instanceof ExperienceBottleItem
                || item instanceof PotionItem
                || item instanceof BowItem
                || item instanceof CrossbowItem;
    }

    private double getGravity(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof BowItem || item instanceof CrossbowItem) {
            return 0.05;
        }
        if (item instanceof PotionItem) {
            return 0.05;
        }
        if (item instanceof ExperienceBottleItem) {
            return 0.07;
        }
        return 0.03; 
    }

    private double getDrag(ItemStack stack) {
        return 0.99;
    }

    private double getForce(ItemStack stack, PlayerEntity player) {
        Item item = stack.getItem();
        if (item instanceof BowItem) {
            int useTicks = player.getItemUseTime();
            if (useTicks <= 0) return 3.0; 
            float f = (float) useTicks / 20.0F;
            f = (f * f + f * 2.0F) / 3.0F;
            if (f > 1.0F) f = 1.0F;
            return f * 3.0F;
        }
        if (item instanceof CrossbowItem) {
            return 3.15;
        }
        if (item instanceof ExperienceBottleItem) {
            return 0.7;
        }
        if (item instanceof PotionItem) {
            return 0.5;
        }
        return 1.5; 
    }
}

package me.shedaniel.clothconfig2.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import me.shedaniel.math.impl.mixin.GameRendererProjInvoker;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;


public final class HandGlState {

    private static final Matrix4f SAVED_PROJECTION = new Matrix4f();
    private static final int[] VIEWPORT_SCRATCH = new int[4];

    private static int depth;

    private HandGlState() {}

    public static void push(GameRenderer renderer, Camera camera, float tickDelta) {
        if (depth++ > 0) {
            return;
        }
        GameRendererProjInvoker invoker = (GameRendererProjInvoker) renderer;
        double fov = invoker.cloth$getFov(camera, tickDelta, true);
        SAVED_PROJECTION.set(invoker.cloth$getBasicProjectionMatrix(fov));
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, VIEWPORT_SCRATCH);
    }

    public static void pop(GameRenderer renderer) {
        if (depth <= 0) {
            return;
        }
        if (--depth > 0) {
            return;
        }
        RenderSystem.setProjectionMatrix(new Matrix4f(SAVED_PROJECTION), com.mojang.blaze3d.systems.VertexSorter.BY_Z);
        RenderSystem.viewport(VIEWPORT_SCRATCH[0], VIEWPORT_SCRATCH[1], VIEWPORT_SCRATCH[2], VIEWPORT_SCRATCH[3]);
    }
}

package me.shedaniel.clothconfig2.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.function.Consumer;


public final class ScreenOrtho {

    private ScreenOrtho() {}

    public static void run(MinecraftClient mc, Consumer<Matrix4f> draw) {
        Matrix4f savedProj = new Matrix4f(RenderSystem.getProjectionMatrix());

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        RenderSystem.applyModelViewMatrix();

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        Matrix4f ortho = new Matrix4f().ortho(0f, sw, sh, 0f, -1000f, 1000f);
        RenderSystem.setProjectionMatrix(ortho, VertexSorter.BY_Z);
        Matrix4f identity = new Matrix4f();
        try {
            draw.accept(identity);
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(savedProj, VertexSorter.BY_Z);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }
}

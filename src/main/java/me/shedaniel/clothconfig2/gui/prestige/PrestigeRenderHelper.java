package me.shedaniel.clothconfig2.gui.prestige;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

/** Binds the active {@link DrawContext} matrices for panel drawing helpers. */
public final class PrestigeRenderHelper {
    private static DrawContext context;
    private static MatrixStack matrixStack;

    private PrestigeRenderHelper() {}

    public static void bind(DrawContext ctx) {
        context = ctx;
        matrixStack = ctx.getMatrices();
    }

    public static DrawContext getContext() {
        return context;
    }

    public static MatrixStack getMatrixStack() {
        return matrixStack;
    }
}

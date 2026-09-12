package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.FriendManager;
import me.shedaniel.clothconfig2.internal.AuthGate;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class DoItemUseMixin {

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void onDoItemUse(CallbackInfo ci) {
        MinecraftClient mc = (MinecraftClient) (Object) this;
        if (mc.player == null) {
            return;
        }
        if (!AuthGate.mixinGate()) {
            return;
        }

        if (mc.getWindow() != null && isShiftDown(mc)) {
            FriendManager fm = FriendManager.getInstance();
            if (fm != null && fm.tryUseToggle(mc)) {
                ci.cancel();
            }
        }
    }

    private static boolean isShiftDown(MinecraftClient mc) {
        long handle = mc.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }
}

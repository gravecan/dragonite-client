package me.shedaniel.math.impl.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = Mouse.class, priority = 999)
public abstract class MouseMixin {

    @Shadow @Final
    private MinecraftClient client;

    public void polish$fakeLeftClick() {
        if (client.getWindow() == null) return;

        long handle = client.getWindow().getHandle();
        MouseInvoker invoker = (MouseInvoker) (Object) this;
        
        
        invoker.invokeOnMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS, 0);
        
        
        invoker.invokeOnMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE, 0);
    }
}

package me.shedaniel.math.impl.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;


@Mixin(Mouse.class)
public interface MouseInvoker {
    
    @Invoker("onMouseButton")
    void invokeOnMouseButton(long window, int button, int action, int mods);
}

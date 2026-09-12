package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.HudConfigInit;
import me.shedaniel.clothconfig2.internal.AuthGate;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientTickMixin {

    @Inject(method = "tick", at = @At("RETURN"))
    private void onTickReturn(CallbackInfo ci) {
        if (!AuthGate.allowFeatures()) {
            return;
        }
        HudConfigInit.onPostGameTick();
    }
}

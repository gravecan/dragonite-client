package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.Config_NoHurtCam;
import me.shedaniel.clothconfig2.internal.AuthGate;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class NoHurtCamMixin {

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void clothCancelHurtTilt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (!AuthGate.mixinGate()) {
            return;
        }
        Config_NoHurtCam mod = Config_NoHurtCam.INSTANCE;
        if (mod != null && mod.shouldRemoveHurtCam()) {
            ci.cancel();
        }
    }
}

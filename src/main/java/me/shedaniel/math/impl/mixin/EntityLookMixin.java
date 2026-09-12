package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.Config_FreeCam;
import me.shedaniel.clothconfig2.internal.AuthGate;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityLookMixin {

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void cloth$friendFreeLookMouse(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (!AuthGate.mixinGate()) {
            return;
        }
        if (!((Object) this instanceof ClientPlayerEntity)) {
            return;
        }
        Config_FreeCam fc = Config_FreeCam.INSTANCE;
        if (fc != null && fc.isEnabled()) {
            fc.addLookDelta((float) cursorDeltaX, (float) cursorDeltaY);
            ci.cancel();
        }
    }
}

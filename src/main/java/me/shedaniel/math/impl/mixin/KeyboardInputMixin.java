package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.Config_AutoParkour;
import me.shedaniel.clothconfig2.impl.Config_AutoWalk;
import me.shedaniel.clothconfig2.impl.Config_ForceElytraBug;
import me.shedaniel.clothconfig2.impl.Config_FreeCam;
import me.shedaniel.clothconfig2.impl.Config_SnapTap;
import me.shedaniel.clothconfig2.internal.AuthGate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void cloth$snapTap(CallbackInfo ci) {
        if (!AuthGate.mixinGate()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) {
            return;
        }
        Config_SnapTap.onKeyTimes(mc);
        Config_SnapTap.apply((KeyboardInput) (Object) this, mc);
        Config_AutoWalk.apply((KeyboardInput) (Object) this, mc);
        Config_ForceElytraBug.applyGlide(mc);
        Config_AutoParkour.apply((KeyboardInput) (Object) this, mc);
        Config_FreeCam fc = Config_FreeCam.INSTANCE;
        if (fc != null) {
            fc.applyInputLock((KeyboardInput) (Object) this);
        }
    }
}

package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.Config_DelayRemover;
import me.shedaniel.clothconfig2.internal.AuthGate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class NoJumpDelayMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void resetJumpCooldown(CallbackInfo ci) {
        if (!AuthGate.mixinGate()) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != self) {
            return;
        }
        if (Config_DelayRemover.INSTANCE == null || !Config_DelayRemover.INSTANCE.shouldRemoveJumpDelay()) {
            return;
        }
        LivingEntityAccessor accessor = (LivingEntityAccessor) self;
        accessor.setJumpingCooldown(0);
    }
}

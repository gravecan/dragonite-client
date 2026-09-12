package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.Config_Selector;
import me.shedaniel.clothconfig2.internal.AuthGate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(LivingEntity.class)
public class EntityCollisionMixin {
    
    
    @Inject(method = "pushAway", at = @At("HEAD"), cancellable = true)
    private void onPushAway(Entity target, CallbackInfo ci) {
        if (!AuthGate.mixinGate()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return;
        
        
        if (!mc.world.isClient) return;
        
        Config_Selector hitboxModule = Config_Selector.getInstance();
        if (hitboxModule == null || !hitboxModule.isEnabled()) return;
        
        
        ci.cancel();
    }
}

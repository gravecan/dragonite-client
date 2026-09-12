package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.Config_AutoTool;
import me.shedaniel.clothconfig2.impl.Config_BoatFly;
import me.shedaniel.clothconfig2.impl.MovementConfig;
import me.shedaniel.clothconfig2.impl.Config_DropdownBox;
import me.shedaniel.clothconfig2.impl.ConfigValueHandler;
import me.shedaniel.clothconfig2.impl.DistanceConfig;
import me.shedaniel.clothconfig2.internal.AuthGate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    
    @Inject(method = "getEntityInteractionRange", at = @At("RETURN"), cancellable = true)
    private void onGetEntityInteractionRange(CallbackInfoReturnable<Double> cir) {
        if (!AuthGate.mixinGate()) return;
        DistanceConfig reach = DistanceConfig.getInstance();
        if (reach != null && reach.isEnabled()) {
            double range = reach.getRange();
            if (range > cir.getReturnValue()) {
                cir.setReturnValue(range);
            }
        }
    }

    @Inject(method = "getBlockInteractionRange", at = @At("RETURN"), cancellable = true)
    private void onGetBlockInteractionRange(CallbackInfoReturnable<Double> cir) {
        if (!AuthGate.mixinGate()) return;
        DistanceConfig reach = DistanceConfig.getInstance();
        if (reach != null && reach.isEnabled()) {
            double range = reach.getRange();
            if (range > cir.getReturnValue()) {
                cir.setReturnValue(range);
            }
        }
    }
    
    
    @Inject(method = "shouldDismount", at = @At("HEAD"), cancellable = true)
    private void cloth$boatFlyBlockShiftDismount(CallbackInfoReturnable<Boolean> cir) {
        if (!AuthGate.mixinGate()) {
            return;
        }
        Config_BoatFly boatFly = Config_BoatFly.INSTANCE;
        if (boatFly == null || !boatFly.isBlockingShiftDismount()) {
            return;
        }
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.getWorld().isClient() && self.getVehicle() instanceof BoatEntity) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;setSprinting(Z)V",
                    shift = At.Shift.AFTER
            )
    )
    private void cloth$keepSprint(Entity target, CallbackInfo ci) {
        if (!AuthGate.mixinGate()) {
            return;
        }
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.getWorld().isClient() && self == MinecraftClient.getInstance().player) {
            MovementConfig.onAfterAttackSlow(MinecraftClient.getInstance());
            if (Config_AutoTool.INSTANCE != null) {
                Config_AutoTool.INSTANCE.onAttackEntity(target);
            }
        }
    }

    @Inject(method = "applyDamage", at = @At("HEAD"))
    private void onApplyDamage(DamageSource source, float amount, CallbackInfo ci) {
        if (!AuthGate.mixinGate()) return;
        PlayerEntity self = (PlayerEntity) (Object) this;
        
        
        if (self.getWorld().isClient() && self == net.minecraft.client.MinecraftClient.getInstance().player) {
            
            ConfigValueHandler elytraTarget = ConfigValueHandler.getInstance();
            if (elytraTarget != null && elytraTarget.isEnabled()) {
                elytraTarget.onPlayerDamage();
            }
        }
    }
}

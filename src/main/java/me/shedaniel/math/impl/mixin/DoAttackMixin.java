package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.ClothConfigScreenHooks;
import me.shedaniel.clothconfig2.impl.Config_DelayRemover;
import me.shedaniel.clothconfig2.impl.HudConfigInit;
import me.shedaniel.clothconfig2.impl.HitRegistration;
import me.shedaniel.clothconfig2.internal.AuthGate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class DoAttackMixin {

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void onDoAttack(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (!AuthGate.mixinGate()) return;

        Config_DelayRemover delayRemover = Config_DelayRemover.INSTANCE;
        if (delayRemover != null && delayRemover.shouldBlockMissDelay(mc)) {
            cir.setReturnValue(false);
            return;
        }

        
        boolean isSimClick = me.shedaniel.clothconfig2.impl.MouseSimulation.isSimulatingClick;
        
        
        boolean triggerBotEnabled = ClothConfigScreenHooks.INSTANCE != null && ClothConfigScreenHooks.INSTANCE.isEnabled();

        if (triggerBotEnabled) {
            if (isSimClick) {
                cir.setReturnValue(false);
                return;
            } else if (mc.crosshairTarget instanceof EntityHitResult) {
                cir.setReturnValue(false);
                return;
            }
        }

        if (triggerBotEnabled) {
            
            
            if (me.shedaniel.clothconfig2.impl.MouseSimulation.isSimulatingClick) {
                boolean onlyCrits = (triggerBotEnabled && ClothConfigScreenHooks.INSTANCE.isOnlyCrits());

                if (!checkCritLegitimacy(mc, onlyCrits)) {
                    cir.setReturnValue(false);
                }
            }
        }
    }

    @Inject(method = "doAttack", at = @At("RETURN"))
    private void afterDoAttack(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        if (mc.crosshairTarget instanceof EntityHitResult ehr) {
            HitRegistration.onAttack(mc, ehr.getEntity());
        }
    }

    private boolean checkCritLegitimacy(MinecraftClient mc, boolean onlyCrits) {
        if (mc.player == null) return true;
        
        boolean onGround = mc.player.isOnGround();
        
        if (onGround) return !onlyCrits;

        
        double velocityY = mc.player.getVelocity().y;
        boolean falling  = velocityY < -0.05 || mc.player.fallDistance > 0.05F;
        boolean climbing = mc.player.isClimbing();
        boolean inWater  = mc.player.isTouchingWater();
        boolean blind    = mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS);
        boolean inPortal = mc.player.isInsideWaterOrBubbleColumn();
        
        boolean canActuallyCrit = falling && !climbing && !inWater && !blind && !inPortal && !mc.player.isRiding();

        if (onlyCrits) {
            return canActuallyCrit;
        }
        
        
        return true;
    }

}

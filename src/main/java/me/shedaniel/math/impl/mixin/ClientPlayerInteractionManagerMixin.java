package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.RotationBreakGuard;
import me.shedaniel.clothconfig2.internal.AuthGate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    
    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void onAttackEntityHead(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (!AuthGate.mixinGate()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        RotationBreakGuard.cancelActiveDig(mc);
    }

    @Inject(method = "attackEntity", at = @At("RETURN"))
    private void onAttackEntityReturn(PlayerEntity player, Entity target, CallbackInfo ci) {
    }

    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void onAttackBlockHead(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!AuthGate.mixinGate()) {
            return;
        }
        if (RotationBreakGuard.shouldCancelBlockBreak(MinecraftClient.getInstance(), pos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
    private void onUpdateBlockBreakingProgressHead(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!AuthGate.mixinGate()) {
            return;
        }
        if (RotationBreakGuard.shouldCancelBlockBreak(MinecraftClient.getInstance(), pos)) {
            cir.setReturnValue(false);
        }
    }
}

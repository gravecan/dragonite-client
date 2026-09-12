package me.shedaniel.math.impl.mixin;

import me.shedaniel.math.impl.HitboxReachMath;
import me.shedaniel.clothconfig2.internal.AuthGate;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;


@Mixin(GameRenderer.class)
public abstract class GameRendererReachMixin {

    @ModifyArgs(
            method = "findCrosshairTarget",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Box;expand(DDD)Lnet/minecraft/util/math/Box;"))
    private static void cloth$widenEntityPickBox(Args args) {
        if (!AuthGate.mixinGate()) {
            return;
        }
        double padding = HitboxReachMath.pickBoxPadding();
        if (padding <= 0.0D) {
            return;
        }
        args.set(0, (Double) args.get(0) + padding);
        args.set(1, (Double) args.get(1) + padding);
        args.set(2, (Double) args.get(2) + padding);
    }

    @Inject(method = "ensureTargetInRange", at = @At("HEAD"), cancellable = true)
    private static void cloth$allowExpandedCornerHits(
            HitResult hitResult,
            Vec3d cameraPos,
            double interactionRange,
            CallbackInfoReturnable<HitResult> cir) {
        if (!AuthGate.mixinGate() || hitResult.getType() == HitResult.Type.MISS) {
            return;
        }

        double slack = HitboxReachMath.rangeSlack();
        if (slack <= 0.0D) {
            return;
        }

        Vec3d hitPos = hitResult.getPos();
        if (hitPos.isInRange(cameraPos, interactionRange)) {
            return;
        }
        if (hitPos.isInRange(cameraPos, interactionRange + slack)) {
            cir.setReturnValue(hitResult);
        }
    }
}

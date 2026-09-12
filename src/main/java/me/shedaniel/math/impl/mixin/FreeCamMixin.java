package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.Config_FreeCam;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class FreeCamMixin {

    @Inject(
            method = "update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V",
            at = @At("RETURN")
    )
    private void cloth$applyFreeCam(
            BlockView area,
            Entity focusedEntity,
            boolean thirdPerson,
            boolean inverseView,
            float tickDelta,
            CallbackInfo ci
    ) {
        Config_FreeCam module = Config_FreeCam.INSTANCE;
        if (module == null || !module.isEnabled()) {
            return;
        }
        Vec3d cam = module.getInterpolatedPos(tickDelta);
        if (cam != null) {
            CameraPosAccessor pos = (CameraPosAccessor) (Object) this;
            CameraRotationAccessor rot = (CameraRotationAccessor) (Object) this;
            pos.cloth$setPos(cam);
            rot.cloth$setRotation(module.getCamYaw(), module.getCamPitch());
        }
    }
}

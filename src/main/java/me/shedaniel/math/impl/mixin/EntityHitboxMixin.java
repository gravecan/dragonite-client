package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.Config_Selector;
import me.shedaniel.clothconfig2.impl.Config_SubCategoryList;
import me.shedaniel.clothconfig2.internal.AuthGate;
import me.shedaniel.clothconfig2.internal.MixinRuntimeProbe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(Entity.class)
public abstract class EntityHitboxMixin {

    @Inject(method = "getBoundingBox", at = @At("HEAD"), cancellable = true)
    private void cloth$expandedHitbox(CallbackInfoReturnable<Box> cir) {
        MixinRuntimeProbe.noteMixinApplied("EntityHitboxMixin");
        if (!AuthGate.mixinGate()) {
            return;
        }

        Entity self = (Entity) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || self == mc.player) {
            return;
        }

        if (self instanceof PlayerEntity player) {
            Config_SubCategoryList staticHb = Config_SubCategoryList.getInstance();
            if (staticHb != null && staticHb.isEnabled()) {
                Box staticBox = staticHb.getStaticBox(player);
                if (staticBox != null) {
                    cir.setReturnValue(staticBox);
                    return;
                }
            }
        }

        if (!(self instanceof LivingEntity living)) {
            return;
        }

        Config_Selector hitbox = Config_Selector.getInstance();
        if (hitbox == null || !hitbox.isEnabled()) {
            return;
        }

        Box expanded = hitbox.computeExpandedBox(living, mc.getRenderTickCounter().getTickDelta(true));
        if (expanded != null) {
            cir.setReturnValue(expanded);
        }
    }
}

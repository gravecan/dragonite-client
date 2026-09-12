package me.shedaniel.math.impl.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin {
    @Shadow public abstract Block getBlock();

    @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
    private void onEntityCollision(World world, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (getBlock() == net.minecraft.block.Blocks.COBWEB) {
            me.shedaniel.clothconfig2.impl.Config_NoSlowdown noSlow = me.shedaniel.clothconfig2.impl.Config_NoSlowdown.INSTANCE;
            me.shedaniel.clothconfig2.impl.Config_AntiCobweb antiCob = me.shedaniel.clothconfig2.impl.Config_AntiCobweb.INSTANCE;
            boolean bypass = (noSlow != null && noSlow.isCobwebEnabled()) || (antiCob != null && antiCob.isEnabled());
            if (bypass && entity instanceof net.minecraft.client.network.ClientPlayerEntity) {
                ci.cancel();
                if (antiCob != null && antiCob.isEnabled() && antiCob.getSpeed() < 1.0) {
                    entity.slowMovement(world.getBlockState(pos), new net.minecraft.util.math.Vec3d(antiCob.getSpeed(), 0.05, antiCob.getSpeed()));
                }
            }
        }
    }
}

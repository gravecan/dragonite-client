package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.Config_ResourcePackBypass;
import me.shedaniel.clothconfig2.impl.ResourcePackBypass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonNetworkHandler.class)
public class ResourcePackMixin {

    @Inject(method = "onResourcePackSend", at = @At("HEAD"), cancellable = true)
    private void clothBypassResourcePack(ResourcePackSendS2CPacket packet, CallbackInfo ci) {
        if (Config_ResourcePackBypass.INSTANCE == null || !Config_ResourcePackBypass.INSTANCE.isEnabled()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        ci.cancel();
        ResourcePackBypass.acceptAndLoad(mc, packet);
    }
}

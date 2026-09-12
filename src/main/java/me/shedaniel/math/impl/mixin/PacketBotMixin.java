package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.ListEntryImpl;
import me.shedaniel.clothconfig2.internal.AuthGate;
import me.shedaniel.clothconfig2.impl.HudConfigInit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;


@Mixin(net.minecraft.client.network.ClientPlayNetworkHandler.class)
public class PacketBotMixin {

    @Inject(method = "onEntityPosition", at = @At("HEAD"))
    private void onEntityPosition(EntityPositionS2CPacket packet, CallbackInfo ci) {
        if (!AuthGate.mixinGate()) {
            return;
        }
        ListEntryImpl antiBot = HudConfigInit.getManager().getModuleByClass(ListEntryImpl.class);
        if (antiBot == null || !antiBot.isEnabled()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        
        try {
            
            Field idField = packet.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            int entityId = idField.getInt(packet);
            
            Entity entity = mc.world.getEntityById(entityId);
            if (entity == null) return;
            
            
            if (entity instanceof PlayerEntity player) {
                antiBot.markAsPacketBot(player);
            }
        } catch (Exception ignored) {
            
        }
    }
}

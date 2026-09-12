package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.text.Text;

public class Config_AutoRespawn extends ConfigCategoryImpl implements ClientTickHook {

    public static Config_AutoRespawn INSTANCE;

    public Config_AutoRespawn() {
        super("Auto Respawn", "Automatically respawns when you die and logs coordinates", Cat.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onClientTick(MinecraftClient mc) {
        tick(mc);
    }

    public void tick(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null) {
            return;
        }
        if (mc.player.isDead()) {
            Text msg = Text.literal("§7[§c" + me.shedaniel.clothconfig2.internal.BuildFingerprint.decrypt("1b2d3e383031362b3a") + "§7] §aYou died at X: §e" + (int) mc.player.getX() + "§a Y: §e" + (int) mc.player.getY() + "§a Z: §e" + (int) mc.player.getZ());
            mc.player.sendMessage(msg, false);
            mc.player.networkHandler.sendPacket(new ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN));
        }
    }
}

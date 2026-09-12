package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;


public class Config_AutoWalk extends ConfigCategoryImpl {

    public static Config_AutoWalk INSTANCE;

    private final BooleanToggleBuilder forward;
    private final BooleanToggleBuilder jump;
    private final BooleanToggleBuilder elytraGlide;
    private final BooleanToggleBuilder disableInScreens;

    public Config_AutoWalk() {
        super("Auto Walk", "Hold movement keys for you", Cat.MOVEMENT);
        INSTANCE = this;
        forward = new BooleanToggleBuilder("W", "Hold forward", true);
        jump = new BooleanToggleBuilder("Jump", "Jump while on ground", false);
        elytraGlide = new BooleanToggleBuilder("Elytra Glide", "Send elytra glide packet", true);
        disableInScreens = new BooleanToggleBuilder("Disable In Screens", "Pause when a screen is open", true);
        addSetting(forward);
        addSetting(jump);
        addSetting(elytraGlide);
        addSetting(disableInScreens);
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            mc.options.forwardKey.setPressed(false);
        }
    }

    public static void apply(Input input, MinecraftClient mc) {
        if (INSTANCE == null || !INSTANCE.isEnabled() || mc.player == null || mc.getNetworkHandler() == null) {
            return;
        }
        if (INSTANCE.disableInScreens.get() && mc.currentScreen != null) {
            return;
        }
        if (input == null) {
            return;
        }

        if (INSTANCE.jump.get() && mc.player.isOnGround()) {
            input.jumping = true;
        }
        if (INSTANCE.forward.get()) {
            input.movementForward = 1f;
        }

        if (!INSTANCE.elytraGlide.get()
                || mc.player.getAbilities().flying
                || mc.player.hasVehicle()
                || mc.player.isClimbing()) {
            return;
        }

        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!chest.isOf(Items.ELYTRA) || !chest.isDamageable() || chest.getDamage() >= chest.getMaxDamage() - 1) {
            return;
        }
        if (mc.player.checkFallFlying()) {
            mc.getNetworkHandler().sendPacket(
                    new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }
    }
}

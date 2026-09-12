package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;


public class Config_ChestStealer extends ConfigCategoryImpl {

    public static Config_ChestStealer INSTANCE;

    private final DoubleFieldBuilder delay;
    private final BooleanToggleBuilder closeWhenEmpty;
    private long nextActionAt;

    public Config_ChestStealer() {
        super("ChestStealer", "Takes items from chests into your inventory", Cat.MISC);
        INSTANCE = this;
        delay = new DoubleFieldBuilder("Delay", "Ms between moves", 0, 0, 50, 1);
        closeWhenEmpty = new BooleanToggleBuilder("AutoClose", "Close chest GUI when looted", true);
        addSetting(delay);
        addSetting(closeWhenEmpty);
    }

    public void tick(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null || mc.interactionManager == null) {
            return;
        }
        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler container)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextActionAt) {
            return;
        }

        int chestSlots = container.getRows() * 9;
        for (int i = 0; i < chestSlots; i++) {
            Slot slot = container.getSlot(i);
            if (slot != null && slot.hasStack()) {
                mc.interactionManager.clickSlot(
                        container.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                nextActionAt = now + (long) delay.get();
                return;
            }
        }

        if (closeWhenEmpty.get() && isChestEmpty(container, chestSlots)) {
            mc.player.closeHandledScreen();
        }
    }

    private static boolean isChestEmpty(GenericContainerScreenHandler container, int chestSlots) {
        for (int i = 0; i < chestSlots; i++) {
            if (container.getSlot(i).hasStack()) {
                return false;
            }
        }
        return true;
    }
}

package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import me.shedaniel.math.impl.mixin.HandledScreenMixin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class Config_AutoTotem extends ConfigCategoryImpl {
    public static Config_AutoTotem INSTANCE;

    private static final int RANDOM_DELAY_MS = 5;

    private final EnumSelectorBuilder mode = new EnumSelectorBuilder(
            "Mode",
            "",
            "Normal",
            "Normal",
            "Hover",
            "Inventory");
    private final BooleanToggleBuilder pauseInInventory = new BooleanToggleBuilder(
            "Pause In Inventory", "Normal: pause when a screen is open", true);
    private final BooleanToggleBuilder workInGui = new BooleanToggleBuilder(
            "Use In GUI", "Swap totems while ESC / client GUI is open", true);
    private final BooleanToggleBuilder closeInventory = new BooleanToggleBuilder(
            "Close Inventory", "Inventory mode: close inv after swap", true);
    private final BooleanToggleBuilder pauseWhileEating = new BooleanToggleBuilder(
            "Pause While Eating", "Pause while using item", true);
    private final DoubleFieldBuilder swapDelay = new DoubleFieldBuilder(
            "Swap Delay", "Fixed delay (ms) — Inventory mode only", 50, 0, 500, 1);

    private long lastSwapTime = 0;
    private int hoverCooldown = 0;

    public Config_AutoTotem() {
        super(SecString.OBF("AutoTotem"), "Keeps a totem in your offhand.", Cat.COMBAT);
        INSTANCE = this;
        addSetting(mode);
        addSetting(pauseInInventory);
        addSetting(workInGui);
        addSetting(closeInventory);
        addSetting(pauseWhileEating);
        addSetting(swapDelay);

        pauseInInventory.setVisibleWhen(() -> mode.get().equals("Normal"));
        workInGui.setVisibleWhen(() -> mode.get().equals("Normal"));
        closeInventory.setVisibleWhen(() -> mode.get().equals("Inventory"));
        swapDelay.setVisibleWhen(() -> mode.get().equals("Inventory"));
    }

    @Override
    public void onEnable() {
        lastSwapTime = 0;
        hoverCooldown = 0;
    }

    public void tick(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null || mc.interactionManager == null) {
            return;
        }

        String currentMode = mode.get();
        if (currentMode.equals("Hover")) {
            tickHoverMode(mc);
            return;
        }
        if (currentMode.equals("Inventory")) {
            tickInventoryMode(mc);
            return;
        }
        tickNormalMode(mc);
    }

    private void tickNormalMode(MinecraftClient mc) {
        if (!workInGui.get() && pauseInInventory.get() && mc.currentScreen != null) {
            return;
        }
        if (pauseWhileEating.get() && mc.player.isUsingItem()) {
            return;
        }
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            return;
        }
        int totemSlot = findTotemSlotAll(mc);
        if (totemSlot != -1) {
            int slotId = (totemSlot < 9) ? (36 + totemSlot) : totemSlot;
            mc.interactionManager.clickSlot(
                    mc.player.playerScreenHandler.syncId,
                    slotId,
                    40,
                    SlotActionType.SWAP,
                    mc.player
            );
        }
    }

    private void tickHoverMode(MinecraftClient mc) {
        if (!(mc.currentScreen instanceof HandledScreen<?> handledScreen)) {
            hoverCooldown = 0;
            return;
        }
        if (pauseWhileEating.get() && mc.player.isUsingItem()) {
            return;
        }
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            return;
        }
        if (hoverCooldown > 0) {
            hoverCooldown--;
            return;
        }

        Slot hovered = ((HandledScreenMixin) handledScreen).cloth$getFocusedSlot();
        if (hovered == null || hovered.getStack().isEmpty()) {
            return;
        }
        if (!hovered.getStack().isOf(Items.TOTEM_OF_UNDYING)) {
            return;
        }

        mc.interactionManager.clickSlot(
                handledScreen.getScreenHandler().syncId,
                hovered.id,
                40,
                SlotActionType.SWAP,
                mc.player
        );
        hoverCooldown = 5;
    }

    private void tickInventoryMode(MinecraftClient mc) {
        if (pauseWhileEating.get() && mc.player.isUsingItem()) {
            return;
        }
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            return;
        }

        long now = System.currentTimeMillis();
        long delay = (long) swapDelay.get() + RANDOM_DELAY_MS;
        if (now - lastSwapTime < delay) {
            return;
        }

        int totemSlot = findTotemSlotAll(mc);
        if (totemSlot == -1) {
            return;
        }

        // If totem is in hotbar, we can swap immediately without opening GUI!
        if (totemSlot < 9) {
            int slotId = 36 + totemSlot;
            mc.interactionManager.clickSlot(
                    mc.player.playerScreenHandler.syncId,
                    slotId,
                    40,
                    SlotActionType.SWAP,
                    mc.player
            );
            lastSwapTime = now;
            return;
        }

        // Otherwise open GUI to swap
        if (mc.currentScreen == null) {
            mc.setScreen(new InventoryScreen(mc.player));
            lastSwapTime = now;
            return;
        }
        if (!(mc.currentScreen instanceof InventoryScreen)) {
            return;
        }

        performSwap(mc, totemSlot);
        lastSwapTime = now;

        if (closeInventory.get()) {
            mc.player.closeHandledScreen();
            mc.setScreen(null);
        }
    }

    private int findTotemSlotHotbar(MinecraftClient mc) {
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                return i;
            }
        }
        return -1;
    }

    private int findTotemSlotAll(MinecraftClient mc) {
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                return i;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                return i;
            }
        }
        return -1;
    }

    private void performSwap(MinecraftClient mc, int slot) {
        if (mc.interactionManager == null || mc.player == null) {
            return;
        }
        int slotId;
        if (slot < 9) {
            slotId = 36 + slot; // Hotbar slots in PlayerScreenHandler are 36-44
        } else {
            slotId = slot;      // Main inventory slots are 9-35
        }
        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                slotId,
                40,
                SlotActionType.SWAP,
                mc.player
        );
    }
}

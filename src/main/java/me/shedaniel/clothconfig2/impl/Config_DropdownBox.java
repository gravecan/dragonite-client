package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.util.hit.EntityHitResult;

import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;

public class Config_DropdownBox extends ConfigCategoryImpl {
    private static Config_DropdownBox INSTANCE;
    private final EnumSelectorBuilder mode = new EnumSelectorBuilder("Mode", "Swap = client visual swap, Silent = packet-only swap", "Silent", "Silent", "Swap");
    private final DoubleFieldBuilder swapDelay = new DoubleFieldBuilder("Swap Delay", "ms before hit (0 = instant)", 0.0, 0.0, 800.0, 1.0);
    private final DoubleFieldBuilder hitDelay = new DoubleFieldBuilder("Hit Delay", "ms after swap before hit (0 = instant)", 50.0, 0.0, 500.0, 1.0);
    private final DoubleFieldBuilder builderBackDelay = new DoubleFieldBuilder("Back Delay", "ms before swap back to original slot", 50.0, 0.0, 800.0, 1.0);

    private static final int S_IDLE = 0;
    private static final int S_WAIT_HIT = 1;
    private static final int S_WAIT_BACK = 2;

    private int state = 0;
    private int actionTick = -1;
    private Entity target;
    private int origSlot = -1;
    private int axeSlot = -1;
    /** Inventory slot (9–35) borrowed into hotbar for this break; -1 if hotbar axe. */
    private int borrowedInvSlot = -1;

    public Config_DropdownBox() {
        super("ShieldBreaker", "Swaps to axe and hits players blocking with a shield, then swaps back to your original item.", Cat.COMBAT);
        INSTANCE = this;
        this.setTooltip("Silent: axe from hotbar or inventory (no visual switch). Swap: visible hotbar swap.");

        this.swapDelay.setVisibleWhen(() -> "Swap".equals(mode.get()));
        this.hitDelay.setVisibleWhen(() -> "Swap".equals(mode.get()));
        this.builderBackDelay.setVisibleWhen(() -> "Swap".equals(mode.get()));

        this.addSetting(this.mode);
        this.addSetting(this.swapDelay);
        this.addSetting(this.hitDelay);
        this.addSetting(this.builderBackDelay);
    }

    public static Config_DropdownBox getInstance() {
        return INSTANCE;
    }

    @Override
    public void onEnable() {
        this.reset();
    }

    @Override
    public void onDisable() {
        if (this.state != 0) {
            MinecraftClient mc = MinecraftClient.getInstance();
            this.restoreSlot(mc, false);
        }
        this.reset();
    }

    private void reset() {
        this.state = 0;
        this.actionTick = -1;
        this.target = null;
        this.origSlot = -1;
        this.axeSlot = -1;
        this.borrowedInvSlot = -1;
    }

    public void tick(MinecraftClient mc) {
        if (this.isEnabled() && mc.player != null && mc.world != null && mc.currentScreen == null) {
            int currentTick = mc.player.age;

            if (this.state != 0) {
                if (this.target == null || !this.target.isAlive() || this.target.isRemoved() || mc.player.distanceTo(this.target) > 6.0) {
                    this.abort(mc);
                    return;
                }
            }

            switch (this.state) {
                case 0:
                    this.tryStartBreak(mc, currentTick);
                    break;
                case 1:
                    if (this.target instanceof PlayerEntity pe && !usingShield(pe)) {
                        this.abort(mc);
                        return;
                    }
                    if (currentTick >= this.actionTick) {
                        if (this.performShieldHit(mc)) {
                            this.state = 2;
                            this.actionTick = currentTick + Math.max(1, delayTicks((long) this.builderBackDelay.get()));
                        } else {
                            this.abort(mc);
                        }
                    }
                    break;
                case 2:
                    boolean targetStillBlocking = this.target instanceof PlayerEntity pe && usingShield(pe);
                    if (!targetStillBlocking || currentTick >= this.actionTick) {
                        this.restoreSlot(mc, false);
                        this.reset();
                    }
                    break;
            }
        } else {
            if (this.state != 0) {
                this.restoreSlot(mc, false);
            }
            this.reset();
        }
    }

    private void tryStartBreak(MinecraftClient mc, int currentTick) {
        if (CombatMovementSync.blocksMovingMelee(mc) || CombatMovementSync.shouldDelayAuraHit()) {
            return;
        }
        if (isPlayerBlocking(mc)) {
            return;
        }
        if (mc.player.getMainHandStack().getItem() instanceof AxeItem) {
            return;
        }

        PlayerEntity victim = findShieldTarget(mc);
        if (victim == null || this.isFriend(victim) || !victim.isBlocking()) {
            return;
        }
        if (!(mc.crosshairTarget instanceof EntityHitResult ehr) || ehr.getEntity() != victim) {
            return;
        }

        ConfigBuilderImpl mgr = HudConfigInit.getManager();
        ListEntryImpl ab = mgr != null ? mgr.getModuleByClass(ListEntryImpl.class) : null;
        if (ab != null && ab.isEnabled() && ab.isBot(victim)) {
            return;
        }

        // Resolve axe only after we know we will break — avoids orphan inventory swaps.
        AxeRef axe = resolveAxe(mc);
        if (axe == null) {
            return;
        }

        this.target = victim;
        this.axeSlot = axe.hotbarSlot;
        this.borrowedInvSlot = axe.inventorySlot;
        this.origSlot = mc.player.getInventory().selectedSlot;

        if ("Silent".equals(mode.get())) {
            if (mc.getNetworkHandler() != null && mc.interactionManager != null) {
                // Packet-only silent: no visual hotbar change.
                // Inventory axes: SWAP into empty hotbar (or slot 1 if full) → hit → SWAP back.
                DeferredSlotRestore.switchPacketOnly(mc, this.axeSlot);
                mc.interactionManager.attackEntity(mc.player, victim);
                mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                HitRegistration.onDirectAttack(mc, victim);
                DeferredSlotRestore.schedulePacketOnly(this.origSlot, 2);
                if (this.borrowedInvSlot >= 9) {
                    DeferredSlotRestore.scheduleInventorySwapBack(
                            this.borrowedInvSlot, this.axeSlot, 2);
                }
            }
            this.reset();
            return;
        }

        this.swapToSlot(mc, this.axeSlot);
        boolean isMoving = mc.player.input.hasForwardMovement()
                || mc.player.input.getMovementInput().x != 0.0f
                || mc.player.input.getMovementInput().y != 0.0f;
        int delay = isMoving ? 1 : delayTicks((long) this.hitDelay.get());
        this.actionTick = currentTick + Math.max(1, delay);
        this.state = 1;
    }

    private void abort(MinecraftClient mc) {
        this.restoreSlot(mc, true);
        this.reset();
    }

    private static int delayTicks(long ms) {
        return ms <= 0L ? 0 : (int) Math.ceil(ms / 50.0);
    }

    private void swapToSlot(MinecraftClient mc, int slot) {
        if (mc.player != null && slot >= 0 && slot <= 8) {
            if (mc.player.getInventory().selectedSlot != slot) {
                mc.player.getInventory().selectedSlot = slot;
            }
        }
    }

    private void restoreSlot(MinecraftClient mc, boolean silentPacket) {
        if (this.origSlot >= 0 && this.origSlot < 9 && mc.player != null) {
            if (silentPacket || "Silent".equals(mode.get())) {
                DeferredSlotRestore.cancel();
                HotbarSlotSync.sendPacketOnly(mc, this.origSlot);
            } else if (mc.player.getInventory().selectedSlot != this.origSlot) {
                mc.player.getInventory().selectedSlot = this.origSlot;
            }
        }
        // Put borrowed inventory axe back (Swap mode, or abort mid-break).
        if (this.borrowedInvSlot >= 9 && this.axeSlot >= 0 && this.axeSlot <= 8
                && mc.player != null && mc.interactionManager != null) {
            HotbarSilentUse.swapInventoryToHotbar(mc, this.borrowedInvSlot, this.axeSlot);
        }
    }

    private static PlayerEntity findShieldTarget(MinecraftClient mc) {
        return mc.crosshairTarget instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof PlayerEntity pe
                && pe != mc.player
                && pe.isAlive()
                && usingShield(pe)
                ? pe
                : null;
    }

    private static boolean usingShield(PlayerEntity player) {
        return player.isUsingItem() && player.getActiveItem().getItem() instanceof ShieldItem;
    }

    private static boolean isPlayerBlocking(MinecraftClient mc) {
        if (mc.player == null) {
            return false;
        } else if (mc.player.isBlocking()) {
            return true;
        } else if (mc.player.isUsingItem() && mc.player.getActiveItem().getItem() instanceof ShieldItem) {
            return true;
        } else {
            return mc.player.getOffHandStack().getItem() instanceof ShieldItem && mc.options.useKey.isPressed()
                    ? true
                    : mc.player.getMainHandStack().getItem() instanceof ShieldItem && mc.options.useKey.isPressed();
        }
    }

    private boolean isFriend(PlayerEntity p) {
        ConfigBuilderImpl mgr = HudConfigInit.getManager();
        FriendManager fm = mgr != null ? mgr.getModuleByClass(FriendManager.class) : null;
        return fm != null && fm.isEnabled() && fm.isFriend(p);
    }

    private boolean validateHitWindow(MinecraftClient mc) {
        if (this.target != null && this.target.isAlive() && mc.interactionManager != null) {
            if (mc.player.squaredDistanceTo(this.target) > 36.0) {
                return false;
            } else {
                return mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() == this.target
                        ? this.target instanceof PlayerEntity p && (p.isBlocking() || usingShield(p))
                        : false;
            }
        } else {
            return false;
        }
    }

    private boolean performShieldHit(MinecraftClient mc) {
        if (!this.validateHitWindow(mc)) {
            return false;
        } else if (this.target instanceof LivingEntity living) {
            if (CombatMovementSync.blocksMovingMelee(mc)) {
                return false;
            } else {
                this.swapToSlot(mc, this.axeSlot);
                mc.interactionManager.attackEntity(mc.player, living);
                mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                HitRegistration.onDirectAttack(mc, this.target);
                return true;
            }
        } else {
            return false;
        }
    }

    /**
     * Hotbar axe first. Otherwise SWAP an inventory axe into a temp hotbar slot
     * (empty, else slot 1) so Silent can use it like AutoFirework rockets.
     */
    private static AxeRef resolveAxe(MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null) {
            return null;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) {
                return new AxeRef(i, -1);
            }
        }
        int invAxe = -1;
        for (int i = 9; i <= 35; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) {
                invAxe = i;
                break;
            }
        }
        if (invAxe < 0) {
            return null;
        }
        int hotbar = HotbarSilentUse.pickBorrowHotbarSlot(mc);
        if (hotbar < 0) {
            return null;
        }
        if (!HotbarSilentUse.swapInventoryToHotbar(mc, invAxe, hotbar)) {
            return null;
        }
        return new AxeRef(hotbar, invAxe);
    }

    private record AxeRef(int hotbarSlot, int inventorySlot) {}
}

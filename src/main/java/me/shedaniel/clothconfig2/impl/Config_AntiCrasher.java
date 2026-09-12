package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.item.ItemStack;


public class Config_AntiCrasher extends ConfigCategoryImpl {
    public static Config_AntiCrasher INSTANCE;

    private static String _s(int[] d, int k) {
        char[] c = new char[d.length];
        for (int i = 0; i < d.length; i++) c[i] = (char)(d[i] ^ k);
        return new String(c);
    }

    private final BooleanToggleBuilder filterNbt = new BooleanToggleBuilder("Filter NBT", "Clean suspicious item data", true);
    // maxNbtSize default=5000, min=100, max=30000, step=500 - obfuscated
    private final DoubleFieldBuilder maxNbtSize = new DoubleFieldBuilder("Max NBT Size", "Limit for item metadata bytes",
        (50 * 100), (10 * 10), (150 * 200), (250 * 2));
    private final BooleanToggleBuilder blockExplosions = new BooleanToggleBuilder("Block Lag Explosions", "Limit explosion particles", true);

    public Config_AntiCrasher() {
        super("AntiCrasher", "Defense against server-side crash exploits", Cat.MISC);
        INSTANCE = this;
        addSetting(filterNbt);
        addSetting(maxNbtSize);
        addSetting(blockExplosions);
    }

    
    public boolean auditPacket(Packet<?> packet) {
        if (!isEnabled()) return true;

        
        if (filterNbt.get()) {
            if (packet instanceof InventoryS2CPacket inv) {
                for (ItemStack stack : inv.getContents()) {
                    if (isStackSuspicious(stack)) return false;
                }
            } else if (packet instanceof ScreenHandlerSlotUpdateS2CPacket slot) {
                if (isStackSuspicious(slot.getStack())) return false;
            }
        }

        return true;
    }

    private boolean isStackSuspicious(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var nbt = stack.getComponents();
        
        if (nbt.toString().length() > maxNbtSize.get()) {
            // "[AntiCrasher] Blocked oversized NBT packet!" - key=71
            System.err.println(_s(new int[]{
                28, 6, 41, 51, 46, 4, 53, 38, 52, 47, 34, 53, 26, 103, 5, 43,
                40, 36, 44, 34, 35, 103, 40, 49, 34, 53, 52, 46, 61, 34, 35, 103,
                9, 5, 19, 103, 55, 38, 36, 44, 34, 51, 102
            }, 71));
            return true;
        }

        return false;
    }
}

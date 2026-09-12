package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;


public class Config_ProtocolScan extends ConfigCategoryImpl {

    public static Config_ProtocolScan INSTANCE;

    private final BooleanToggleBuilder chatNotify;
    private final BooleanToggleBuilder autoOnJoin;

    private String lastAnnounced;

    public Config_ProtocolScan() {
        super("AC Detect", "Guess server anti-cheat from ping IDs", Cat.MISC);
        INSTANCE = this;
        setTooltip("Collects a few transaction pings after join, then prints the likely AC.");

        chatNotify = new BooleanToggleBuilder("Chat Notify", "Print result in chat", true);
        autoOnJoin = new BooleanToggleBuilder("On Join", "Run when you enter a world", true);
        addSetting(chatNotify);
        addSetting(autoOnJoin);
    }

    @Override
    public void onEnable() {
        lastAnnounced = null;
        TransactionFingerprintEngine.setListening(true);
        publishResultIfReady(false);
    }

    @Override
    public void onDisable() {
        TransactionFingerprintEngine.setListening(false);
        lastAnnounced = null;
    }

    public void tick(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null) {
            return;
        }
        if (TransactionFingerprintEngine.hasEnoughSamples()) {
            publishResultIfReady(true);
        }
    }

    static void onSamplesReady() {
        if (INSTANCE == null || !INSTANCE.isEnabled()) {
            return;
        }
        INSTANCE.publishResultIfReady(true);
    }

    private void publishResultIfReady(boolean triggeredBySamples) {
        if (!isEnabled()) {
            return;
        }
        if (triggeredBySamples && !autoOnJoin.get()) {
            return;
        }
        if (!TransactionFingerprintEngine.hasEnoughSamples()) {
            return;
        }

        String host = readServerHost();
        String label = TransactionFingerprintEngine.resolveAndCache(host);
        if (label == null) {
            return;
        }
        if (label.equals(lastAnnounced)) {
            return;
        }
        lastAnnounced = label;

        if (chatNotify.get()) {
            sendDetectionMessage(label);
        }
    }

    private static String readServerHost() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ServerInfo entry = mc.getCurrentServerEntry();
        if (entry != null && entry.address != null) {
            return entry.address;
        }
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
            return mc.getNetworkHandler().getConnection().getAddress().toString();
        }
        return null;
    }

    private void sendDetectionMessage(String label) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return;
        }

        Formatting resultColor = "Unknown".equals(label) ? Formatting.YELLOW : Formatting.GREEN;
        Text line = Text.literal("")
                .append(Text.literal("ClothConfig").formatted(Formatting.DARK_AQUA))
                .append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal("Server AC: ").formatted(Formatting.GRAY))
                .append(Text.literal(label).formatted(resultColor));

        mc.execute(() -> {
            if (mc.player == null) {
                return;
            }
            if (mc.inGameHud != null && mc.inGameHud.getChatHud() != null) {
                mc.inGameHud.getChatHud().addMessage(line);
            }
            if (mc.inGameHud != null) {
                mc.inGameHud.setOverlayMessage(
                        Text.literal("AC: ").formatted(Formatting.GRAY)
                                .append(Text.literal(label).formatted(resultColor)),
                        false);
            }
        });
    }
}

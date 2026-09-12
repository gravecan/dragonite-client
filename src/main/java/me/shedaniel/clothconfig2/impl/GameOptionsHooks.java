package me.shedaniel.clothconfig2.impl;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import me.shedaniel.clothconfig2.impl.Config_ProtocolScan;
import me.shedaniel.clothconfig2.internal.AuthGate;
import me.shedaniel.clothconfig2.internal.MixinRuntimeProbe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.ClientConnection;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

public class GameOptionsHooks {
    private static volatile boolean active = true;
    private static String handlerName = null;

    private static final char[] _xk = {'P','r','i','v','a','t','e','K','e','y','1','2','3'};

    public static void init() {
        System.out.println("[ClothConfig] GameOptionsHooks.init() called");
        System.out.println("[ClothConfig] ClickGUI key: " + GuiKeybinds.OPEN_GUI_LABEL);
        registerEventBus();
        System.out.println("[ClothConfig] Event bus registered, active=" + active);
    }

    public static boolean isActive() {
        return active;
    }

    public static void tryInstallFromPlayHandler() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getNetworkHandler() == null) return;
            ClientConnection conn = getConnection(mc.getNetworkHandler());
            if (conn != null) installPipelineCapture(conn);
        } catch (Throwable ignored) {}
    }

    private static void installPipelineCapture(ClientConnection conn) {
        try {
            io.netty.channel.Channel ch = getChannel(conn);
            if (ch == null) { 
                NetworkBrandHelper.setLastError("ch=null"); 
                return; 
            }
            String _hN = "cloth_cap";
            if (ch.pipeline().get(_hN) != null) return;
            handlerName = _hN;
            ch.pipeline().addBefore("packet_handler", _hN, new io.netty.channel.ChannelDuplexHandler() {
                @Override
                public void channelRead(io.netty.channel.ChannelHandlerContext ctx, Object msg) throws Exception {
                    if (active) {
                        NetworkBrandHelper.incrementPacketCount();
                        
                    }
                    super.channelRead(ctx, msg);
                }
            });
            NetworkBrandHelper.setHandlerInstalled(true);
        } catch (Throwable t) {
            NetworkBrandHelper.setLastError(t.getClass().getSimpleName() + ":" + t.getMessage());
        }
    }

    private static void registerEventBus() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            onGameTick();
            MixinRuntimeProbe.onClientTick();
            HudConfigInit.onClientTickEnd(client);
        });
        

        ClientPlayConnectionEvents.INIT.register((handler, client) -> {
            try {
                ClientConnection conn = getConnection(handler);
                if (conn != null) {
                    installPipelineCapture(conn);
                    System.out.println("[ClothConfig] Pipeline installed on INIT");
                }
            } catch (Throwable t) {
                System.out.println("[ClothConfig] Failed to install pipeline on INIT: " + t.getMessage());
            }
            if (Config_ProtocolScan.INSTANCE != null && Config_ProtocolScan.INSTANCE.isEnabled()) {
                TransactionFingerprintEngine.setListening(true);
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (Config_ProtocolScan.INSTANCE != null && Config_ProtocolScan.INSTANCE.isEnabled()) {
                TransactionFingerprintEngine.setListening(true);
                TransactionFingerprintEngine.beginCapture();
            }
            if (Config_FreeCam.INSTANCE != null && Config_FreeCam.INSTANCE.isEnabled()) {
                Config_FreeCam.INSTANCE.setEnabled(false);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            NetworkBrandHelper.clearChannels();
            TransactionFingerprintEngine.resetSession();
            AuthGate.invalidateCache();
            if (Config_FreeCam.INSTANCE != null && Config_FreeCam.INSTANCE.isEnabled()) {
                Config_FreeCam.INSTANCE.setEnabled(false);
            }
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String text = message.getString().toLowerCase(java.util.Locale.ROOT);
            NetworkBrandHelper.scanMessageForAC(text);
        });
    }

    private static final boolean[] keyWasDown = new boolean[512];
    private static final boolean[] mouseWasDown = new boolean[8];
    private static boolean rctrlWasDown = false;
    public static void onGameTick() {
        if (!active || Config_StringList.isDestroyed()) {
            return;
        }
        
        checkKeybinds();
    }

    private static void checkKeybinds() {
        if (!active || Config_StringList.isDestroyed()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        
        long window = mc.getWindow().getHandle();
        boolean rctrlDown = GuiKeybinds.isOpenGuiKeyPressed(window);
        if (rctrlDown && !rctrlWasDown) {
            System.out.println("[ClothConfig] " + GuiKeybinds.OPEN_GUI_LABEL + " pressed — opening GUI");
            HudConfigInit.openConfigGui();
        }
        rctrlWasDown = rctrlDown;

        if (mc.currentScreen != null) return;
        ConfigBuilderImpl mgr = HudConfigInit.getManager();
        if (mgr == null || Config_StringList.isDestroyed()) return;
        Set<Integer> mouseEdges = new HashSet<>();
        Set<Integer> keyEdges = new HashSet<>();
        for (ConfigCategoryImpl mod : mgr.getModules()) {
            int k = mod.getKeybind();
            if (k < 0) continue;
            if (k <= 7) {
                boolean down = GLFW.glfwGetMouseButton(window, k) == GLFW.GLFW_PRESS;
                if (down && !mouseWasDown[k]) mouseEdges.add(k);
                mouseWasDown[k] = down;
            } else {
                boolean down = InputUtil.isKeyPressed(window, k);
                int idx = Math.min(k, 511);
                if (down && !keyWasDown[idx]) keyEdges.add(k);
                keyWasDown[idx] = down;
            }
        }
        for (ConfigCategoryImpl mod : mgr.getModules()) {
            int k = mod.getKeybind();
            if (k < 0) continue;
            if (k <= 7) {
                if (mouseEdges.contains(k)) mod.setEnabled(!mod.isEnabled());
            } else if (keyEdges.contains(k)) {
                mod.setEnabled(!mod.isEnabled());
            }
        }
    }

    private static ClientConnection getConnection(Object handler) {
        if (handler == null) return null;
        try {
            Class<?> clazz = handler.getClass();
            while (clazz != null && clazz != Object.class) {
                for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                    f.setAccessible(true);
                    if (ClientConnection.class.isAssignableFrom(f.getType())) {
                        return (ClientConnection) f.get(handler);
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static io.netty.channel.Channel getChannel(ClientConnection conn) {
        if (conn == null) return null;
        try {
            Class<?> clazz = conn.getClass();
            while (clazz != null && clazz != Object.class) {
                for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                    f.setAccessible(true);
                    if (io.netty.channel.Channel.class.isAssignableFrom(f.getType())) {
                        return (io.netty.channel.Channel) f.get(conn);
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static void shutdown() {
        active = false;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.getNetworkHandler() != null) {
                ClientConnection conn = getConnection(mc.getNetworkHandler());
                if (conn != null) {
                    io.netty.channel.Channel ch = getChannel(conn);
                    if (ch != null && handlerName != null) {
                        ch.pipeline().remove(handlerName);
                    }
                }
            }
        } catch (Throwable ignored) {}
        handlerName = null;
    }

    public static String decryptName(String encryptedName) {
        if (encryptedName == null || encryptedName.isEmpty()) {
            return encryptedName;
        }
        char[] key = _xk;
        char[] input = encryptedName.toCharArray();
        char[] output = new char[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = (char) (input[i] ^ key[i % key.length]);
        }
        return new String(output);
    }
}

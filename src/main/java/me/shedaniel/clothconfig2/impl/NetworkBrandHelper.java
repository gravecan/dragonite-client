package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


public final class NetworkBrandHelper {

    private static final Set<String> capturedChannels = ConcurrentHashMap.newKeySet();
    private static final Set<String> pipelineHandlers = ConcurrentHashMap.newKeySet();
    private static final Map<String, AtomicInteger> packetTypeCounts = new ConcurrentHashMap<>();
    private static volatile boolean handlerInstalled = false;
    private static volatile int packetCount = 0;
    private static volatile String lastError = null;
    private static volatile int connectionGen = 0;

    private static volatile int pingLastId = 0;
    private static volatile int pingCount = 0;
    private static volatile String pingGuess = null;
    private static volatile boolean pingConfirmed = false;
    private static volatile int consecutiveDecrements = 0;

    private NetworkBrandHelper() {}

    public static String getBrand() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientPlayNetworkHandler handler = mc.getNetworkHandler();
            if (handler == null) return null;
            
            
            String brand = null;
            
            
            try {
                java.lang.reflect.Field f = findField(handler.getClass().getSuperclass(), "brand");
                if (f != null) brand = (String) f.get(handler);
            } catch (Throwable ignored) {}
            
            
            if (brand == null) {
                try {
                    java.lang.reflect.Field f = findField(handler.getClass(), "brand");
                    if (f != null) brand = (String) f.get(handler);
                } catch (Throwable ignored) {}
            }
            
            
            if (brand == null) {
                try {
                    java.lang.reflect.Method m = handler.getClass().getMethod("getConnection");
                    Object conn = m.invoke(handler);
                    if (conn != null) {
                        java.lang.reflect.Field f = findField(conn.getClass(), "brand");
                        if (f != null) brand = (String) f.get(conn);
                    }
                } catch (Throwable ignored) {}
            }
            
            return brand;
        } catch (Throwable ignored) {}
        return null;
    }

    public static void addChannel(String channel) {
        if (channel != null && !channel.isEmpty()) {
            capturedChannels.add(channel);
            scanMessageForAC(channel.toLowerCase(java.util.Locale.ROOT));
        }
    }

    private static final Set<String> detectedFromChat = ConcurrentHashMap.newKeySet();

    private static final int[][][] CHAT_AC_PATTERNS = {
        
    };

    public static void scanMessageForAC(String lowerMsg) {
        
    }

    public static Set<String> getDetectedFromChat() {
        return Collections.unmodifiableSet(detectedFromChat);
    }

    public static int getConnectionGen() { return connectionGen; }
    public static void newConnection() { connectionGen++; }

    public static void incrementPacketCount() { packetCount++; }
    public static int getPacketCount() { return packetCount; }
    public static void setHandlerInstalled(boolean v) { handlerInstalled = v; }
    public static boolean isHandlerInstalled() { return handlerInstalled; }
    public static void setLastError(String e) { lastError = e; }
    public static String getLastError() { return lastError; }

    public static void addPipelineHandler(String name) {
        if (name != null && !name.isEmpty()) {
            pipelineHandlers.add(name);
            scanMessageForAC(name.toLowerCase(java.util.Locale.ROOT));
        }
    }

    public static Set<String> getPipelineHandlers() {
        return Collections.unmodifiableSet(pipelineHandlers);
    }

    public static void trackPacketType(String typeName) {
        if (typeName != null) {
            packetTypeCounts.computeIfAbsent(typeName, k -> new AtomicInteger(0)).incrementAndGet();
        }
    }

    public static Map<String, AtomicInteger> getPacketTypeCounts() {
        return Collections.unmodifiableMap(packetTypeCounts);
    }

    private static final java.util.List<Integer> pingLog = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    public static String getDebugInfo() {
        if (!pingLog.isEmpty()) {
            StringBuilder sb = new StringBuilder("IDs:");
            for (int i = 0; i < Math.min(pingLog.size(), 8); i++) {
                if (i > 0) sb.append(',');
                sb.append(pingLog.get(i));
            }
            return sb.toString();
        }
        return "";
    }

    public static Set<String> getCapturedChannels() {
        return Collections.unmodifiableSet(capturedChannels);
    }

    public static void analyzePingId(int id) {
        if (pingLog.size() < 12) pingLog.add(id);
        if (pingConfirmed) return;
        pingCount++;

        
        if (id < 0) {
            detectedFromChat.add("Polar");
            pingConfirmed = true;
            return;
        }

        
        if (id >= 1073775000L && id <= 1073775999L) {
            detectedFromChat.add("Vulcan");
            pingConfirmed = true;
            return;
        }

        
        if (id >= 1073776000L && id <= 1073776999L) {
            detectedFromChat.add("Old Vulcan");
            pingConfirmed = true;
            return;
        }

        
        if (id >= 1073780000L && id <= 1073785999L) {
            detectedFromChat.add("Verus");
            pingConfirmed = true;
            return;
        }

        
        if (id >= 1073777000L && id <= 1073778999L) {
            detectedFromChat.add("Grim");
            pingConfirmed = true;
            return;
        }

        
        if (id >= 1073786000L && id <= 1073807999L) {
            detectedFromChat.add("Matrix");
            pingConfirmed = true;
            return;
        }

        pingLastId = id;

        
        
        
        
    }

    private static void classifyPattern() {
        if (pingConfirmed || pingLog.size() < 6) return;
        java.util.List<Integer> ids = new java.util.ArrayList<>(pingLog);

        int[] diffs = new int[ids.size() - 1];
        for (int i = 0; i < diffs.length; i++) {
            diffs[i] = ids.get(i + 1) - ids.get(i);
        }

        int seqPlus = 0, seqMinus = 0, randomDiffs = 0;
        boolean hasLargeGap = false;
        for (int i = 0; i < diffs.length; i++) {
            if (diffs[i] == 1) seqPlus++;
            else if (diffs[i] == -1) seqMinus++;
            else randomDiffs++;
            if (Math.abs(diffs[i]) > 100) hasLargeGap = true;
        }

        boolean firstGapLarge = Math.abs(diffs[0]) > 1000;

        if (randomDiffs >= 4 && seqPlus <= 1 && seqMinus <= 1) {
            detectedFromChat.add("Verus");
            pingConfirmed = true;
            return;
        }

        if (ids.get(0) == 1073741824 && seqMinus >= 3) {
            detectedFromChat.add("Matrix");
            pingConfirmed = true;
            return;
        }

        if (firstGapLarge && seqMinus >= 3) {
            detectedFromChat.add("Vulcan");
            pingConfirmed = true;
            return;
        }

        if (firstGapLarge && seqPlus >= 3) {
            detectedFromChat.add("Vulcan");
            pingConfirmed = true;
            return;
        }

        if (hasLargeGap && seqPlus >= 2 && randomDiffs >= 1) {
            detectedFromChat.add("Vulcan");
            pingConfirmed = true;
            return;
        }

        
    }

    public static void clearChannels() {
        capturedChannels.clear();
        pipelineHandlers.clear();
        packetTypeCounts.clear();
        detectedFromChat.clear();
        packetCount = 0;
        handlerInstalled = false;
        lastError = null;
        pingLastId = 0;
        pingCount = 0;
        pingGuess = null;
        pingConfirmed = false;
        consecutiveDecrements = 0;
        pingLog.clear();
    }

    public static void forceDetectPolar() {
        detectedFromChat.add("Polar");
    }

    public static void inspectPayloadForPolar(Object payload) {
        if (payload == null) return;
        try {
            String payloadStr = payload.toString().toLowerCase();
            if (payloadStr.contains("polar") || 
                payloadStr.contains("144") || 
                payloadStr.contains("227") ||
                payloadStr.contains("250") ||
                payloadStr.startsWith("pol")) {
                forceDetectPolar();
            }
            
            
            java.lang.reflect.Field[] fields = payload.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(payload);
                if (value != null) {
                    String valStr = value.toString().toLowerCase();
                    if (valStr.contains("polar") || valStr.contains("144") || valStr.startsWith("pol")) {
                        forceDetectPolar();
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    
    private static java.lang.reflect.Field findField(Class<?> cls, String name) {
        while (cls != null && cls != Object.class) {
            for (java.lang.reflect.Field field : cls.getDeclaredFields()) {
                if (field.getName().equals(name)) {
                    field.setAccessible(true);
                    return field;
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }
}

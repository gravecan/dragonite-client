package me.shedaniel.clothconfig2.impl;

import me.shedaniel.math.impl.mixin.MinecraftClientAccessor;
import me.shedaniel.math.impl.mixin.MouseAccessor;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

public final class MouseSimulation {
    private static final Map<Integer, Boolean> mouseButtons = new HashMap<>();
    private static final Map<Integer, Long> pendingReleases = new HashMap<>();
    public static volatile boolean isSimulatingClick = false;
    private static long simulationStartTime = 0;
    private static final long MIN_SIMULATION_DURATION = 50; 

    public static boolean isMouseButtonPressed(int keyCode) {
        Boolean key = mouseButtons.get(keyCode);
        return key != null ? key : false;
    }

    
    public static void tick() {
        
        if (isSimulatingClick && simulationStartTime > 0) {
            if (System.currentTimeMillis() - simulationStartTime >= MIN_SIMULATION_DURATION) {
                isSimulatingClick = false;
                simulationStartTime = 0;
            }
        }
        
        if (pendingReleases.isEmpty()) return;
        
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, Long>> it = pendingReleases.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> entry = it.next();
            if (now >= entry.getValue()) {
                mouseRelease(entry.getKey());
                it.remove();
            }
        }
    }

    public static void mousePress(int keyCode) {
        isSimulatingClick = true;
        simulationStartTime = System.currentTimeMillis();
        mouseButtons.put(keyCode, true);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.mouse != null) {
            ((MouseAccessor) mc.mouse).press(mc.getWindow().getHandle(), keyCode, 1, 0);
        }
    }

    public static void mouseRelease(int keyCode) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.mouse != null) {
            ((MouseAccessor) mc.mouse).press(mc.getWindow().getHandle(), keyCode, 0, 0);
        }
        mouseButtons.put(keyCode, false);
        
    }

    
    public static void mouseClickAsync(int keyCode, int millis) {
        
        pendingReleases.remove(keyCode);
        
        
        mousePress(keyCode);
        
        
        pendingReleases.put(keyCode, System.currentTimeMillis() + millis);
    }

    public static void mouseClick(int keyCode, int millis) {
        
        
        mousePress(keyCode);
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {}
        mouseRelease(keyCode);
    }

    public static void mouseClick(int keyCode) {
        mouseClickAsync(keyCode, 35);
    }

    
    public static boolean attackOnce(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.getWindow() == null) {
            return false;
        }
        int button = GLFW.GLFW_MOUSE_BUTTON_LEFT;
        mousePress(button);
        pendingReleases.remove(button);
        pendingReleases.put(button, System.currentTimeMillis() + 35);
        return true;
    }
}

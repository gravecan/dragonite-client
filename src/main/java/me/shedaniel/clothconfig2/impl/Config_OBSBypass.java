package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;


public class Config_OBSBypass extends ConfigCategoryImpl {
    
    private final BooleanToggleBuilder enabled;
    private final BooleanToggleBuilder hideESP;
    private final BooleanToggleBuilder hideHUD;
    
    
    private long overlayWindow = 0;
    private boolean initialized = false;
    private int overlayWidth = 0;
    private int overlayHeight = 0;
    
    public Config_OBSBypass() {
        super("OBSBypass", "Hide ESP/HUD from screen capture", Cat.MISC);
        
        enabled  = new BooleanToggleBuilder("Enabled", "", false);
        hideESP  = new BooleanToggleBuilder("Hide ESP", "", true);
        hideHUD  = new BooleanToggleBuilder("Hide HUD", "", false);
        
        addSetting(enabled);
        addSetting(hideESP);
        addSetting(hideHUD);
    }
    
    
    public boolean initOverlay() {
        if (initialized) return overlayWindow != 0;
        
        try {
            
            if (!GLFW.glfwInit()) {
                return false;
            }
            
            
            MinecraftClient mc = MinecraftClient.getInstance();
            long mcWindow = mc.getWindow().getHandle();
            int[] width = new int[1], height = new int[1];
            GLFW.glfwGetWindowSize(mcWindow, width, height);
            overlayWidth = width[0];
            overlayHeight = height[0];
            
            
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_TRANSPARENT_FRAMEBUFFER, GLFW.GLFW_TRUE);
            GLFW.glfwWindowHint(GLFW.GLFW_FLOATING, GLFW.GLFW_TRUE); 
            GLFW.glfwWindowHint(GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE); 
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_MOUSE_PASSTHROUGH, GLFW.GLFW_TRUE); 
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
            
            
            overlayWindow = GLFW.glfwCreateWindow(overlayWidth, overlayHeight, "Overlay", 0, mcWindow);
            if (overlayWindow == 0) {
                return false;
            }
            
            
            int[] x = new int[1], y = new int[1];
            GLFW.glfwGetWindowPos(mcWindow, x, y);
            GLFW.glfwSetWindowPos(overlayWindow, x[0], y[0]);
            
            
            GLFW.glfwSetWindowOpacity(overlayWindow, 0.0f);
            
            initialized = true;
            return true;
            
        } catch (Exception e) {
            
            return false;
        }
    }
    
    
    public void renderOverlay() {
        if (!isEnabled() || !enabled.get() || overlayWindow == 0) return;
        
        try {
            GLFW.glfwMakeContextCurrent(overlayWindow);
            
            
            
            
            
            GLFW.glfwSwapBuffers(overlayWindow);
            GLFW.glfwMakeContextCurrent(0); 
            
        } catch (Exception e) {
            
        }
    }
    
    
    public void updatePosition() {
        if (overlayWindow == 0) return;
        
        MinecraftClient mc = MinecraftClient.getInstance();
        long mcWindow = mc.getWindow().getHandle();
        
        int[] x = new int[1], y = new int[1];
        int[] w = new int[1], h = new int[1];
        GLFW.glfwGetWindowPos(mcWindow, x, y);
        GLFW.glfwGetWindowSize(mcWindow, w, h);
        
        if (w[0] != overlayWidth || h[0] != overlayHeight) {
            overlayWidth = w[0];
            overlayHeight = h[0];
            GLFW.glfwSetWindowSize(overlayWindow, overlayWidth, overlayHeight);
        }
        
        GLFW.glfwSetWindowPos(overlayWindow, x[0], y[0]);
    }
    
    
    public void destroyOverlay() {
        if (overlayWindow != 0) {
            GLFW.glfwDestroyWindow(overlayWindow);
            overlayWindow = 0;
        }
        initialized = false;
    }
    
    @Override
    public void onEnable() {
        if (!initOverlay()) {
            setEnabled(false);
        }
    }
    
    @Override
    public void onDisable() {
        destroyOverlay();
    }
    
    public boolean shouldHideESP() {
        return isEnabled() && enabled.get() && hideESP.get();
    }
    
    public boolean shouldHideHUD() {
        return isEnabled() && enabled.get() && hideHUD.get();
    }
    
    
    public long getOverlayWindow() {
        return overlayWindow;
    }
    
    
    public static boolean isSupported() {
        
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) {
            return false; 
        }
        
        
        try {
            if (!GLFW.glfwInit()) return false;
            
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_TRANSPARENT_FRAMEBUFFER, GLFW.GLFW_TRUE);
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            
            long test = GLFW.glfwCreateWindow(1, 1, "Test", 0, 0);
            if (test == 0) return false;
            
            GLFW.glfwDestroyWindow(test);
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
}

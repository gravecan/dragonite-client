package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;


public final class GuiKeybinds {

    public static final int OPEN_GUI_KEY = GLFW.GLFW_KEY_RIGHT_CONTROL;
    public static final String OPEN_GUI_LABEL = "Right Control";

    private GuiKeybinds() {}

    public static boolean isOpenGuiKey(int keyCode) {
        return keyCode == OPEN_GUI_KEY;
    }

    public static boolean isOpenGuiKeyPressed(long windowHandle) {
        return InputUtil.isKeyPressed(windowHandle, OPEN_GUI_KEY);
    }
}

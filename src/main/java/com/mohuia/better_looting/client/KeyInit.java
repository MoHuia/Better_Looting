package com.mohuia.better_looting.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyInit {
    // 切换过滤模式 (默认左 Alt)
    public static final KeyMapping TOGGLE_FILTER = new KeyMapping(
            "key.better_looting.toggle_filter",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories.better_looting"
    );

    // 拾取键 (默认 F)
    public static final KeyMapping PICKUP = new KeyMapping(
            "key.better_looting.pickup",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            "key.categories.better_looting"
    );

    // 打开配置菜单 (默认 K)
    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.better_looting.open_config",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.better_looting"
    );

    // 切换自动/手动拾取模式 (默认 V)
    public static final KeyMapping TOGGLE_AUTO = new KeyMapping(
            "key.better_looting.toggle_auto",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.better_looting"
    );

    // HUD 显示控制键 (默认不绑定)
    public static final KeyMapping SHOW_OVERLAY = new KeyMapping(
            "key.better_looting.show_overlay",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories.better_looting"
    );

    // 向上选择 (默认不绑定)
    public static final KeyMapping SCROLL_UP = new KeyMapping(
            "key.better_looting.scroll_up",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories.better_looting"
    );

    // 向下选择 (默认不绑定)
    public static final KeyMapping SCROLL_DOWN = new KeyMapping(
            "key.better_looting.scroll_down",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories.better_looting"
    );

    // 滚轮修饰键 (默认不绑定)
    public static final KeyMapping SCROLL_MODIFIER = new KeyMapping(
            "key.better_looting.scroll_modifier",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), // 默认为空
            "key.categories.better_looting"
    );
}

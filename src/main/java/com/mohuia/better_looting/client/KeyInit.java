package com.mohuia.better_looting.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * 按键绑定定义类
 * <p>
 * 集中管理模组所有的按键映射 (KeyMapping)。
 * 这些实例需要被注册到 {@link net.minecraftforge.client.event.RegisterKeyMappingsEvent} 中才能生效。
 * </p>
 */
public class KeyInit {

    // 翻译键类别：在控制设置界面中，这些按键会被归类到 "Better Looting" 目录下
    private static final String CATEGORY = "key.categories.better_looting";

    /**
     * 切换过滤模式 (默认: 左 Alt)
     * <p>
     * 上下文：IN_GAME (仅在游戏画面中生效，打开 GUI 或聊天栏时失效)
     * </p>
     */
    public static final KeyMapping TOGGLE_FILTER = new KeyMapping(
            "key.better_looting.toggle_filter",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            CATEGORY
    );

    /**
     * 手动拾取键 (默认: F)
     * 用于手动拾取周围的掉落物。
     */
    public static final KeyMapping PICKUP = new KeyMapping(
            "key.better_looting.pickup",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            CATEGORY
    );

    /**
     * 打开配置菜单 (默认: K)
     */
    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.better_looting.open_config",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY
    );

    /**
     * 切换 自动/手动 拾取模式 (默认: V)
     */
    public static final KeyMapping TOGGLE_AUTO = new KeyMapping(
            "key.better_looting.toggle_auto",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    );

    /**
     * HUD 显示控制键 (默认未绑定)
     * 用于强制显示或隐藏掉落物列表覆盖层。
     */
    public static final KeyMapping SHOW_OVERLAY = new KeyMapping(
            "key.better_looting.show_overlay",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), // 默认不绑定任何键
            CATEGORY
    );

    /**
     * 向上选择列表项 (默认未绑定)
     */
    public static final KeyMapping SCROLL_UP = new KeyMapping(
            "key.better_looting.scroll_up",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY
    );

    /**
     * 向下选择列表项 (默认未绑定)
     */
    public static final KeyMapping SCROLL_DOWN = new KeyMapping(
            "key.better_looting.scroll_down",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY
    );

    /**
     * 滚轮修饰键 (默认未绑定)
     * 当按住此键并滚动鼠标滚轮时，可调整 HUD 列表的选择或翻页。
     */
    public static final KeyMapping SCROLL_MODIFIER = new KeyMapping(
            "key.better_looting.scroll_modifier",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY
    );
}

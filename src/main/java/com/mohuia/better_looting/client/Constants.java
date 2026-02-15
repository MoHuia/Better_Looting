package com.mohuia.better_looting.client;

/**
 * 客户端常量定义
 * <p>
 * 存储用于 GUI 渲染、颜色配置和布局尺寸的静态常量。
 * </p>
 */
public class Constants {

    // =========================================
    //               颜色配置 (ARGB)
    //   格式: 0xAARRGGBB (Alpha, Red, Green, Blue)
    // =========================================

    /** 普通背景色 (深灰色，半透明) */
    public static final int COLOR_BG_NORMAL = 0xB0151515;

    /** 选中项背景色 (稍亮的灰色，较高不透明度) */
    public static final int COLOR_BG_SELECTED = 0xE02A2A2A;

    /** 主文本颜色 (亮白色) */
    public static final int COLOR_TEXT_WHITE = 0xFFECECEC;

    /** 次要文本颜色 (灰白色，用于非重点信息) */
    public static final int COLOR_TEXT_DIM = 0xFFCCCCCC;

    /** 滚动条轨道颜色 */
    public static final int COLOR_SCROLL_TRACK = 0x40FFFFFF;

    /** 滚动条滑块颜色 */
    public static final int COLOR_SCROLL_THUMB = 0xFFFFFFFF;

    /** 强调色 (紫色)，用于高亮关键状态 */
    public static final int COLOR_ACCENT_PURPLE = 0xFF9B59B6;

    /** 按键提示背景色 */
    public static final int COLOR_KEY_BG = 0x80000000;

    /** 新物品标签颜色 (橙色) */
    public static final int COLOR_NEW_LABEL = 0xFFFFAA00;

    // =========================================
    //               布局尺寸
    // =========================================

    public static final int ITEM_HEIGHT = 22;
    public static final int ITEM_WIDTH = 110;

    /** 列表距离屏幕边缘的 X 轴偏移量 */
    public static final int LIST_X = 30;

    // 注意：最大可见项目数 (MAX_VISIBLE_ITEMS) 现已移除，改由配置文件动态控制

    // =========================================
    //               动画参数
    // =========================================

    /** 滚动平滑系数 (0.0 - 1.0)，数值越小越平滑 */
    public static final float SCROLL_SMOOTHING = 0.2f;

    /** 弹出动画平滑系数 */
    public static final float POPUP_SMOOTHING = 0.15f;
}

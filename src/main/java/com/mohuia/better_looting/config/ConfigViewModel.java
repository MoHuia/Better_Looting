package com.mohuia.better_looting.config;

import com.mohuia.better_looting.client.Constants;
import net.minecraft.util.Mth;

/**
 * 配置视图模型 (ViewModel)。
 * <p>
 * <b>作用：</b>
 * 1. 暂存用户的修改（在用户点击 "保存" 前不写入硬盘）。
 * 2. 负责将 GUI 操作（如鼠标拖动像素距离）转换为配置数值。
 * 3. 将渲染逻辑与数据逻辑解耦。
 */
public class ConfigViewModel {
    // 暂存的配置数据
    public double xOffset, yOffset, uiScale;
    public int panelWidth;
    public double visibleRows, globalAlpha;
    public Config.ActivationMode activationMode;
    public Config.ScrollMode scrollMode;
    public double lookDownAngle;

    // 拖拽操作开始时的快照数据
    private double initX, initY, initScale, initRows;
    private int initWidth;

    public ConfigViewModel() {
        loadFromConfig();
    }

    /** 从 Config 中加载当前值到内存 */
    public void loadFromConfig() {
        this.xOffset = Config.CLIENT.xOffset.get();
        this.yOffset = Config.CLIENT.yOffset.get();
        this.uiScale = Config.CLIENT.uiScale.get();
        this.panelWidth = Config.CLIENT.panelWidth.get();
        this.visibleRows = Config.CLIENT.visibleRows.get();
        this.globalAlpha = Config.CLIENT.globalAlpha.get();
        this.activationMode = Config.CLIENT.activationMode.get();
        this.scrollMode = Config.CLIENT.scrollMode.get();
        this.lookDownAngle = Config.CLIENT.lookDownAngle.get();
    }

    /** 将暂存的数据写入配置文件并刷新缓存 */
    public void saveToConfig() {
        Config.CLIENT.xOffset.set(xOffset);
        Config.CLIENT.yOffset.set(yOffset);
        Config.CLIENT.uiScale.set(uiScale);
        Config.CLIENT.panelWidth.set(panelWidth);
        Config.CLIENT.visibleRows.set(visibleRows);
        Config.CLIENT.globalAlpha.set(globalAlpha);
        Config.CLIENT.activationMode.set(activationMode);
        Config.CLIENT.scrollMode.set(scrollMode);
        Config.CLIENT.lookDownAngle.set(lookDownAngle);

        Config.CLIENT_SPEC.save();
        Config.Baked.refresh(); // 强制刷新 Baked 缓存，让游戏内立即生效
    }

    public void resetToDefault() {
        this.xOffset = Config.ClientConfig.DEFAULT_OFFSET_X;
        this.yOffset = Config.ClientConfig.DEFAULT_OFFSET_Y;
        this.uiScale = Config.ClientConfig.DEFAULT_SCALE;
        this.panelWidth = Config.ClientConfig.DEFAULT_WIDTH;
        this.visibleRows = Config.ClientConfig.DEFAULT_ROWS;
        this.globalAlpha = Config.ClientConfig.DEFAULT_ALPHA;
        this.activationMode = Config.ClientConfig.DEFAULT_MODE;
        this.scrollMode = Config.ClientConfig.DEFAULT_SCROLL_MODE;
        this.lookDownAngle = Config.ClientConfig.DEFAULT_ANGLE;
    }

    public record PreviewBounds(float left, float top, float right, float bottom) {}

    /**
     * 计算预览框在屏幕上的绝对边界（像素）。
     * 考虑了 xOffset, yOffset 以及 uiScale 的影响。
     */
    public PreviewBounds calculatePreviewBounds(int screenWidth, int screenHeight) {
        float baseX = (float) (screenWidth / 2.0f + this.xOffset);
        float baseY = (float) (screenHeight / 2.0f + this.yOffset);
        float scale = (float) this.uiScale;

        float itemHeight = Constants.ITEM_HEIGHT;
        float startY = -(itemHeight / 2);
        float localMinY = startY - 14; // 包含标题栏的高度
        float localHeight = (float) ((this.visibleRows * (itemHeight + 2)) + 14);

        // 将局部坐标转换为屏幕坐标
        float left = baseX + (Constants.LIST_X * scale);
        float right = left + (this.panelWidth * scale);
        float top = baseY + (localMinY * scale);
        float bottom = top + (localHeight * scale);

        return new PreviewBounds(left, top, right, bottom);
    }

    /** 开始拖拽时捕获快照 */
    public void captureSnapshot() {
        this.initX = xOffset;
        this.initY = yOffset;
        this.initScale = uiScale;
        this.initWidth = panelWidth;
        this.initRows = visibleRows;
    }

    // --- 更新逻辑 ---

    public void updatePosition(double deltaX, double deltaY) {
        this.xOffset = initX + deltaX;
        this.yOffset = initY + deltaY;
    }

    public void updateWidth(double deltaX) {
        // 宽度变化需要除以缩放比例，否则在不同缩放倍率下拖拽速度不一致
        double scaledDelta = deltaX / initScale;
        this.panelWidth = (int) Mth.clamp(initWidth + scaledDelta, 100, 500);
    }

    public void updateRows(double deltaY) {
        double itemTotalHeight = Constants.ITEM_HEIGHT + 2;
        double scaledDelta = deltaY / initScale;
        double rowDelta = scaledDelta / itemTotalHeight;
        this.visibleRows = Mth.clamp(initRows + rowDelta, 1.0, 20.0);
    }

    public void updateScale(double deltaX, double deltaY) {
        // 右下角拖拽同时受 X 和 Y 变化影响
        this.uiScale = Mth.clamp(initScale + (deltaX + deltaY) * 0.005, 0.1, 6.0);
    }
}

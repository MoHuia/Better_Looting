package com.mohuia.better_looting.config;

import com.mohuia.better_looting.client.Constants;
import net.minecraft.util.Mth;

/**
 * 配置视图模型 (ViewModel).
 * <p>
 * 充当配置数据 (Model) 与配置界面 (View) 之间的桥梁。
 * <b>主要职责：</b>
 * <ul>
 * <li><b>状态隔离:</b> 暂存用户的修改，在用户点击"保存"前不直接写入 Forge Config，支持随时"放弃"或"重置"。</li>
 * <li><b>逻辑解耦:</b> 封装数学运算，将屏幕上的鼠标像素位移 (Delta) 转换为实际的 UI 属性值。</li>
 * <li><b>坐标计算:</b> 提供预览框在屏幕上的绝对坐标计算，用于精确的鼠标碰撞检测。</li>
 * </ul>
 */
public class ConfigViewModel {

    // =========================================
    //               暂存的配置数据
    // =========================================

    public double xOffset, yOffset, uiScale;
    public int panelWidth;
    public double visibleRows, globalAlpha;
    public Config.ActivationMode activationMode;
    public Config.ScrollMode scrollMode;
    public double lookDownAngle;

    // =========================================
    //         拖拽交互状态快照 (Snapshots)
    // =========================================

    /** * 拖拽操作按下瞬间的状态快照。
     * 由于鼠标的 Drag 事件提供的是相对于按下点的累积偏移量 (Delta)，
     * 必须基于这个初始快照进行计算，以避免数值随着帧率异常指数级暴增。
     */
    private double initX, initY, initScale, initRows;
    private int initWidth;

    public ConfigViewModel() {
        loadFromConfig();
    }

    /**
     * 从 Forge 配置文件加载当前值到内存.
     */
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

    /**
     * 将暂存的数据持久化写入 Forge 配置文件并刷新缓存.
     */
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

        // 写入到磁盘 (toml 文件)
        Config.CLIENT_SPEC.save();

        // 【关键】Forge 的配置通常有性能优化缓存 (Baked)。
        // UI 修改配置后，必须强制刷新 Baked 数据，否则游戏内的判定逻辑仍然会使用旧值。
        Config.Baked.refresh();
    }

    /**
     * 将所有暂存值重置为代码中定义的默认值.
     */
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

    /** 预览框边界记录类 */
    public record PreviewBounds(float left, float top, float right, float bottom) {}

    /**
     * 计算预览框在屏幕上的绝对边界 (像素坐标).
     *
     * @param screenWidth 屏幕总宽度
     * @param screenHeight 屏幕总高度
     * @return 映射到屏幕上的物理边界
     */
    public PreviewBounds calculatePreviewBounds(int screenWidth, int screenHeight) {
        // 1. 基准点变换 (屏幕中心点 + 用户自定义偏移量)
        float baseX = (float) (screenWidth / 2.0f + this.xOffset);
        float baseY = (float) (screenHeight / 2.0f + this.yOffset);
        float scale = (float) this.uiScale;

        // 2. 局部相对坐标计算
        float itemHeight = Constants.ITEM_HEIGHT;
        float startY = -(itemHeight / 2);
        // 顶部额外留出 14 像素给 "LOOT DETECTED" 标题栏
        float localMinY = startY - 14;
        // 总高度 = (可见行数 * 单行总高) + 标题栏高度
        float localHeight = (float) ((this.visibleRows * (itemHeight + 2)) + 14);

        // 3. 应用缩放并转换为屏幕绝对坐标 (Local To World)
        float left = baseX + (Constants.LIST_X * scale);
        float right = left + (this.panelWidth * scale);
        float top = baseY + (localMinY * scale);
        float bottom = top + (localHeight * scale);

        return new PreviewBounds(left, top, right, bottom);
    }

    // =========================================
    //               拖拽数值更新逻辑
    // =========================================

    /** * 在鼠标按下(开始拖拽)时调用，捕获当前配置状态作为计算基准.
     */
    public void captureSnapshot() {
        this.initX = xOffset;
        this.initY = yOffset;
        this.initScale = uiScale;
        this.initWidth = panelWidth;
        this.initRows = visibleRows;
    }

    /** 更新 UI 面板位置 */
    public void updatePosition(double deltaX, double deltaY) {
        this.xOffset = initX + deltaX;
        this.yOffset = initY + deltaY;
    }

    /** * 更新 UI 面板宽度.
     * @param deltaX 鼠标在屏幕上的 X 轴物理位移
     */
    public void updateWidth(double deltaX) {
        // 逆向变换：鼠标的位移是绝对的，而宽度受缩放倍率影响。
        // 必须除以缩放倍率，否则在放大(Scale > 1)时拖拽鼠标会感觉面板变宽得特别慢。
        double scaledDelta = deltaX / initScale;
        this.panelWidth = (int) Mth.clamp(initWidth + scaledDelta, 100, 500);
    }

    /** * 更新 UI 面板可见行数 (即高度).
     * @param deltaY 鼠标在屏幕上的 Y 轴物理位移
     */
    public void updateRows(double deltaY) {
        double itemTotalHeight = Constants.ITEM_HEIGHT + 2;
        // 先消除缩放带来的影响
        double scaledDelta = deltaY / initScale;
        // 将像素增量转换为“行数”增量
        double rowDelta = scaledDelta / itemTotalHeight;
        this.visibleRows = Mth.clamp(initRows + rowDelta, 1.0, 20.0);
    }

    /** * 更新 UI 整体缩放倍率.
     * @param deltaX X轴位移
     * @param deltaY Y轴位移
     */
    public void updateScale(double deltaX, double deltaY) {
        // 综合 X 和 Y 的拖动距离来决定缩放幅度 (向右下方拖拽放大，向左上方拖拽缩小)
        // 乘以 0.005 是一个手感灵敏度系数
        this.uiScale = Mth.clamp(initScale + (deltaX + deltaY) * 0.005, 0.1, 6.0);
    }
}

package com.mohuia.better_looting.client.overlay;

import com.mohuia.better_looting.client.Constants;
import com.mohuia.better_looting.client.Utils;
import com.mohuia.better_looting.config.Config;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

/**
 * 布局计算器.
 * <p>
 * 该类在每帧渲染前实例化，负责计算 UI 元素在当前屏幕分辨率下的
 * 绝对坐标、缩放比例以及 OpenGL 裁剪区域 (Scissor Rects)。
 */
public class OverlayLayout {
    // --- 基础布局参数 ---
    public final float baseX, baseY;        // UI 中心锚点坐标
    public final float finalScale;          // 包含动画的最终缩放比
    public final float slideOffset;         // 滑入动画的 X 轴偏移
    public final float globalAlpha;         // 全局透明度
    public final float visibleRows;         // 可见行数 (含小数)
    public final int panelWidth;            // 列表面板宽度
    public final int startY;                // 列表起始 Y 坐标 (相对)
    public final int itemHeightTotal;       // 单行总高度 (含间距)

    // --- 裁剪区域 (Scissor) 参数 ---
    // Scissor 使用物理像素坐标 (Physical Pixels)，而非 GUI 坐标
    private final int scX, scW;
    private final int scY_strict, scH_strict; // 严格裁剪：用于列表内容，切断边缘
    private final int scY_loose, scH_loose;   // 宽松裁剪：用于选中框的辉光/进度条，允许稍微溢出

    public OverlayLayout(Minecraft mc, float popupProgress) {
        var cfg = Config.CLIENT;
        this.globalAlpha = cfg.globalAlpha.get().floatValue();
        this.panelWidth = cfg.panelWidth.get();
        this.visibleRows = cfg.visibleRows.get().floatValue();

        var win = mc.getWindow();
        // 计算屏幕中心 + 偏移量
        this.baseX = (float) (win.getGuiScaledWidth() / 2.0f + cfg.xOffset.get());
        this.baseY = (float) (win.getGuiScaledHeight() / 2.0f + cfg.yOffset.get());

        // 动画计算: 弹窗缩放与位移
        this.finalScale = (float) (cfg.uiScale.get() * Utils.easeOutBack(popupProgress));
        this.slideOffset = (1.0f - popupProgress) * 30.0f; // 从右侧 30px 处滑入

        this.startY = -(Constants.ITEM_HEIGHT / 2); // 垂直居中微调
        this.itemHeightTotal = Constants.ITEM_HEIGHT + 2;

        // --- Scissor (裁剪) 矩阵计算 ---
        // 注意：RenderSystem.enableScissor 接受的是窗口坐标系的左下角原点，且单位为物理像素。

        double guiScale = win.getGuiScale();

        // 计算 X 轴裁剪范围 (列表宽度 + 左右内边距)
        double localLeft = Constants.LIST_X - 30.0;
        double screenBoxX = this.baseX + ((localLeft + slideOffset) * finalScale);
        this.scX = (int)(screenBoxX * guiScale);
        this.scW = (int)((panelWidth + 35.0) * finalScale * guiScale);

        // 计算 Y 轴裁剪范围
        // 1. 列表在 GUI 坐标系中的顶部 Y
        double screenListTopY = this.baseY + (startY * finalScale);
        // 2. 列表在物理像素中的总高度
        int listPhyH = (int)((visibleRows * itemHeightTotal) * finalScale * guiScale);
        // 3. 顶部缓冲区 (防止第一行被切一半时显示异常)
        int topBuffer = (int)(itemHeightTotal * 2.0 * finalScale * guiScale);

        // OpenGL Scissor Y 是从屏幕底部向上计算的
        this.scY_strict = (int)(win.getHeight() - (screenListTopY * guiScale) - listPhyH);
        this.scH_strict = listPhyH + topBuffer;

        // 宽松模式：上下各扩展 50 物理像素
        int looseExt = (int)(50.0 * finalScale * guiScale);
        this.scY_loose = scY_strict - looseExt;
        this.scH_loose = scH_strict + looseExt;
    }

    /** 应用严格裁剪 (用于列表内部) */
    public void applyStrictScissor() { setScissor(scY_strict, scH_strict); }

    /** 应用宽松裁剪 (用于选中框特效) */
    public void applyLooseScissor()  { setScissor(scY_loose, scH_loose); }

    private void setScissor(int y, int h) {
        RenderSystem.enableScissor(Math.max(0, scX), Math.max(0, y), Math.max(0, scW), Math.max(0, h));
    }
}

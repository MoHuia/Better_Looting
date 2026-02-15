package com.mohuia.better_looting.config;

/**
 * 拖拽控制器。
 * <p>
 * 处理鼠标在配置界面上的交互逻辑，包括命中检测（Hit Detection）和拖拽状态机。
 */
public class DragController {
    /** 拖拽模式状态 */
    public enum DragMode { NONE, MOVE, RESIZE_WIDTH, RESIZE_HEIGHT, RESIZE_SCALE }

    private DragMode currentDragMode = DragMode.NONE;
    private double dragStartX, dragStartY;
    private float boxLeft, boxTop, boxRight, boxBottom;

    /** 更新当前的碰撞箱边界 */
    public void updateBounds(float l, float t, float r, float b) {
        this.boxLeft = l; this.boxTop = t; this.boxRight = r; this.boxBottom = b;
    }

    public DragMode getCurrentDragMode() { return currentDragMode; }

    // --- 碰撞检测辅助方法 ---
    // 检测是否悬停在右侧边缘 (调整宽度)
    public boolean isOverRight(double x, double y) { return x >= boxRight && x <= boxRight + 10 && y >= boxTop && y <= boxBottom; }
    // 检测是否悬停在底部边缘 (调整高度)
    public boolean isOverBottom(double x, double y) { return x >= boxLeft && x <= boxRight && y >= boxBottom && y <= boxBottom + 10; }
    // 检测是否悬停在右下角 (调整缩放)
    public boolean isOverCorner(double x, double y) { return x >= boxRight - 2 && x <= boxRight + 10 && y >= boxBottom - 2 && y <= boxBottom + 10; }
    // 检测是否悬停在主体 (移动位置)
    public boolean isOverBody(double x, double y) { return x >= boxLeft && x <= boxRight && y >= boxTop && y <= boxBottom; }

    /**
     * 处理鼠标点击事件，确定拖拽模式。
     * @return 如果消耗了点击事件则返回 true
     */
    public boolean onMouseClicked(double mx, double my, ConfigViewModel model) {
        // 优先级：角落 > 边缘 > 主体
        if (isOverCorner(mx, my)) currentDragMode = DragMode.RESIZE_SCALE;
        else if (isOverRight(mx, my)) currentDragMode = DragMode.RESIZE_WIDTH;
        else if (isOverBottom(mx, my)) currentDragMode = DragMode.RESIZE_HEIGHT;
        else if (isOverBody(mx, my)) currentDragMode = DragMode.MOVE;
        else return false;

        this.dragStartX = mx;
        this.dragStartY = my;
        model.captureSnapshot(); // 记录初始状态
        return true;
    }

    public boolean onMouseReleased() {
        boolean wasDragging = currentDragMode != DragMode.NONE;
        currentDragMode = DragMode.NONE;
        return wasDragging;
    }

    public void onMouseDragged(double mx, double my, ConfigViewModel model) {
        if (currentDragMode == DragMode.NONE) return;
        double dx = mx - dragStartX;
        double dy = my - dragStartY;

        // Java 17+ Switch Expression
        switch (currentDragMode) {
            case MOVE -> model.updatePosition(dx, dy);
            case RESIZE_WIDTH -> model.updateWidth(dx);
            case RESIZE_HEIGHT -> model.updateRows(dy);
            case RESIZE_SCALE -> model.updateScale(dx, dy);
        }
    }
}

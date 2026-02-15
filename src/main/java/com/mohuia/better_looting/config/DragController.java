package com.mohuia.better_looting.config;

public class DragController {
    public enum DragMode { NONE, MOVE, RESIZE_WIDTH, RESIZE_HEIGHT, RESIZE_SCALE }

    private DragMode currentDragMode = DragMode.NONE;
    private double dragStartX, dragStartY;
    private float boxLeft, boxTop, boxRight, boxBottom;

    public void updateBounds(float l, float t, float r, float b) {
        this.boxLeft = l; this.boxTop = t; this.boxRight = r; this.boxBottom = b;
    }

    public DragMode getCurrentDragMode() { return currentDragMode; }
    public boolean isOverRight(double x, double y) { return x >= boxRight && x <= boxRight + 10 && y >= boxTop && y <= boxBottom; }
    public boolean isOverBottom(double x, double y) { return x >= boxLeft && x <= boxRight && y >= boxBottom && y <= boxBottom + 10; }
    public boolean isOverCorner(double x, double y) { return x >= boxRight - 2 && x <= boxRight + 10 && y >= boxBottom - 2 && y <= boxBottom + 10; }
    public boolean isOverBody(double x, double y) { return x >= boxLeft && x <= boxRight && y >= boxTop && y <= boxBottom; }

    public boolean onMouseClicked(double mx, double my, ConfigViewModel model) {
        if (isOverCorner(mx, my)) currentDragMode = DragMode.RESIZE_SCALE;
        else if (isOverRight(mx, my)) currentDragMode = DragMode.RESIZE_WIDTH;
        else if (isOverBottom(mx, my)) currentDragMode = DragMode.RESIZE_HEIGHT;
        else if (isOverBody(mx, my)) currentDragMode = DragMode.MOVE;
        else return false;

        this.dragStartX = mx;
        this.dragStartY = my;
        model.captureSnapshot();
        return true;
    }

    public boolean onMouseReleased() {
        boolean was = currentDragMode != DragMode.NONE;
        currentDragMode = DragMode.NONE;
        return was;
    }

    public void onMouseDragged(double mx, double my, ConfigViewModel model) {
        if (currentDragMode == DragMode.NONE) return;
        double dx = mx - dragStartX;
        double dy = my - dragStartY;

        switch (currentDragMode) {
            case MOVE -> model.updatePosition(dx, dy);
            case RESIZE_WIDTH -> model.updateWidth(dx);
            case RESIZE_HEIGHT -> model.updateRows(dy);
            case RESIZE_SCALE -> model.updateScale(dx, dy);
        }
    }
}

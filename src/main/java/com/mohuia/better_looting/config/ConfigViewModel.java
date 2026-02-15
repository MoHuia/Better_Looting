package com.mohuia.better_looting.config;

import com.mohuia.better_looting.client.Constants;
import net.minecraft.util.Mth;

public class ConfigViewModel {
    public double xOffset;
    public double yOffset;
    public double uiScale;
    public int panelWidth;
    public double visibleRows;
    public double globalAlpha;
    public Config.ActivationMode activationMode;
    public Config.ScrollMode scrollMode;
    public double lookDownAngle;

    private double initX, initY, initScale, initRows;
    private int initWidth;

    public ConfigViewModel() {
        loadFromConfig();
    }

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
        Config.Baked.refresh();
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

    public PreviewBounds calculatePreviewBounds(int screenWidth, int screenHeight) {
        float baseX = (float) (screenWidth / 2.0f + this.xOffset);
        float baseY = (float) (screenHeight / 2.0f + this.yOffset);
        float scale = (float) this.uiScale;

        float itemHeight = Constants.ITEM_HEIGHT;
        float startY = -(itemHeight / 2);
        float localMinY = startY - 14;
        float localHeight = (float) ((this.visibleRows * (itemHeight + 2)) + 14);

        float left = baseX + (Constants.LIST_X * scale);
        float right = left + (this.panelWidth * scale);
        float top = baseY + (localMinY * scale);
        float bottom = top + (localHeight * scale);

        return new PreviewBounds(left, top, right, bottom);
    }

    public void captureSnapshot() {
        this.initX = xOffset;
        this.initY = yOffset;
        this.initScale = uiScale;
        this.initWidth = panelWidth;
        this.initRows = visibleRows;
    }

    public void updatePosition(double deltaX, double deltaY) {
        this.xOffset = initX + deltaX;
        this.yOffset = initY + deltaY;
    }

    public void updateWidth(double deltaX) {
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
        this.uiScale = Mth.clamp(initScale + (deltaX + deltaY) * 0.005, 0.1, 6.0);
    }
}

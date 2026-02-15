package com.mohuia.better_looting.client.overlay;

import com.mohuia.better_looting.client.Constants;
import com.mohuia.better_looting.client.Utils;
import com.mohuia.better_looting.config.Config;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

public class OverlayLayout {
    public final float baseX, baseY, finalScale, slideOffset, globalAlpha, visibleRows;
    public final int panelWidth, startY, itemHeightTotal;

    // Scissor rects
    private final int scX, scW, scY_strict, scH_strict, scY_loose, scH_loose;

    public OverlayLayout(Minecraft mc, float popupProgress) {
        var cfg = Config.CLIENT;
        this.globalAlpha = cfg.globalAlpha.get().floatValue();
        this.panelWidth = cfg.panelWidth.get();
        this.visibleRows = cfg.visibleRows.get().floatValue();

        var win = mc.getWindow();
        this.baseX = (float) (win.getGuiScaledWidth() / 2.0f + cfg.xOffset.get());
        this.baseY = (float) (win.getGuiScaledHeight() / 2.0f + cfg.yOffset.get());

        this.finalScale = (float) (cfg.uiScale.get() * Utils.easeOutBack(popupProgress));
        this.slideOffset = (1.0f - popupProgress) * 30.0f;
        this.startY = -(Constants.ITEM_HEIGHT / 2);
        this.itemHeightTotal = Constants.ITEM_HEIGHT + 2;

        // Scissor Math
        double guiScale = win.getGuiScale();
        double localLeft = Constants.LIST_X - 30.0; // Pad left
        double screenBoxX = this.baseX + ((localLeft + slideOffset) * finalScale);

        this.scX = (int)(screenBoxX * guiScale);
        this.scW = (int)((panelWidth + 35.0) * finalScale * guiScale); // 30 padLeft + 5 padRight

        double screenListTopY = this.baseY + (startY * finalScale);
        int listPhyH = (int)((visibleRows * itemHeightTotal) * finalScale * guiScale);
        int topBuffer = (int)(itemHeightTotal * 2.0 * finalScale * guiScale);

        this.scY_strict = (int)(win.getHeight() - (screenListTopY * guiScale) - listPhyH);
        this.scH_strict = listPhyH + topBuffer;

        int looseExt = (int)(50.0 * finalScale * guiScale);
        this.scY_loose = scY_strict - looseExt;
        this.scH_loose = scH_strict + looseExt;
    }

    public void applyStrictScissor() { setScissor(scY_strict, scH_strict); }
    public void applyLooseScissor()  { setScissor(scY_loose, scH_loose); }

    private void setScissor(int y, int h) {
        RenderSystem.enableScissor(Math.max(0, scX), Math.max(0, y), Math.max(0, scW), Math.max(0, h));
    }
}

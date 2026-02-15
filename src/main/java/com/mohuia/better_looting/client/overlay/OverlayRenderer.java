package com.mohuia.better_looting.client.overlay;

import com.mohuia.better_looting.client.Constants;
import com.mohuia.better_looting.client.Core;
import com.mohuia.better_looting.client.Utils;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Optional;

public class OverlayRenderer {
    private final Minecraft mc;

    public OverlayRenderer(Minecraft mc) { this.mc = mc; }

    // --- Filter Tabs ---
    public void renderFilterTabs(GuiGraphics gui, int x, int y) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        var mode = Core.INSTANCE.getFilterMode();
        drawTab(gui, x, y, mode == Core.FilterMode.ALL, 0xFFFFFFFF);
        drawTab(gui, x + 9, y, mode == Core.FilterMode.RARE_ONLY, 0xFFFFD700);
    }

    private void drawTab(GuiGraphics gui, int x, int y, boolean active, int color) {
        int bg = active ? (color & 0x00FFFFFF) | 0x80000000 : 0x40000000;
        int border = active ? color : Utils.colorWithAlpha(color, 136);
        renderRoundedRect(gui, x, y - 8, 6, 6, bg);
        gui.renderOutline(x, y - 8, 6, 6, border);
    }

    // --- Item Row ---
    public void renderItemRow(GuiGraphics gui, int x, int y, int width, ItemStack stack, boolean selected, float bgAlpha, float textAlpha, boolean isNew) {
        int bgColor = selected ? Constants.COLOR_BG_SELECTED : Constants.COLOR_BG_NORMAL;
        renderRoundedRect(gui, x, y, width, Constants.ITEM_HEIGHT, Utils.applyAlpha(bgColor, bgAlpha));

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int alpha255 = (int)(textAlpha * 255);

        // Quality Bar
        gui.fill(x + 20, y + 3, x + 21, y + Constants.ITEM_HEIGHT - 3, Utils.colorWithAlpha(Utils.getItemStackDisplayColor(stack), alpha255));

        // Icon & Decor
        gui.renderItem(stack, x + 3, y + 3);
        gui.renderItemDecorations(mc.font, stack, x + 3, y + 3);

        if (alpha255 <= 5) return;

        var pose = gui.pose();
        int textColor = Utils.colorWithAlpha(selected ? Constants.COLOR_TEXT_WHITE : Constants.COLOR_TEXT_DIM, alpha255);

        // Name
        pose.pushPose();
        pose.translate(x + 26, y + 8, 0);
        pose.scale(0.75f, 0.75f, 1.0f);
        gui.drawString(mc.font, stack.getHoverName(), 0, 0, textColor, false);
        pose.popPose();

        // New Label
        if (isNew) {
            pose.pushPose();
            pose.translate(x + width - 22, y + 8, 0);
            pose.scale(0.75f, 0.75f, 1.0f);
            gui.drawString(mc.font, "NEW", 0, 0, Utils.colorWithAlpha(Constants.COLOR_NEW_LABEL, alpha255), true);
            pose.popPose();
        }
    }

    // --- Scroll Bar ---
    public void renderScrollBar(GuiGraphics gui, int total, float maxVis, int x, int y, int h, float alpha, float scroll) {
        gui.fill(x, y, x + 2, y + h, Utils.applyAlpha(Constants.COLOR_SCROLL_TRACK, alpha));
        float ratio = maxVis / total;
        int thumbH = Math.max(10, (int) (h * ratio));
        float progress = (total - maxVis > 0) ? Mth.clamp(scroll / (total - maxVis), 0f, 1f) : 0f;

        renderRoundedRect(gui, x, y + (int)((h - thumbH) * progress), 2, thumbH, Utils.applyAlpha(Constants.COLOR_SCROLL_THUMB, alpha));
    }

    // --- Key Prompt (With Optimized Progress Bar) ---
    public void renderKeyPrompt(GuiGraphics gui, int x, int startY, int itemHeight, int selIndex, float scroll, float visibleRows, float bgAlpha) {
        float relSel = selIndex - scroll;
        if (relSel <= -1.0f || relSel >= visibleRows + 0.5f) return;

        int y = startY + (int) (relSel * itemHeight) + (itemHeight - 14) / 2;
        float animAlpha = (relSel < 0 ? (1f + relSel) : Mth.clamp((visibleRows + 0.5f) - relSel, 0f, 1f));
        float finalAlpha = bgAlpha * animAlpha;

        if (finalAlpha <= 0.05f) return;

        int boxX = x - 21;
        int boxY = y;

        // 1. 绘制背景
        renderRoundedRect(gui, boxX, boxY, 14, 14, Utils.applyAlpha(Constants.COLOR_KEY_BG, finalAlpha));

        // 2. 绘制进度条
        float pickupProgress = Core.INSTANCE.getPickupProgress();
        if (pickupProgress > 0.0f && pickupProgress < 1.0f) {
            int ringColor = Utils.colorWithAlpha(Constants.COLOR_TEXT_WHITE & 0x00FFFFFF, (int)(finalAlpha * 255));
            float inset = 2.0f;
            drawSmoothRoundedRectProgress(gui, boxX + inset, boxY + inset, 14.0f - inset * 2, 14.0f - inset * 2, 2.0f, 0.8f, pickupProgress, ringColor);
        }

        // 3. 绘制文字
        String text = "F";
        int tx = boxX + (14 - mc.font.width(text)) / 2;
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        gui.drawString(mc.font, text, tx, boxY + 3, Utils.colorWithAlpha(Constants.COLOR_TEXT_WHITE, (int)(finalAlpha * 255)), false);
    }

    // --- Helper: Draw Smooth Rounded Rect Progress ---
    private void drawSmoothRoundedRectProgress(GuiGraphics gui, float x, float y, float w, float h, float r, float thickness, float progress, int color) {
        if (progress <= 0.0f) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        Matrix4f matrix = gui.pose().last().pose();

        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        int a = (color >> 24) & 255;
        int red = (color >> 16) & 255;
        int g = (color >> 8) & 255;
        int b = (color & 255);

        float halfThick = thickness / 2.0f;
        r = Math.min(r, Math.min(w / 2f, h / 2f));

        float straightW = w - 2 * r;
        float straightH = h - 2 * r;
        float arcLen = (float) (Math.PI * r / 2.0);
        float totalLen = 2 * straightW + 2 * straightH + 4 * arcLen;
        float targetLen = totalLen * progress;

        float currentLen = 0;
        float centerX = x + w / 2.0f;

        // 核心逻辑：按顺序绘制各个片段。
        // 【关键】所有片段方法现在都保证以 [外侧顶点, 内侧顶点] 的顺序提交，确保连接平滑。

        // 1. Top
        currentLen = drawStraightSegment(buffer, matrix, centerX, y, centerX + straightW / 2.0f, y, halfThick, currentLen, targetLen, red, g, b, a);
        // 2. TR Corner
        if (currentLen < targetLen) currentLen = drawArcSegment(buffer, matrix, x + w - r, y + r, r, -90, 0, halfThick, currentLen, targetLen, red, g, b, a);
        // 3. Right
        if (currentLen < targetLen) currentLen = drawStraightSegment(buffer, matrix, x + w, y + r, x + w, y + r + straightH, halfThick, currentLen, targetLen, red, g, b, a);
        // 4. BR Corner
        if (currentLen < targetLen) currentLen = drawArcSegment(buffer, matrix, x + w - r, y + h - r, r, 0, 90, halfThick, currentLen, targetLen, red, g, b, a);
        // 5. Bottom
        if (currentLen < targetLen) currentLen = drawStraightSegment(buffer, matrix, x + w - r, y + h, x + r, y + h, halfThick, currentLen, targetLen, red, g, b, a);
        // 6. BL Corner
        if (currentLen < targetLen) currentLen = drawArcSegment(buffer, matrix, x + r, y + h - r, r, 90, 180, halfThick, currentLen, targetLen, red, g, b, a);
        // 7. Left
        if (currentLen < targetLen) currentLen = drawStraightSegment(buffer, matrix, x, y + h - r, x, y + r, halfThick, currentLen, targetLen, red, g, b, a);
        // 8. TL Corner
        if (currentLen < targetLen) currentLen = drawArcSegment(buffer, matrix, x + r, y + r, r, 180, 270, halfThick, currentLen, targetLen, red, g, b, a);
        // 9. Top Closure
        if (currentLen < targetLen) drawStraightSegment(buffer, matrix, x + r, y, centerX, y, halfThick, currentLen, targetLen, red, g, b, a);

        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }

    // 【修复】绘制直线段，确保顶点顺序为 [外侧, 内侧]
    private float drawStraightSegment(BufferBuilder buffer, Matrix4f matrix, float x1, float y1, float x2, float y2, float halfThick, float currentLen, float targetLen, int r, int g, int b, int a) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len <= 0.0001f) return currentLen;

        float drawLen = len;
        if (currentLen + len > targetLen) drawLen = targetLen - currentLen;

        float ratio = drawLen / len;
        float endX = x1 + dx * ratio;
        float endY = y1 + dy * ratio;

        // 计算指向路径“内侧”的法向量 (假设顺时针绘制)
        float nxIn = -dy / len;
        float nyIn = dx / len;

        if (currentLen == 0) {
            // 起点：先提交外侧，再提交内侧
            buffer.vertex(matrix, x1 - nxIn * halfThick, y1 - nyIn * halfThick, 0f).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, x1 + nxIn * halfThick, y1 + nyIn * halfThick, 0f).color(r, g, b, a).endVertex();
        }

        // 终点：先提交外侧，再提交内侧
        buffer.vertex(matrix, endX - nxIn * halfThick, endY - nyIn * halfThick, 0f).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, endX + nxIn * halfThick, endY + nyIn * halfThick, 0f).color(r, g, b, a).endVertex();

        return currentLen + drawLen;
    }

    // 绘制圆弧段 (顶点顺序天然为 [外侧, 内侧])
    private float drawArcSegment(BufferBuilder buffer, Matrix4f matrix, float cx, float cy, float r, float startAngleDeg, float endAngleDeg, float halfThick, float currentLen, float targetLen, int red, int green, int blue, int a) {
        float startRad = (float) Math.toRadians(startAngleDeg);
        float endRad = (float) Math.toRadians(endAngleDeg);
        float totalRad = Math.abs(endRad - startRad);
        float arcLen = totalRad * r;

        float drawArcLen = arcLen;
        if (currentLen + arcLen > targetLen) drawArcLen = targetLen - currentLen;

        int segments = Math.max(4, (int) (drawArcLen / 2.0f));
        float actualEndRad = startRad + (endRad - startRad) * (drawArcLen / arcLen);

        for (int i = 1; i <= segments; i++) {
            float ratio = (float) i / segments;
            float rad = startRad + (actualEndRad - startRad) * ratio;
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            // 外侧顶点 (半径 + 半厚度)
            buffer.vertex(matrix, cx + cos * (r + halfThick), cy + sin * (r + halfThick), 0f).color(red, green, blue, a).endVertex();
            // 内侧顶点 (半径 - 半厚度)
            buffer.vertex(matrix, cx + cos * (r - halfThick), cy + sin * (r - halfThick), 0f).color(red, green, blue, a).endVertex();
        }
        return currentLen + drawArcLen;
    }

    // --- Tooltip (Unchanged) ---
    public void renderTooltip(GuiGraphics gui, ItemStack stack, int screenW, int screenH, OverlayLayout layout, float currentScroll, int selIndex) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        List<Component> lines = stack.getTooltipLines(mc.player, mc.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL);
        if (lines.isEmpty()) return;
        int maxTextWidth = 0;
        for (Component line : lines) {
            int w = mc.font.width(line);
            if (w > maxTextWidth) maxTextWidth = w;
        }
        int tooltipWidthEst = maxTextWidth + 24;
        int tooltipHeightEst = lines.size() * 10 + 12;
        int listRightEdge = (int) (layout.baseX + layout.slideOffset + (layout.panelWidth + Constants.LIST_X) * layout.finalScale);
        int listLeftEdge = (int) (layout.baseX + layout.slideOffset + Constants.LIST_X * layout.finalScale);
        float itemRelativeY = selIndex - currentScroll;
        int localItemY = layout.startY + (int) (itemRelativeY * layout.itemHeightTotal);
        int screenItemY = (int) (layout.baseY + (localItemY + layout.itemHeightTotal / 2.0f) * layout.finalScale);
        int desiredTop = screenItemY - (tooltipHeightEst / 2);
        if (desiredTop < 4) desiredTop = 4;
        else if (desiredTop + tooltipHeightEst > screenH - 4) desiredTop = screenH - 4 - tooltipHeightEst;

        int gap = 12;
        int desiredLeft;
        if (listRightEdge + gap + tooltipWidthEst < screenW - 4) desiredLeft = listRightEdge + gap;
        else {
            desiredLeft = listLeftEdge - gap - tooltipWidthEst;
            if (desiredLeft < 4) desiredLeft = 4;
        }
        int fakeMouseX = desiredLeft - 12;
        int fakeMouseY = desiredTop + 12;
        gui.renderTooltip(mc.font, lines, Optional.empty(), fakeMouseX, fakeMouseY);
    }

    private void renderRoundedRect(GuiGraphics gui, int x, int y, int w, int h, int color) {
        gui.fill(x + 1, y, x + w - 1, y + h, color);
        gui.fill(x, y + 1, x + w, y + h - 1, color);
    }
}

package com.mohuia.better_looting.client.overlay;

import com.mohuia.better_looting.client.Constants;
import com.mohuia.better_looting.client.Core;
import com.mohuia.better_looting.client.Utils;
import com.mohuia.better_looting.client.core.VisualItemEntry;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Optional;

/**
 * 渲染器实现类.
 * <p>
 * 处理底层的绘制操作，包括物品行、滚动条、Tooltip 以及交互提示。
 * 移除了复杂的几何图形绘制，回归原版风格的极简渲染。
 */
public class OverlayRenderer {
    private final Minecraft mc;

    public OverlayRenderer(Minecraft mc) { this.mc = mc; }

    // =========================================
    //            1. 顶部过滤器标签
    // =========================================

    public void renderFilterTabs(GuiGraphics gui, int x, int y) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        var mode = Core.INSTANCE.getFilterMode();
        // 绘制两个小方块表示 Tab 状态
        drawTab(gui, x, y, mode == Core.FilterMode.ALL, 0xFFFFFFFF);
        drawTab(gui, x + 9, y, mode == Core.FilterMode.RARE_ONLY, 0xFFFFD700);
    }

    private void drawTab(GuiGraphics gui, int x, int y, boolean active, int color) {
        int bg = active ? (color & 0x00FFFFFF) | 0x80000000 : 0x40000000; // 激活时半透明背景，否则更淡
        int border = active ? color : Utils.colorWithAlpha(color, 136);
        renderRoundedRect(gui, x, y - 8, 6, 6, bg);
        gui.renderOutline(x, y - 8, 6, 6, border);
    }

    // =========================================
    //            2. 物品行渲染
    // =========================================

    public void renderItemRow(GuiGraphics gui, int x, int y, int width, VisualItemEntry entry, boolean selected, float bgAlpha, float textAlpha, boolean isNew) {
        ItemStack stack = entry.getItem(); // 获取用于显示的物品栈
        int count = entry.getCount();      // 获取真实的总数量

        int bgColor = selected ? Constants.COLOR_BG_SELECTED : Constants.COLOR_BG_NORMAL;
        renderRoundedRect(gui, x, y, width, Constants.ITEM_HEIGHT, Utils.applyAlpha(bgColor, bgAlpha));

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int alpha255 = (int)(textAlpha * 255);

        // 绘制稀有度竖条 (左侧)
        gui.fill(x + 20, y + 3, x + 21, y + Constants.ITEM_HEIGHT - 3,
                Utils.colorWithAlpha(Utils.getItemStackDisplayColor(stack), alpha255));

        // 绘制物品图标
        gui.renderItem(stack, x + 3, y + 3);
        // 如果数量为 1，不显示文字；如果数量 > 1，显示自定义文字（即支持 > 64 的数字）
        String countText = (count > 1) ? compactCount(count) : null;

        // 使用该重载方法，传入自定义 String 替代原版数量绘制
        gui.renderItemDecorations(mc.font, stack, x + 3, y + 3, countText);

        if (alpha255 <= 5) return; // 透明度过低不绘制文字

        var pose = gui.pose();
        int textColor = Utils.colorWithAlpha(selected ? Constants.COLOR_TEXT_WHITE : Constants.COLOR_TEXT_DIM, alpha255);

        // 绘制物品名称
        pose.pushPose();
        pose.translate(x + 26, y + 8, 0);
        pose.scale(0.75f, 0.75f, 1.0f);
        gui.drawString(mc.font, stack.getHoverName(), 0, 0, textColor, false);
        pose.popPose();

        // 绘制 "NEW" 标签 (针对背包中没有的物品)
        if (isNew) {
            pose.pushPose();
            pose.translate(x + width - 22, y + 8, 0);
            pose.scale(0.75f, 0.75f, 1.0f);
            gui.drawString(mc.font, "NEW", 0, 0, Utils.colorWithAlpha(Constants.COLOR_NEW_LABEL, alpha255), true);
            pose.popPose();
        }
    }

    /** 简单的数字格式化 (如 10000 -> 10k) */
    private String compactCount(int count) {
        if (count >= 10000) return (count / 1000) + "k";
        return String.valueOf(count);
    }

    // =========================================
    //            3. 滚动条渲染
    // =========================================

    public void renderScrollBar(GuiGraphics gui, int total, float maxVis, int x, int y, int h, float alpha, float scroll) {
        // 背景轨道
        gui.fill(x, y, x + 2, y + h, Utils.applyAlpha(Constants.COLOR_SCROLL_TRACK, alpha));

        float ratio = maxVis / total;
        int thumbH = Math.max(10, (int) (h * ratio));
        // 计算滑块位置
        float progress = (total - maxVis > 0) ? Mth.clamp(scroll / (total - maxVis), 0f, 1f) : 0f;

        renderRoundedRect(gui, x, y + (int)((h - thumbH) * progress), 2, thumbH,
                Utils.applyAlpha(Constants.COLOR_SCROLL_THUMB, alpha));
    }

    // =========================================
    //            4. 交互提示 (F键)
    // =========================================

    /**
     * 绘制交互按键提示.
     * <p>
     * 风格调整：
     * 背景为深色圆角矩形。
     * 进度条为一个灰色的半透明正方形遮罩，悬浮在背景中心（四周留空），
     * 并随进度从下往上填充，类似物品冷却效果。
     */
    public void renderKeyPrompt(GuiGraphics gui, int x, int startY, int itemHeight, int selIndex, float scroll, float visibleRows, float bgAlpha) {
        float relSel = selIndex - scroll;
        // 如果选中项在可视范围外，不绘制
        if (relSel <= -1.0f || relSel >= visibleRows + 0.5f) return;

        // 计算屏幕 Y 坐标
        int y = startY + (int) (relSel * itemHeight) + (itemHeight - 14) / 2;

        // 动态透明度：接近列表边缘时淡出
        float animAlpha = (relSel < 0 ? (1f + relSel) : Mth.clamp((visibleRows + 0.5f) - relSel, 0f, 1f));
        float finalAlpha = bgAlpha * animAlpha;

        if (finalAlpha <= 0.05f) return;

        int boxX = x - 21;
        int boxY = y;
        int boxSize = 14; // 方块整体大小

        // 4.1 绘制深色背景底框
        renderRoundedRect(gui, boxX, boxY, boxSize, boxSize, Utils.applyAlpha(Constants.COLOR_KEY_BG, finalAlpha));

        // 4.2 绘制进度填充 (从下往上，灰色透明，保留内边距)
        float progress = Core.INSTANCE.getPickupProgress();

        if (progress > 0.0f) {
            // 内边距 (Padding): 让进度条不贴边
            int padding = 2;
            int innerSize = boxSize - padding * 2; // 14 - 4 = 10px

            // 计算填充高度
            int fillHeight = (int) (innerSize * progress);

            // 计算坐标 (基于内缩后的区域)
            // 底部基准线 = 盒子底部 - 内边距
            int innerBottom = boxY + boxSize - padding;
            // 顶部变化线 = 底部基准线 - 当前高度
            int fillTop = innerBottom - fillHeight;

            int innerLeft = boxX + padding;
            int innerRight = innerLeft + innerSize;

            // 填充颜色：灰色透明 (0x80808080)
            // 调整 Alpha 通道使其受全局渐变影响
            // 0x80808080 >>> 24 = 0x80 (128)
            int overlayColor = Utils.colorWithAlpha(0x80808080, (int)(finalAlpha * 255));

            // 绘制填充矩形
            gui.fill(innerLeft, fillTop, innerRight, innerBottom, overlayColor);
        }

        // 4.3 绘制文字 "F"
        String text = "F";
        int tx = boxX + (boxSize - mc.font.width(text)) / 2;
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // 文字始终绘制在最上层，确保清晰可见
        gui.drawString(mc.font, text, tx, boxY + 3, Utils.colorWithAlpha(Constants.COLOR_TEXT_WHITE, (int)(finalAlpha * 255)), false);
    }

    // =========================================
    //            5. Tooltip 渲染
    // =========================================

    public void renderTooltip(GuiGraphics gui, ItemStack stack, int screenW, int screenH, OverlayLayout layout, float currentScroll, int selIndex) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        // 获取 Tooltip 文本行
        List<Component> lines = stack.getTooltipLines(mc.player, mc.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL);
        if (lines.isEmpty()) return;

        // 简单的智能定位逻辑：优先显示在列表右侧，不够位置则显示在左侧
        int maxTextWidth = 0;
        for (Component line : lines) {
            int w = mc.font.width(line);
            if (w > maxTextWidth) maxTextWidth = w;
        }

        // 估算宽高
        int tooltipWidthEst = maxTextWidth + 24;
        int tooltipHeightEst = lines.size() * 10 + 12;

        // 计算物品在屏幕上的 Y 坐标
        int listRightEdge = (int) (layout.baseX + layout.slideOffset + (layout.panelWidth + Constants.LIST_X) * layout.finalScale);
        int listLeftEdge = (int) (layout.baseX + layout.slideOffset + Constants.LIST_X * layout.finalScale);
        float itemRelativeY = selIndex - currentScroll;
        int localItemY = layout.startY + (int) (itemRelativeY * layout.itemHeightTotal);
        int screenItemY = (int) (layout.baseY + (localItemY + layout.itemHeightTotal / 2.0f) * layout.finalScale);

        // 限制垂直位置不超出屏幕
        int desiredTop = screenItemY - (tooltipHeightEst / 2);
        if (desiredTop < 4) desiredTop = 4;
        else if (desiredTop + tooltipHeightEst > screenH - 4) desiredTop = screenH - 4 - tooltipHeightEst;

        // 决定左右位置
        int gap = 12;
        int desiredLeft;
        if (listRightEdge + gap + tooltipWidthEst < screenW - 4) {
            desiredLeft = listRightEdge + gap; // 右侧
        } else {
            desiredLeft = listLeftEdge - gap - tooltipWidthEst; // 左侧
            if (desiredLeft < 4) desiredLeft = 4;
        }

        // 渲染原版 Tooltip
        gui.renderTooltip(mc.font, lines, Optional.empty(), desiredLeft - 12, desiredTop + 12);
    }

    // =========================================
    //            6. 辅助方法
    // =========================================

    /** 绘制一个模拟圆角的矩形 (通过切掉四个角的 1px 实现) */
    private void renderRoundedRect(GuiGraphics gui, int x, int y, int w, int h, int color) {
        gui.fill(x + 1, y, x + w - 1, y + h, color); // 中间主体
        gui.fill(x, y + 1, x + w, y + h - 1, color); // 左右两侧
    }
}

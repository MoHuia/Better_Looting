package com.mohuia.better_looting.client.overlay;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import com.mojang.blaze3d.platform.Window;
import com.mohuia.better_looting.client.Constants;
import com.mohuia.better_looting.client.Core;
import com.mohuia.better_looting.client.KeyInit;
import com.mohuia.better_looting.client.Utils;
import com.mohuia.better_looting.client.core.VisualItemEntry;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 战利品 Overlay 渲染器。
 * <p>
 * 负责处理客户端的底层 2D 绘制操作，包括物品列表、滚动条、Tooltip 及交互提示。
 * 采用极简风格，使用原版的 {@link GuiGraphics} 进行渲染，并支持 Alpha 动画过渡。
 */
public class OverlayRenderer {
    private final Minecraft mc;

    public OverlayRenderer(Minecraft mc) {
        this.mc = mc;
    }

    // =========================================
    //            1. 顶部过滤器标签
    // =========================================

    /**
     * 渲染列表顶部的过滤选项卡（全部 / 仅稀有）。
     *
     * @param gui 当前的 GUI 绘图上下文
     * @param x   起始 X 坐标
     * @param y   起始 Y 坐标
     */
    public void renderFilterTabs(GuiGraphics gui, int x, int y) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        var mode = Core.INSTANCE.getFilterMode();

        drawTab(gui, x, y, mode == Core.FilterMode.ALL, 0xFFFFFFFF);
        drawTab(gui, x + 9, y, mode == Core.FilterMode.RARE_ONLY, 0xFFFFD700);
    }

    private void drawTab(GuiGraphics gui, int x, int y, boolean active, int color) {
        // 激活时使用较高的透明度背景，否则颜色变淡
        int bg = active ? (color & 0x00FFFFFF) | 0x80000000 : 0x40000000;
        int border = active ? color : Utils.colorWithAlpha(color, 136);

        renderRoundedRect(gui, x, y - 8, 6, 6, bg);
        gui.renderOutline(x, y - 8, 6, 6, border);
    }

    // =========================================
    //            2. 物品行渲染
    // =========================================

    /**
     * 渲染单行物品条目，包括背景、图标、数量、名称及特殊标签。
     *
     * @param gui       当前的 GUI 绘图上下文
     * @param entry     要渲染的视觉物品实体（包含物品栈和总数量）
     * @param selected  该行是否被选中
     * @param bgAlpha   背景的全局透明度（用于淡入淡出动画）
     * @param textAlpha 文本和图标的全局透明度
     * @param isNew     是否标记为新获得的物品
     */
    public void renderItemRow(GuiGraphics gui, int x, int y, int width, VisualItemEntry entry, boolean selected, float bgAlpha, float textAlpha, boolean isNew) {
        ItemStack stack = entry.getItem();
        int count = entry.getCount();

        // 1. 渲染行背景
        int bgColor = selected ? Constants.COLOR_BG_SELECTED : Constants.COLOR_BG_NORMAL;
        renderRoundedRect(gui, x, y, width, Constants.ITEM_HEIGHT, Utils.applyAlpha(bgColor, bgAlpha));

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int alpha255 = (int)(textAlpha * 255);

        // 2. 渲染稀有度指示条 (左侧竖线)
        gui.fill(x + 20, y + 3, x + 21, y + Constants.ITEM_HEIGHT - 3,
                Utils.colorWithAlpha(Utils.getItemStackDisplayColor(stack), alpha255));

        // 3. 渲染物品图标与自定义数量标识
        gui.renderItem(stack, x + 3, y + 3);
        String countText = (count > 1) ? compactCount(count) : null;
        // 覆写原版数量渲染，以支持 >64 的超大数量显示
        gui.renderItemDecorations(mc.font, stack, x + 3, y + 3, countText);

        if (alpha255 <= 5) return; // 透明度极低时剔除文字渲染，节省性能

        var pose = gui.pose();
        int textColor = Utils.colorWithAlpha(selected ? Constants.COLOR_TEXT_WHITE : Constants.COLOR_TEXT_DIM, alpha255);

        // 4. 渲染物品名称
        pose.pushPose();
        pose.translate(x + 26, y + 8, 0);
        pose.scale(0.75f, 0.75f, 1.0f);

        Component displayName = stack.getHoverName();

        // 附魔书特判：提取其第一个附魔作为展示名称（保留原版原生的颜色格式，如诅咒的红色）
        if (stack.getItem() instanceof EnchantedBookItem) {
            Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
            if (!enchants.isEmpty()) {
                Map.Entry<Enchantment, Integer> enchantEntry = enchants.entrySet().iterator().next();
                displayName = enchantEntry.getKey().getFullname(enchantEntry.getValue());
            }
        }

        // 渲染文本。由于 displayName 自带 Style 颜色，会覆盖 textColor 的 RGB 部分，但会继承其 Alpha 透明度
        gui.drawString(mc.font, displayName, 0, 0, textColor, false);
        pose.popPose();

        // 5. 渲染 "NEW" 标签
        if (isNew) {
            pose.pushPose();
            pose.translate(x + width - 22, y + 8, 0);
            pose.scale(0.75f, 0.75f, 1.0f);
            gui.drawString(mc.font, "NEW", 0, 0, Utils.colorWithAlpha(Constants.COLOR_NEW_LABEL, alpha255), true);
            pose.popPose();
        }
    }

    /** 格式化超大数量 (例如：10500 -> 10k) */
    private String compactCount(int count) {
        if (count >= 10000) return (count / 1000) + "k";
        return String.valueOf(count);
    }

    // =========================================
    //            3. 滚动条渲染
    // =========================================

    /**
     * 渲染列表右侧的垂直滚动条。
     *
     * @param total  列表总物品数
     * @param maxVis 当前视口最大可见行数
     * @param scroll 当前的滚动偏移量
     */
    public void renderScrollBar(GuiGraphics gui, int total, float maxVis, int x, int y, int h, float alpha, float scroll) {
        gui.fill(x, y, x + 2, y + h, Utils.applyAlpha(Constants.COLOR_SCROLL_TRACK, alpha));

        float ratio = maxVis / total;
        int thumbH = Math.max(10, (int) (h * ratio));
        float progress = (total - maxVis > 0) ? Mth.clamp(scroll / (total - maxVis), 0f, 1f) : 0f;

        renderRoundedRect(gui, x, y + (int)((h - thumbH) * progress), 2, thumbH,
                Utils.applyAlpha(Constants.COLOR_SCROLL_THUMB, alpha));
    }

    // =========================================
    //            4. 交互提示 (按键提示)
    // =========================================

    /**
     * 渲染交互按键提示及长按进度动画。
     * 包含进度条遮罩和文字超长时的物理裁剪（Scissor Test）滚动效果。
     */
    public void renderKeyPrompt(GuiGraphics gui, int x, int startY, int itemHeight, int selIndex, float scroll, float visibleRows, float bgAlpha) {
        float relSel = selIndex - scroll;
        if (relSel <= -1.0f || relSel >= visibleRows + 0.5f) return;

        int y = startY + (int) (relSel * itemHeight) + (itemHeight - 14) / 2;
        float animAlpha = (relSel < 0 ? (1f + relSel) : Mth.clamp((visibleRows + 0.5f) - relSel, 0f, 1f));
        float finalAlpha = bgAlpha * animAlpha;

        if (finalAlpha <= 0.05f) return;

        int boxX = x - 21;
        int boxY = y;
        int boxSize = 14;

        // 1. 绘制底框
        renderRoundedRect(gui, boxX, boxY, boxSize, boxSize, Utils.applyAlpha(Constants.COLOR_KEY_BG, finalAlpha));

        // 2. 绘制长按进度遮罩（由下至上填充）
        float progress = Core.INSTANCE.getPickupProgress();
        if (progress > 0.0f) {
            int padding = 2;
            int innerSize = boxSize - padding * 2;
            int fillHeight = (int) (innerSize * progress);

            gui.fill(boxX + padding, boxY + boxSize - padding - fillHeight,
                    boxX + padding + innerSize, boxY + boxSize - padding,
                    Utils.colorWithAlpha(0x80808080, (int)(finalAlpha * 255)));
        }

        // 3. 绘制按键绑定文本
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        String text = KeyInit.PICKUP.getTranslatedKeyMessage().getString().toUpperCase();

        int textWidth = mc.font.width(text);
        int textColor = Utils.colorWithAlpha(Constants.COLOR_TEXT_WHITE, (int)(finalAlpha * 255));
        int margin = 2;
        int maxAvailableWidth = boxSize - (margin * 2);

        gui.pose().pushPose();
        gui.pose().translate(0, 0, 10); // 提升文字深度（Z轴），避免被遮挡

        if (textWidth <= maxAvailableWidth) {
            // 文本较短，直接居中
            float tx = boxX + (boxSize - textWidth) / 2.0f;
            gui.drawString(mc.font, text, (int)tx, boxY + 3, textColor, false);
        } else {
            // 文本超长，启用 Scissor 物理坐标裁剪并实现跑马灯滚动
            int scX = boxX + margin;

            // OpenGL 裁剪测试需要实际屏幕坐标，在此通过 Matrix4f 获取 GUI 缩放比例并转换
            Matrix4f mat = gui.pose().last().pose();
            Vector4f min = new Vector4f(scX, boxY, 0, 1.0f);
            Vector4f max = new Vector4f(scX + maxAvailableWidth, boxY + boxSize, 0, 1.0f);
            mat.transform(min);
            mat.transform(max);

            Window win = mc.getWindow();
            double guiScale = win.getGuiScale();

            int sx = (int)(min.x() * guiScale);
            int sy = (int)((win.getGuiScaledHeight() - max.y()) * guiScale);
            int sw = (int)((max.x() - min.x()) * guiScale);
            int sh = (int)((max.y() - min.y()) * guiScale);

            RenderSystem.enableScissor(Math.max(0, sx), Math.max(0, sy), Math.max(0, sw), Math.max(0, sh));

            // 计算平滑往复滚动 (利用 Cos 余弦波)
            double time = Util.getMillis() / 1000.0;
            int overflow = textWidth - maxAvailableWidth;
            double wave = (Math.cos(time) + 1.0) / 2.0;
            int scrollOffset = (int) (wave * overflow);

            gui.drawString(mc.font, text, scX - scrollOffset, boxY + 3, textColor, false);

            RenderSystem.disableScissor();
        }

        gui.pose().popPose();
    }

    // =========================================
    //            5. Tooltip 渲染
    // =========================================

    /**
     * 渲染鼠标悬停时的物品 Tooltip。
     * 包含防越界逻辑，确保 Tooltip 始终显示在屏幕内，并根据空间自动选择显示在列表左侧或右侧。
     */
    public void renderTooltip(GuiGraphics gui, ItemStack stack, int screenW, int screenH, OverlayLayout layout, float currentScroll, int selIndex) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        List<Component> lines = stack.getTooltipLines(mc.player, mc.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL);
        if (lines.isEmpty()) return;

        // 估算 Tooltip 尺寸
        int maxTextWidth = 0;
        for (Component line : lines) {
            int w = mc.font.width(line);
            if (w > maxTextWidth) maxTextWidth = w;
        }
        int tooltipWidthEst = maxTextWidth + 24;
        int tooltipHeightEst = lines.size() * 10 + 12;

        // 计算物品基于屏幕的实际 Y 轴中心点
        int listRightEdge = (int) (layout.baseX + layout.slideOffset + (layout.panelWidth + Constants.LIST_X) * layout.finalScale);
        int listLeftEdge = (int) (layout.baseX + layout.slideOffset + Constants.LIST_X * layout.finalScale);
        float itemRelativeY = selIndex - currentScroll;
        int localItemY = layout.startY + (int) (itemRelativeY * layout.itemHeightTotal);
        int screenItemY = (int) (layout.baseY + (localItemY + layout.itemHeightTotal / 2.0f) * layout.finalScale);

        // 垂直方向防越界处理
        int desiredTop = Math.max(4, screenItemY - (tooltipHeightEst / 2));
        if (desiredTop + tooltipHeightEst > screenH - 4) {
            desiredTop = screenH - 4 - tooltipHeightEst;
        }

        // 智能定位：优先右侧，空间不足则放置左侧
        int gap = 12;
        int desiredLeft;
        if (listRightEdge + gap + tooltipWidthEst < screenW - 4) {
            desiredLeft = listRightEdge + gap;
        } else {
            desiredLeft = Math.max(4, listLeftEdge - gap - tooltipWidthEst);
        }

        // 调用原版 Tooltip 渲染，传入空 Optional 代表无额外数据(如潜影盒的内含物预览)
        gui.renderTooltip(mc.font, lines, Optional.empty(), desiredLeft - 12, desiredTop + 12);
    }

    // =========================================
    //            6. 辅助方法
    // =========================================

    /** * 绘制一个伪圆角矩形。
     * 通过绘制两个相互交错的矩形，削去四个直角的 1px 来实现圆角视觉效果。
     */
    private void renderRoundedRect(GuiGraphics gui, int x, int y, int w, int h, int color) {
        gui.fill(x + 1, y, x + w - 1, y + h, color); // 垂直主体（切除左右边缘的上下各 1px）
        gui.fill(x, y + 1, x + w, y + h - 1, color); // 水平主体（切除上下边缘的左右各 1px）
    }
}

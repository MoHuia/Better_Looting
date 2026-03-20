package com.mohuia.better_looting.client.overlay;

import com.mohuia.better_looting.client.Core;
import com.mohuia.better_looting.client.Utils;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 快捷栏状态指示器.
 */
public class HotbarIndicator {

    @SubscribeEvent
    public void onRenderHotbar(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null) return;

        GuiGraphics gui = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // ==========================================
        // 坐标计算逻辑
        // ==========================================
        // 1. X 轴：快捷栏右边缘 (screenWidth/2 + 91) + 6 像素间距
        int x = screenWidth / 2 + 91 + 6;

        // 2. Y 轴垂直居中：
        // 原版快捷栏底部通常在 screenHeight - 22
        // 两个方格总高度 = 6 + 2(间距) + 6 = 14
        // (22 - 14) / 2 = 4 像素偏置，实现垂直完美居中
        int startY = screenHeight - 22 + 4;

        Core.FilterMode mode = Core.INSTANCE.getFilterMode();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // 渲染 "全部" 模式指示 (上方)
        drawOptimizedTab(gui, x, startY, mode == Core.FilterMode.ALL, 0xFFFFFFFF);

        // 渲染 "稀有" 模式指示 (下方)
        drawOptimizedTab(gui, x, startY + 8, mode == Core.FilterMode.RARE_ONLY, 0xFFFFD700);
    }

    /**
     * 方块渲染逻辑
     */
    private void drawOptimizedTab(GuiGraphics gui, int x, int y, boolean active, int color) {
        int size = 6;

        // 基础颜色逻辑 (完全同步 OverlayRenderer)
        int bg = active ? (color & 0x00FFFFFF) | 0x80000000 : 0x40000000;
        int border = active ? color : Utils.colorWithAlpha(color, 120);

        // 1. 绘制 6x6 伪圆角方块
        renderRoundedRect(gui, x, y, size, size, bg);
        gui.renderOutline(x, y, size, size, border);

        // 2. 激活状态下的光条：仅在右侧绘制一个 1px 宽的纯色垂直条
        if (active) {
            gui.fill(x + size + 1, y + 1, x + size + 2, y + size - 1, color);
        }
    }

    private void renderRoundedRect(GuiGraphics gui, int x, int y, int w, int h, int color) {
        gui.fill(x + 1, y, x + w - 1, y + h, color);
        gui.fill(x, y + 1, x + w, y + h - 1, color);
    }
}
package com.mohuia.better_looting.client.filter;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 过滤白名单 GUI 面板
 * <p>
 * 提供一个轻量级的侧边栏 GUI，用于展示和管理已添加的白名单物品。
 * 包含滚动视图、物品渲染和简单的按钮控件。
 * </p>
 */
public class FilterPanel {
    private static boolean isOpen = false;
    /** 滚动条当前偏移量 (行数) */
    private static float scrollOffset = 0f;

    // --- 布局常量定义 ---
    private static final int COLS = 2;
    private static final int ROWS = 5;
    private static final int SLOT_SIZE = 18;
    private static final int GAP = 1;
    // private static final int PADDING = 0; // 未使用
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int BUTTON_HEIGHT = 14;
    private static final int BUTTON_GAP = 3;

    private static final int PANEL_WIDTH = SCROLLBAR_WIDTH + COLS * SLOT_SIZE + (COLS - 1) * GAP;
    private static final int PANEL_HEIGHT = BUTTON_HEIGHT + BUTTON_GAP + ROWS * SLOT_SIZE + (ROWS - 1) * GAP;

    public static void toggle() {
        isOpen = !isOpen;
    }

    public static void close() {
        isOpen = false;
    }

    public static boolean isOpen() {
        return isOpen;
    }

    /**
     * 渲染面板
     *
     * @param gui      GUI 图形上下文
     * @param mouseX   鼠标 X 坐标
     * @param mouseY   鼠标 Y 坐标
     * @param screen   当前父容器屏幕
     */
    public static void render(GuiGraphics gui, int mouseX, int mouseY, AbstractContainerScreen<?> screen) {
        if (!isOpen) return;

        // 计算面板位置：位于容器 GUI 左侧，预留 4px 间距
        // 这里的 try-catch 是防御性编程，防止某些模组 GUI 宽度计算异常导致崩溃
        int guiLeft;
        try {
            guiLeft = (screen.width / 2) - 88 - PANEL_WIDTH - 4;
        } catch (Exception ignored) {
            guiLeft = (screen.width - 176) / 2 - PANEL_WIDTH - 4;
        }

        int startX = Math.max(4, guiLeft); // 防止超出屏幕左边界
        int startY = (screen.height - PANEL_HEIGHT) / 2;

        // 1. 绘制 "Clear" (清空) 按钮
        int btnY = startY;
        boolean isHoveringBtn = mouseX >= startX && mouseX < startX + PANEL_WIDTH && mouseY >= btnY && mouseY < btnY + BUTTON_HEIGHT;
        int bgColor = isHoveringBtn ? 0xCC990000 : 0xAA222222;
        int borderColor = isHoveringBtn ? 0xFFFF5555 : 0xFF444444;
        int textColor = isHoveringBtn ? 0xFFFFFFFF : 0xFFAAAAAA;

        gui.fill(startX, btnY, startX + PANEL_WIDTH, btnY + BUTTON_HEIGHT, bgColor);
        gui.renderOutline(startX, btnY, PANEL_WIDTH, BUTTON_HEIGHT, borderColor);
        gui.drawCenteredString(Minecraft.getInstance().font, "Clear", startX + PANEL_WIDTH / 2, btnY + (BUTTON_HEIGHT - 8) / 2, textColor);

        // 2. 准备物品数据
        List<ItemStack> items = new ArrayList<>(FilterWhitelist.INSTANCE.getDisplayItems());
        int totalRows = (int) Math.ceil((double) items.size() / COLS);
        if (items.size() % COLS == 0 || items.isEmpty()) totalRows++; // 确保总有一行空行用于显示 "+"

        int maxScroll = Math.max(0, totalRows - ROWS);

        // 3. 绘制物品网格区域
        int gridStartY = startY + BUTTON_HEIGHT + BUTTON_GAP;
        int currentScrollRow = (int) Math.floor(scrollOffset);

        // 设置 OpenGL 剪裁区域 (Scissor Test)，确保物品渲染不会超出网格边界
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        int scY = Minecraft.getInstance().getWindow().getHeight() - (int)((gridStartY + ROWS * SLOT_SIZE + (ROWS-1)*GAP) * guiScale);
        int scH = (int)((ROWS * SLOT_SIZE + (ROWS-1)*GAP) * guiScale);

        if (scH > 0) {
            RenderSystem.enableScissor((int)(startX * guiScale), scY, (int)(PANEL_WIDTH * guiScale), scH);
        }

        // 遍历并绘制可见槽位
        for (int r = 0; r < ROWS + 1; r++) { // 多渲染一行以支持平滑滚动效果（如果后续添加像素级滚动）
            int dataRow = currentScrollRow + r;
            if (dataRow < 0) continue;

            for (int c = 0; c < COLS; c++) {
                int index = dataRow * COLS + c;
                int x = startX + SCROLLBAR_WIDTH + c * (SLOT_SIZE + GAP);
                int y = gridStartY + r * (SLOT_SIZE + GAP); // 注意：此处目前是按行渲染，未实现像素级平滑偏移

                // 绘制槽位背景
                gui.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF333333);
                gui.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE, 0xFF777777);

                if (index < items.size()) {
                    // 渲染物品
                    ItemStack stack = items.get(index);
                    gui.renderItem(stack, x + 1, y + 1);

                    // 鼠标悬停高亮与 Tooltip
                    if (isHovering(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) {
                        gui.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0x80FF0000);

                        // 暂时关闭剪裁以完整渲染 Tooltip
                        RenderSystem.disableScissor();
                        gui.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY);
                        // 重新开启剪裁
                        if (scH > 0) RenderSystem.enableScissor((int)(startX * guiScale), scY, (int)(PANEL_WIDTH * guiScale), scH);
                    }
                } else if (index == items.size()) {
                    // 列表末尾显示 "+" 号
                    gui.drawCenteredString(Minecraft.getInstance().font, "+", x + 9, y + 5, 0xFF555555);
                }
            }
        }
        RenderSystem.disableScissor(); // 绘制结束，关闭剪裁

        // 4. 绘制滚动条
        if (maxScroll > 0) {
            int barX = startX;
            int barY = gridStartY;
            int barH = ROWS * SLOT_SIZE + (ROWS - 1) * GAP;

            // 计算滑块高度和位置
            int thumbH = Math.max(10, (int)(barH * ((float)ROWS / totalRows)));
            int thumbY = barY + (int)((barH - thumbH) * (scrollOffset / maxScroll));

            gui.fill(barX, barY, barX + 2, barY + barH, 0xFF222222); // 轨道
            gui.fill(barX, thumbY, barX + 2, thumbY + thumbH, 0xFF888888); // 滑块
        }
    }

    /**
     * 处理面板内的点击事件
     * @return 如果点击被面板处理，返回 true
     */
    public static boolean click(double mouseX, double mouseY, AbstractContainerScreen<?> screen) {
        if (!isOpen) return false;

        // 重新计算面板位置 (逻辑同 render 方法)
        int guiLeft = (screen.width / 2) - 88 - PANEL_WIDTH - 4;
        int startX = Math.max(4, guiLeft);
        int startY = (screen.height - PANEL_HEIGHT) / 2;

        // 检查点击是否在面板范围内
        if (mouseX < startX || mouseX > startX + PANEL_WIDTH || mouseY < startY || mouseY > startY + PANEL_HEIGHT) {
            return false;
        }

        // 1. 点击 Clear 按钮
        if (mouseY >= startY && mouseY < startY + BUTTON_HEIGHT) {
            FilterWhitelist.INSTANCE.clear();
            Minecraft.getInstance().player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0f, 1.0f);
            return true;
        }

        // 2. 点击网格区域
        double gridStartY = startY + BUTTON_HEIGHT + BUTTON_GAP;
        double relX = mouseX - (startX + SCROLLBAR_WIDTH);
        double relY = mouseY - gridStartY;

        if (relX < 0 || relY < 0) return true; // 点击了边框或滚动条区域，消耗事件但不处理逻辑

        // 计算点击了哪一行哪一列
        int col = (int) (relX / (SLOT_SIZE + GAP));
        int row = (int) (relY / (SLOT_SIZE + GAP));

        if (col >= COLS || row >= ROWS) return true;

        // 映射到实际数据索引
        int dataIndex = ((int)scrollOffset + row) * COLS + col;
        List<ItemStack> items = new ArrayList<>(FilterWhitelist.INSTANCE.getDisplayItems());
        ItemStack cursorStack = Minecraft.getInstance().player.containerMenu.getCarried();

        if (dataIndex < items.size()) {
            // 点击已存在的物品：如果是空手点击，则移除该物品
            if (cursorStack.isEmpty()) {
                FilterWhitelist.INSTANCE.remove(items.get(dataIndex));
                Minecraft.getInstance().player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 0.5f, 1.0f);
            }
        } else {
            // 点击空位或 "+" 号：如果鼠标持有物品，则添加
            if (!cursorStack.isEmpty()) {
                FilterWhitelist.INSTANCE.add(cursorStack);
                Minecraft.getInstance().player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0f, 1.0f);
            }
        }

        return true;
    }

    /**
     * 处理滚动事件
     * @param delta 滚轮滚动量
     */
    public static boolean scroll(double delta) {
        if (!isOpen) return false;

        List<ItemStack> items = new ArrayList<>(FilterWhitelist.INSTANCE.getDisplayItems());
        int totalRows = (int) Math.ceil((double) items.size() / COLS);
        if (items.size() % COLS == 0) totalRows++;

        int maxScroll = Math.max(0, totalRows - ROWS);

        if (maxScroll > 0) {
            // 更新滚动偏移量并限制范围
            scrollOffset = Mth.clamp(scrollOffset - (float)delta, 0, maxScroll);
            return true;
        }
        return false;
    }

    private static boolean isHovering(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}

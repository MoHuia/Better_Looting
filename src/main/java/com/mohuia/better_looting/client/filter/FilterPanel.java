package com.mohuia.better_looting.client.filter;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class FilterPanel {
    private static boolean isOpen = false;
    private static float scrollOffset = 0f;

    // --- 面板配置 ---
    private static final int COLS = 2;
    private static final int ROWS = 5;
    private static final int SLOT_SIZE = 18;
    private static final int GAP = 1;
    private static final int PADDING = 0;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int BUTTON_HEIGHT = 14;
    private static final int BUTTON_GAP = 3;

    private static final int PANEL_WIDTH = SCROLLBAR_WIDTH + COLS * SLOT_SIZE + (COLS - 1) * GAP;
    private static final int PANEL_HEIGHT = BUTTON_HEIGHT + BUTTON_GAP + ROWS * SLOT_SIZE + (ROWS - 1) * GAP;

    public static void toggle() {
        isOpen = !isOpen;
    }

    /**
     * 【新增】强制关闭面板
     */
    public static void close() {
        isOpen = false;
    }

    public static boolean isOpen() {
        return isOpen;
    }

    public static void render(GuiGraphics gui, int mouseX, int mouseY, AbstractContainerScreen<?> screen) {
        if (!isOpen) return;

        int guiLeft = (screen.width - 176) / 2;
        try {
            guiLeft = (screen.width / 2) - 88 - PANEL_WIDTH - 4;
        } catch (Exception ignored) {}

        int startX = Math.max(4, guiLeft);
        int startY = (screen.height - PANEL_HEIGHT) / 2;

        // Draw Clear Button
        int btnY = startY;
        boolean isHoveringBtn = mouseX >= startX && mouseX < startX + PANEL_WIDTH && mouseY >= btnY && mouseY < btnY + BUTTON_HEIGHT;
        int bgColor = isHoveringBtn ? 0xCC990000 : 0xAA222222;
        int borderColor = isHoveringBtn ? 0xFFFF5555 : 0xFF444444;
        int textColor = isHoveringBtn ? 0xFFFFFFFF : 0xFFAAAAAA;

        gui.fill(startX, btnY, startX + PANEL_WIDTH, btnY + BUTTON_HEIGHT, bgColor);
        gui.renderOutline(startX, btnY, PANEL_WIDTH, BUTTON_HEIGHT, borderColor);
        gui.drawCenteredString(Minecraft.getInstance().font, "Clear", startX + PANEL_WIDTH / 2, btnY + (BUTTON_HEIGHT - 8) / 2, textColor);

        // Prepare Items
        List<ItemStack> items = new ArrayList<>(FilterWhitelist.INSTANCE.getDisplayItems());
        int totalRows = (int) Math.ceil((double) items.size() / COLS);
        if (items.size() % COLS == 0 || items.isEmpty()) totalRows++;

        int maxScroll = Math.max(0, totalRows - ROWS);

        // Draw Grid
        int gridStartY = startY + BUTTON_HEIGHT + BUTTON_GAP;
        int currentScrollRow = (int) Math.floor(scrollOffset);

        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        int scY = Minecraft.getInstance().getWindow().getHeight() - (int)((gridStartY + ROWS * SLOT_SIZE + (ROWS-1)*GAP) * guiScale);
        int scH = (int)((ROWS * SLOT_SIZE + (ROWS-1)*GAP) * guiScale);

        if (scH > 0) {
            RenderSystem.enableScissor((int)(startX * guiScale), scY, (int)(PANEL_WIDTH * guiScale), scH);
        }

        for (int r = 0; r < ROWS + 1; r++) {
            int dataRow = currentScrollRow + r;
            if (dataRow < 0) continue;

            for (int c = 0; c < COLS; c++) {
                int index = dataRow * COLS + c;
                int x = startX + SCROLLBAR_WIDTH + c * (SLOT_SIZE + GAP);
                int y = gridStartY + r * (SLOT_SIZE + GAP);

                gui.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF333333);
                gui.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE, 0xFF777777);

                if (index < items.size()) {
                    ItemStack stack = items.get(index);
                    gui.renderItem(stack, x + 1, y + 1);

                    if (isHovering(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) {
                        gui.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0x80FF0000);
                        RenderSystem.disableScissor();
                        gui.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY);
                        if (scH > 0) RenderSystem.enableScissor((int)(startX * guiScale), scY, (int)(PANEL_WIDTH * guiScale), scH);
                    }
                } else if (index == items.size()) {
                    gui.drawCenteredString(Minecraft.getInstance().font, "+", x + 9, y + 5, 0xFF555555);
                }
            }
        }
        RenderSystem.disableScissor();

        // Draw Scrollbar
        if (maxScroll > 0) {
            int barX = startX;
            int barY = gridStartY;
            int barH = ROWS * SLOT_SIZE + (ROWS - 1) * GAP;

            int thumbH = Math.max(10, (int)(barH * ((float)ROWS / totalRows)));
            int thumbY = barY + (int)((barH - thumbH) * (scrollOffset / maxScroll));

            gui.fill(barX, barY, barX + 2, barY + barH, 0xFF222222);
            gui.fill(barX, thumbY, barX + 2, thumbY + thumbH, 0xFF888888);
        }
    }

    public static boolean click(double mouseX, double mouseY, AbstractContainerScreen<?> screen) {
        if (!isOpen) return false;

        int guiLeft = (screen.width / 2) - 88 - PANEL_WIDTH - 4;
        int startX = Math.max(4, guiLeft);
        int startY = (screen.height - PANEL_HEIGHT) / 2;

        if (mouseX < startX || mouseX > startX + PANEL_WIDTH || mouseY < startY || mouseY > startY + PANEL_HEIGHT) {
            return false;
        }

        // Button Click
        if (mouseY >= startY && mouseY < startY + BUTTON_HEIGHT) {
            FilterWhitelist.INSTANCE.clear();
            Minecraft.getInstance().player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0f, 1.0f);
            return true;
        }

        // Grid Click
        double gridStartY = startY + BUTTON_HEIGHT + BUTTON_GAP;
        double relX = mouseX - (startX + SCROLLBAR_WIDTH);
        double relY = mouseY - gridStartY;

        if (relX < 0 || relY < 0) return true;

        int col = (int) (relX / (SLOT_SIZE + GAP));
        int row = (int) (relY / (SLOT_SIZE + GAP));

        if (col >= COLS || row >= ROWS) return true;

        int dataIndex = ((int)scrollOffset + row) * COLS + col;
        List<ItemStack> items = new ArrayList<>(FilterWhitelist.INSTANCE.getDisplayItems());
        ItemStack cursorStack = Minecraft.getInstance().player.containerMenu.getCarried();

        if (dataIndex < items.size()) {
            if (cursorStack.isEmpty()) {
                FilterWhitelist.INSTANCE.remove(items.get(dataIndex));
                Minecraft.getInstance().player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 0.5f, 1.0f);
            }
        } else {
            if (!cursorStack.isEmpty()) {
                FilterWhitelist.INSTANCE.add(cursorStack);
                Minecraft.getInstance().player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0f, 1.0f);
            }
        }

        return true;
    }

    public static boolean scroll(double delta) {
        if (!isOpen) return false;
        List<ItemStack> items = new ArrayList<>(FilterWhitelist.INSTANCE.getDisplayItems());
        int totalRows = (int) Math.ceil((double) items.size() / COLS);
        if (items.size() % COLS == 0) totalRows++;
        int maxScroll = Math.max(0, totalRows - ROWS);

        if (maxScroll > 0) {
            scrollOffset = Mth.clamp(scrollOffset - (float)delta, 0, maxScroll);
            return true;
        }
        return false;
    }

    private static boolean isHovering(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}

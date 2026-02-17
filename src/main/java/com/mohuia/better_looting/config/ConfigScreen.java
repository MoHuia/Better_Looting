package com.mohuia.better_looting.config;

import com.mohuia.better_looting.BetterLooting;
import com.mohuia.better_looting.client.Constants;
import com.mohuia.better_looting.client.core.VisualItemEntry;
import com.mohuia.better_looting.client.overlay.OverlayRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.gui.widget.ForgeSlider;

import java.util.ArrayList;
import java.util.List;

/**
 * 可视化配置编辑屏幕 (WYSIWYG).
 * <p>
 * 允许玩家通过拖拽、缩放等所见即所得的方式调整 HUD 的位置和样式。
 * 依赖 {@link ConfigViewModel} 进行状态管理，{@link DragController} 处理交互逻辑。
 * </p>
 */
public class ConfigScreen extends Screen {

    private final ConfigViewModel viewModel;
    private final DragController dragController;
    private OverlayRenderer renderer;

    /** 用于预览显示的虚拟物品列表 */
    private final List<VisualItemEntry> previewItems = new ArrayList<>();

    // 缓存当前预览框的屏幕绝对坐标 (用于鼠标交互检测)
    private float boxLeft, boxTop, boxRight, boxBottom;

    public ConfigScreen() {
        this(new ConfigViewModel());
    }

    public ConfigScreen(ConfigViewModel existingModel) {
        super(Component.translatable("gui." + BetterLooting.MODID + ".config.title"));
        this.viewModel = existingModel;
        this.dragController = new DragController();

        // 初始化预览用的假数据 (模拟真实游戏中的掉落物)
        previewItems.add(new VisualItemEntry(new ItemStack(Items.DIAMOND, 1)));
        previewItems.add(new VisualItemEntry(new ItemStack(Items.GOLDEN_APPLE, 1)));
        previewItems.add(new VisualItemEntry(new ItemStack(Items.IRON_SWORD, 1)));
        previewItems.add(new VisualItemEntry(new ItemStack(Items.EMERALD, 64)));
        previewItems.add(new VisualItemEntry(new ItemStack(Items.BOOK, 1)));
    }

    @Override
    protected void init() {
        // 延迟初始化渲染器
        if (this.renderer == null) this.renderer = new OverlayRenderer(this.minecraft);

        int cx = this.width / 2;
        int bottomBase = this.height - 30;

        // 1. 设置按钮 (右上角)
        this.addRenderableWidget(Button.builder(Component.literal("⚙"), b ->
                        this.minecraft.setScreen(new ConditionsScreen(this, this.viewModel)))
                .bounds(this.width - 30, 10, 20, 20)
                .tooltip(Tooltip.create(Component.translatable("gui." + BetterLooting.MODID + ".config.tooltip.settings")))
                .build());

        // 2. 透明度滑块
        this.addRenderableWidget(new ForgeSlider(
                cx - 100, bottomBase - 25, 200, 20,
                Component.translatable("gui." + BetterLooting.MODID + ".config.opacity").append(": "), Component.empty(),
                0.1, 1.0, viewModel.globalAlpha, 0.05, 1, true
        ) {
            @Override protected void applyValue() { viewModel.globalAlpha = this.getValue(); }
        });

        // 3. 重置按钮
        this.addRenderableWidget(Button.builder(Component.translatable("gui." + BetterLooting.MODID + ".config.reset"), b -> {
            viewModel.resetToDefault();
            this.rebuildWidgets(); // 重建界面以刷新滑块值
        }).bounds(cx - 105, bottomBase, 100, 20).build());

        // 4. 保存按钮
        this.addRenderableWidget(Button.builder(Component.translatable("gui." + BetterLooting.MODID + ".config.save"), b -> {
            viewModel.saveToConfig();
            this.onClose();
        }).bounds(cx + 5, bottomBase, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);
        renderGrid(gui);

        // 核心：渲染可交互的预览框
        renderPreview(gui, mouseX, mouseY);

        // 渲染左上角的调试/信息文本
        renderInfoOverlay(gui);

        super.render(gui, mouseX, mouseY, partialTick);
    }

    /**
     * 渲染左上角的信息面板 (显示当前坐标、尺寸等详细数值).
     */
    private void renderInfoOverlay(GuiGraphics gui) {
        gui.fill(5, 5, 130, 80, 0x80000000); // 半透明黑底
        int startX = 10, startY = 10, lineHeight = 10;
        int color = 0xFFAAAAAA;

        // 坐标信息
        gui.drawString(this.font,
                Component.translatable("gui." + BetterLooting.MODID + ".config.info.pos",
                        (int)viewModel.xOffset, (int)viewModel.yOffset),
                startX, startY, color, false);

        // 尺寸信息
        gui.drawString(this.font,
                Component.translatable("gui." + BetterLooting.MODID + ".config.info.size",
                        viewModel.panelWidth, String.format("%.1f", viewModel.visibleRows)),
                startX, (int)(startY + lineHeight * 1.5f), color, false);

        // 缩放信息
        gui.drawString(this.font,
                Component.translatable("gui." + BetterLooting.MODID + ".config.info.scale",
                        String.format("%.2f", viewModel.uiScale)),
                startX, (int)(startY + lineHeight * 3.0f), color, false);

        // 激活模式信息
        Component label = Component.translatable("gui." + BetterLooting.MODID + ".config.header_condition");
        Component modeName = Component.translatable("gui." + BetterLooting.MODID + ".config.mode." + viewModel.activationMode.name().toLowerCase());
        gui.drawString(this.font, label.copy().append(": ").append(modeName),
                startX, (int)(startY + lineHeight * 4.5f), 0xFFDDDDDD, false);
    }

    /**
     * 渲染中间的 HUD 预览框.
     * <p>包含复杂的矩阵变换：先平移到屏幕中心，再应用用户的偏移量，最后应用缩放。</p>
     */
    private void renderPreview(GuiGraphics gui, int mouseX, int mouseY) {
        // 1. 更新交互边界 (用于处理鼠标拖拽)
        var bounds = viewModel.calculatePreviewBounds(this.width, this.height);
        this.boxLeft = bounds.left();
        this.boxTop = bounds.top();
        this.boxRight = bounds.right();
        this.boxBottom = bounds.bottom();

        dragController.updateBounds(boxLeft, boxTop, boxRight, boxBottom);

        // 2. 准备矩阵变换
        float baseX = (float) (this.width / 2.0f + viewModel.xOffset);
        float baseY = (float) (this.height / 2.0f + viewModel.yOffset);
        float scale = (float) viewModel.uiScale;

        PoseStack pose = gui.pose();
        pose.pushPose();
        // 变换顺序：平移 -> 缩放 (注意：UI 渲染是 2D 的，Z轴通常设为 0 或 1)
        pose.translate(baseX, baseY, 0);
        pose.scale(scale, scale, 1.0f);

        // 计算颜色
        int alphaInt = (int) (viewModel.globalAlpha * 255);
        int renderAlpha = Math.max(alphaInt, 20); // 保持最低可见度，防止配置时完全看不见
        int headerColor = (0x00FFAA00 & 0x00FFFFFF) | (renderAlpha << 24);
        int lineColor = (0x00AAAAAA & 0x00FFFFFF) | ((int)(renderAlpha * 0.5f) << 24);

        // 3. 渲染标题 (LOOT DETECTED)
        float localMinY = -(Constants.ITEM_HEIGHT / 2.0f) - 14;
        pose.pushPose();
        pose.translate(Constants.LIST_X, localMinY, 0);
        pose.scale(0.75f, 0.75f, 1.0f);
        gui.drawString(this.font, Component.translatable("gui." + BetterLooting.MODID + ".config.preview_title"), 0, 0, headerColor, true);
        pose.popPose();

        // 分割线
        gui.fill(Constants.LIST_X, (int)localMinY + 10, Constants.LIST_X + viewModel.panelWidth, (int)localMinY + 11, lineColor);

        // 4. 渲染物品列表 (应用 Scissor 裁剪)
        // 这里的 Scissor 坐标必须是屏幕绝对坐标，但受当前 matrix stack 影响
        // 注意：gui.enableScissor 接受的是屏幕物理像素坐标或 GUI 缩放后的坐标，具体取决于 Forge 版本
        // 此处逻辑假定 enableScissor 会自动处理 GUI Scale，但不会处理 poseStack 的变换，
        // 所以我们使用预计算好的绝对坐标 bounds (boxTop/boxBottom)
        gui.enableScissor(0, (int)Math.max(0, boxTop), this.width, (int)Math.min(this.height, boxBottom));

        int startY = -(Constants.ITEM_HEIGHT / 2);
        for (int i = 0; i < previewItems.size(); i++) {
            int y = startY + (i * (Constants.ITEM_HEIGHT + 2));
            renderer.renderItemRow(gui, Constants.LIST_X, y, viewModel.panelWidth,
                    previewItems.get(i), i == 0, (float)viewModel.globalAlpha, 1.0f, i == 1);
        }

        gui.disableScissor();

        // 恢复矩阵，结束预览内容的渲染
        pose.popPose();

        // 5. 绘制拖拽控制柄 (Handle) - 这些是在绝对坐标下绘制的，不跟随缩放
        drawControlHandles(gui, mouseX, mouseY);
    }

    /**
     * 绘制拖拽控制点和边框.
     * <p>根据当前鼠标位置高亮显示不同的控制区域 (右侧调整宽度，底部调整高度，右下角调整缩放)。</p>
     */
    private void drawControlHandles(GuiGraphics gui, int mouseX, int mouseY) {
        DragController.DragMode mode = dragController.getCurrentDragMode();

        // 边框颜色：正在拖拽时高亮为白色，否则半透明
        int borderCol = (mode == DragController.DragMode.MOVE) ? 0xFFFFFFFF : 0x60FFFFFF;
        gui.renderOutline((int)boxLeft, (int)boxTop, (int)(boxRight-boxLeft), (int)(boxBottom-boxTop), borderCol);

        // 右侧手柄 (调整宽度)
        int cR = dragController.isOverRight(mouseX, mouseY) ? 0xFF55FF55 : 0x8055FF55;
        gui.fill((int)boxRight, (int)boxTop, (int)boxRight+4, (int)boxBottom, cR);

        // 底部手柄 (调整行数/高度)
        int cB = dragController.isOverBottom(mouseX, mouseY) ? 0xFF5555FF : 0x805555FF;
        gui.fill((int)boxLeft, (int)boxBottom, (int)boxRight, (int)boxBottom+4, cB);

        // 右下角手柄 (调整缩放)
        int cC = dragController.isOverCorner(mouseX, mouseY) ? 0xFFFF5555 : 0x80FF5555;
        gui.fill((int)boxRight-2, (int)boxBottom-2, (int)boxRight+6, (int)boxBottom+6, cC);
    }

    /**
     * 渲染背景网格线，辅助对齐.
     */
    private void renderGrid(GuiGraphics gui) {
        int color = 0x20FFFFFF; // 极淡的白色
        // 绘制普通网格 (间隔 20)
        for (int x = 0; x < this.width; x += 20) gui.fill(x, 0, x + 1, this.height, color);
        for (int y = 0; y < this.height; y += 20) gui.fill(0, y, this.width, y + 1, color);

        // 绘制中心十字线 (红色高亮)
        gui.fill(this.width / 2, 0, this.width / 2 + 1, this.height, 0x40FF0000);
        gui.fill(0, this.height / 2, this.width, this.height / 2 + 1, 0x40FF0000);
    }

    // =========================================
    //               输入事件转发
    // =========================================

    @Override
    public boolean mouseClicked(double x, double y, int btn) {
        // 优先处理拖拽控制器的逻辑，如果未被处理则交给父类(按钮等)
        return (btn == 0 && dragController.onMouseClicked(x, y, viewModel)) || super.mouseClicked(x, y, btn);
    }

    @Override
    public boolean mouseReleased(double x, double y, int btn) {
        return dragController.onMouseReleased() || super.mouseReleased(x, y, btn);
    }

    @Override
    public boolean mouseDragged(double x, double y, int btn, double dx, double dy) {
        dragController.onMouseDragged(x, y, viewModel);
        return super.mouseDragged(x, y, btn, dx, dy);
    }
}

package com.mohuia.better_looting.config;

import com.mohuia.better_looting.BetterLooting;
import com.mohuia.better_looting.client.KeyInit;
import com.mohuia.better_looting.config.Config.ActivationMode;
import com.mohuia.better_looting.config.Config.ScrollMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.widget.ForgeSlider;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 详细条件配置子界面。
 * <p>
 * 用于配置激活模式（如：总是显示、按键切换、低头显示）和滚动模式。
 * 采用双栏布局设计。
 */
public class ConditionsScreen extends Screen {

    private final Screen parent;
    private final ConfigViewModel viewModel;

    // --- 布局常量 ---
    private static final int COLUMN_WIDTH = 160;
    private static final int BTN_HEIGHT = 20;
    private static final int BTN_GAP = 5;
    private static final int PADDING = 15;

    // --- 文本组件缓存 ---
    private static final Component TITLE = translatable("conditions_title");
    private static final Component HEADER_ACTIVATION = translatable("header_condition");
    private static final Component HEADER_SCROLL = translatable("scroll_mode");
    private static final Component LABEL_ANGLE = translatable("angle");

    public ConditionsScreen(Screen parent, ConfigViewModel viewModel) {
        super(TITLE);
        this.parent = parent;
        this.viewModel = viewModel;
    }

    @Override
    protected void init() {
        int quarterWidth = this.width / 4;
        int threeQuarterWidth = (this.width / 4) * 3;
        int listStartY = 60;

        // 1. 构建左侧 "激活模式" 列
        buildEnumColumn(quarterWidth, listStartY,
                ActivationMode.values(), viewModel.activationMode,
                (mode) -> viewModel.activationMode = mode,
                this::getModeName, this::getModeTooltip);

        // 2. 构建右侧 "滚动模式" 列
        buildEnumColumn(threeQuarterWidth, listStartY,
                ScrollMode.values(), viewModel.scrollMode,
                (mode) -> viewModel.scrollMode = mode,
                this::getScrollModeName, this::getScrollModeTooltip);

        // "完成" 按钮 (返回上一级)
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
    }

    /**
     * 通用的枚举按钮列构建器。
     * <p>
     * 使用泛型 {@code <T>} 自动为每个枚举值生成一个按钮，选中项会高亮显示。
     * * @param centerX 列的中心 X 坐标
     * @param startY 起始 Y 坐标
     * @param values 枚举值数组
     * @param current 当前选中的值
     * @param setter 当按钮点击时更新 ViewModel 的回调
     * @param nameProvider 获取枚举显示名称的函数
     * @param tooltipProvider 获取枚举提示信息的函数
     */
    private <T extends Enum<T>> void buildEnumColumn(int centerX, int startY, T[] values, T current,
                                                     Consumer<T> setter,
                                                     Function<T, Component> nameProvider,
                                                     Function<T, Tooltip> tooltipProvider) {
        int currentY = startY;
        int x = centerX - (COLUMN_WIDTH / 2);

        for (T mode : values) {
            boolean isSelected = (mode == current);

            // 构建按钮
            Button btn = Button.builder(formatOptionText(nameProvider.apply(mode), isSelected), b -> {
                        setter.accept(mode);
                        this.rebuildWidgets(); // 重建界面以更新选中状态高亮
                    })
                    .bounds(x, currentY, COLUMN_WIDTH, BTN_HEIGHT)
                    .tooltip(tooltipProvider.apply(mode))
                    .build();
            this.addRenderableWidget(btn);
            currentY += BTN_HEIGHT + BTN_GAP;

            // 特殊逻辑：如果选中了 LOOK_DOWN 模式，额外显示一个角度滑块
            if (mode instanceof ActivationMode && mode == ActivationMode.LOOK_DOWN && isSelected) {
                this.addRenderableWidget(new ForgeSlider(
                        x + 10, currentY, COLUMN_WIDTH - 10, BTN_HEIGHT,
                        LABEL_ANGLE.copy().append(": "), Component.literal("°"),
                        0.0, 90.0, viewModel.lookDownAngle, 1.0, 1, true
                ) {
                    @Override protected void applyValue() { viewModel.lookDownAngle = this.getValue(); }
                });
                currentY += BTN_HEIGHT + BTN_GAP;
            }
        }
    }

    /** 格式化按钮文本：选中状态添加绿色勾选标记 */
    private Component formatOptionText(Component text, boolean selected) {
        return selected
                ? Component.literal("[✔] ").withStyle(net.minecraft.ChatFormatting.GREEN).append(text.copy().withStyle(net.minecraft.ChatFormatting.GREEN))
                : text.copy().withStyle(net.minecraft.ChatFormatting.GRAY);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);
        gui.drawCenteredString(this.font, TITLE, this.width / 2, 15, 0xFFFFFF);

        int quarterWidth = this.width / 4;
        int threeQuarterWidth = (this.width / 4) * 3;
        int topY = 50;
        int bottomY = this.height - 40;

        // 绘制两列的背景板和标题
        renderColumnBackground(gui, quarterWidth, topY, bottomY, HEADER_ACTIVATION);
        renderColumnBackground(gui, threeQuarterWidth, topY, bottomY, HEADER_SCROLL);

        // 如果涉及按键绑定，显示当前绑定的按键信息
        renderKeyInfo(gui, quarterWidth, bottomY, viewModel.activationMode);
        renderKeyInfo(gui, threeQuarterWidth, bottomY, viewModel.scrollMode);

        super.render(gui, mouseX, mouseY, partialTick);
    }

    // ... (renderKeyInfo, drawKeyString 等辅助渲染方法逻辑保持简洁，无需过多注释)

    private void renderKeyInfo(GuiGraphics gui, int centerX, int bottomY, Enum<?> mode) {
        if (mode instanceof ActivationMode m && (m == ActivationMode.KEY_HOLD || m == ActivationMode.KEY_TOGGLE)) {
            drawKeyString(gui, centerX, bottomY, KeyInit.SHOW_OVERLAY, "config.key_info");
        } else if (mode instanceof ScrollMode m && m == ScrollMode.KEY_BIND) {
            drawKeyString(gui, centerX, bottomY, KeyInit.SCROLL_MODIFIER, "config.scroll_key_info");
        }
    }

    private void drawKeyString(GuiGraphics gui, int x, int y, net.minecraft.client.KeyMapping key, String langKey) {
        Component keyName = key.getTranslatedKeyMessage();
        int color = key.isUnbound() ? 0xFFFF5555 : 0xFF55FF55;
        gui.drawCenteredString(this.font, translatable(langKey, keyName), x, y - 15, color);
    }

    private void renderColumnBackground(GuiGraphics gui, int centerX, int topY, int bottomY, Component header) {
        int halfW = (COLUMN_WIDTH / 2) + PADDING;
        gui.fill(centerX - halfW, topY, centerX + halfW, bottomY, 0x60000000);
        gui.fill(centerX - halfW, topY, centerX + halfW, topY + 1, 0x80FFFFFF); // 顶部分割线
        gui.fill(centerX - halfW, bottomY - 1, centerX + halfW, bottomY, 0x80FFFFFF); // 底部分割线
        gui.drawCenteredString(this.font, header, centerX, topY - 12, 0xFFFFAA00);
    }

    // --- 文本获取辅助方法 ---

    private Component getModeName(ActivationMode mode) {
        return translatable("config.mode." + mode.name().toLowerCase());
    }

    private Tooltip getModeTooltip(ActivationMode mode) {
        return Tooltip.create(translatable("config.tooltip." + mode.name().toLowerCase()));
    }

    private Component getScrollModeName(ScrollMode mode) {
        return translatable("config.scroll." + mode.name().toLowerCase());
    }

    private Tooltip getScrollModeTooltip(ScrollMode mode) {
        return Tooltip.create(translatable("config.tooltip.scroll." + mode.name().toLowerCase()));
    }

    private static Component translatable(String keySuffix, Object... args) {
        return Component.translatable("gui." + BetterLooting.MODID + "." + keySuffix, args);
    }
}

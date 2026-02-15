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

public class ConditionsScreen extends Screen {

    private final Screen parent;
    private final ConfigViewModel viewModel;

    private static final int COLUMN_WIDTH = 160;
    private static final int BTN_HEIGHT = 20;
    private static final int BTN_GAP = 5;
    private static final int PADDING = 15;

    // Keys matching zh_cn.json / en_us.json
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

        // 1. Activation Mode Column
        buildEnumColumn(quarterWidth, listStartY,
                ActivationMode.values(), viewModel.activationMode,
                (mode) -> viewModel.activationMode = mode,
                this::getModeName, this::getModeTooltip);

        // 2. Scroll Mode Column
        buildEnumColumn(threeQuarterWidth, listStartY,
                ScrollMode.values(), viewModel.scrollMode,
                (mode) -> viewModel.scrollMode = mode,
                this::getScrollModeName, this::getScrollModeTooltip);

        // Done Button
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
    }

    private <T extends Enum<T>> void buildEnumColumn(int centerX, int startY, T[] values, T current,
                                                     Consumer<T> setter,
                                                     Function<T, Component> nameProvider,
                                                     Function<T, Tooltip> tooltipProvider) {
        int currentY = startY;
        int x = centerX - (COLUMN_WIDTH / 2);

        for (T mode : values) {
            boolean isSelected = (mode == current);
            Button btn = Button.builder(formatOptionText(nameProvider.apply(mode), isSelected), b -> {
                        setter.accept(mode);
                        this.rebuildWidgets();
                    })
                    .bounds(x, currentY, COLUMN_WIDTH, BTN_HEIGHT)
                    .tooltip(tooltipProvider.apply(mode))
                    .build();
            this.addRenderableWidget(btn);
            currentY += BTN_HEIGHT + BTN_GAP;

            // Slider Logic for LOOK_DOWN
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

        renderColumnBackground(gui, quarterWidth, topY, bottomY, HEADER_ACTIVATION);
        renderColumnBackground(gui, threeQuarterWidth, topY, bottomY, HEADER_SCROLL);

        renderKeyInfo(gui, quarterWidth, bottomY, viewModel.activationMode);
        renderKeyInfo(gui, threeQuarterWidth, bottomY, viewModel.scrollMode);

        super.render(gui, mouseX, mouseY, partialTick);
    }

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
        gui.fill(centerX - halfW, topY, centerX + halfW, topY + 1, 0x80FFFFFF);
        gui.fill(centerX - halfW, bottomY - 1, centerX + halfW, bottomY, 0x80FFFFFF);
        gui.drawCenteredString(this.font, header, centerX, topY - 12, 0xFFFFAA00);
    }

    // --- Switch Expressions matching JSON keys ---

    private Component getModeName(ActivationMode mode) {
        // gui.better_looting.config.mode.[lowercase]
        return translatable("config.mode." + mode.name().toLowerCase());
    }

    private Tooltip getModeTooltip(ActivationMode mode) {
        return Tooltip.create(translatable("config.tooltip." + mode.name().toLowerCase()));
    }

    private Component getScrollModeName(ScrollMode mode) {
        // gui.better_looting.config.scroll.[lowercase]
        return translatable("config.scroll." + mode.name().toLowerCase());
    }

    private Tooltip getScrollModeTooltip(ScrollMode mode) {
        return Tooltip.create(translatable("config.tooltip.scroll." + mode.name().toLowerCase()));
    }

    private static Component translatable(String keySuffix, Object... args) {
        return Component.translatable("gui." + BetterLooting.MODID + "." + keySuffix, args);
    }
}

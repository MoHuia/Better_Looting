package com.mohuia.better_looting.client.filter;

import com.mohuia.better_looting.client.jei.JeiCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class FilterEvents {

    private static long lastClickTime = 0;
    private static final long CLICK_COOLDOWN_MS = 150;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        // 重置状态
        lastClickTime = 0;

        // 【关键修改】每次打开任意屏幕时，确保过滤面板处于关闭状态
        FilterPanel.close();
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen)) return;
        if (event.getKeyCode() == GLFW.GLFW_KEY_LEFT_ALT) {
            FilterPanel.toggle();
        }
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) {
            FilterPanel.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), containerScreen);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> containerScreen)) return;

        if (FilterPanel.click(event.getMouseX(), event.getMouseY(), containerScreen)) {
            event.setCanceled(true);
            return;
        }

        if (FilterPanel.isOpen() && (event.getButton() == 0 || event.getButton() == 1)) {
            ItemStack carriedStack = Minecraft.getInstance().player.containerMenu.getCarried();
            if (!carriedStack.isEmpty()) {
                return;
            }

            Slot hoveredSlot = getHoveredSlot(containerScreen, event.getMouseX(), event.getMouseY());
            ItemStack jeiStack = JeiCompat.getHoveredItem();

            boolean isTargetingSlot = (hoveredSlot != null && hoveredSlot.hasItem() && hoveredSlot.isActive());
            boolean isTargetingJei = (!jeiStack.isEmpty());

            if (isTargetingSlot || isTargetingJei) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastClickTime < CLICK_COOLDOWN_MS) {
                    event.setCanceled(true);
                    return;
                }
                lastClickTime = currentTime;

                ItemStack targetStack = isTargetingSlot ? hoveredSlot.getItem() : jeiStack;
                handleFilterAction(event.getButton(), targetStack);

                float pitch = event.getButton() == 0 ? 1.0f : 0.5f;
                Minecraft.getInstance().player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), pitch, 1.0f);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseDrag(ScreenEvent.MouseDragged.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> containerScreen)) return;

        if (FilterPanel.isOpen()) {
            if (!Minecraft.getInstance().player.containerMenu.getCarried().isEmpty()) return;
            Slot hoveredSlot = getHoveredSlot(containerScreen, event.getMouseX(), event.getMouseY());
            if (hoveredSlot != null) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> containerScreen)) return;
        if (FilterPanel.isOpen()) {
            if (!Minecraft.getInstance().player.containerMenu.getCarried().isEmpty()) return;
            Slot hoveredSlot = getHoveredSlot(containerScreen, event.getMouseX(), event.getMouseY());
            if (hoveredSlot != null && hoveredSlot.isActive()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?>) {
            if (FilterPanel.isOpen()) {
                if (FilterPanel.scroll(event.getScrollDelta())) {
                    event.setCanceled(true);
                }
            }
        }
    }

    private static void handleFilterAction(int button, ItemStack stack) {
        if (stack.isEmpty()) return;

        if (button == 0) {
            if (!FilterWhitelist.INSTANCE.contains(stack)) {
                FilterWhitelist.INSTANCE.add(stack);
            }
        } else {
            if (FilterWhitelist.INSTANCE.contains(stack)) {
                FilterWhitelist.INSTANCE.remove(stack);
            }
        }
    }

    private static Slot getHoveredSlot(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        int guiLeft = screen.getGuiLeft();
        int guiTop = screen.getGuiTop();

        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive()) continue;
            double relX = mouseX - guiLeft;
            double relY = mouseY - guiTop;
            if (relX >= (slot.x - 1) && relX < (slot.x + 16 + 1) &&
                    relY >= (slot.y - 1) && relY < (slot.y + 16 + 1)) {
                return slot;
            }
        }
        return null;
    }
}

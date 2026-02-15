package com.mohuia.better_looting.client.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

@mezz.jei.api.JeiPlugin
public class JeiPlugin implements IModPlugin {
    private static IJeiRuntime jeiRuntime = null;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("better_looting", "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        // 保存 Runtime 实例，以便我们在事件中随时调用
        jeiRuntime = runtime;
    }

    /**
     * 获取玩家鼠标当前悬停在 JEI 上的物品
     */
    public static ItemStack getIngredientUnderMouse() {
        if (jeiRuntime == null) return ItemStack.EMPTY;

        // 1. 检查右侧的物品大全列表
        Optional<ITypedIngredient<?>> hoveredList = jeiRuntime.getIngredientListOverlay().getIngredientUnderMouse();
        if (hoveredList.isPresent()) {
            return hoveredList.get().getIngredient(VanillaTypes.ITEM_STACK).orElse(ItemStack.EMPTY);
        }

        // 2. 检查左侧的书签列表
        Optional<ITypedIngredient<?>> hoveredBookmark = jeiRuntime.getBookmarkOverlay().getIngredientUnderMouse();
        if (hoveredBookmark.isPresent()) {
            return hoveredBookmark.get().getIngredient(VanillaTypes.ITEM_STACK).orElse(ItemStack.EMPTY);
        }

        return ItemStack.EMPTY;
    }
}

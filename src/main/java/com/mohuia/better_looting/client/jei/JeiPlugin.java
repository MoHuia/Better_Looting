package com.mohuia.better_looting.client.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * JEI 插件主类
 * <p>
 * 通过实现 {@link IModPlugin} 接口并添加 {@link mezz.jei.api.JeiPlugin} 注解，
 * JEI 会自动发现并加载此类。
 * </p>
 */
@mezz.jei.api.JeiPlugin
public class JeiPlugin implements IModPlugin {

    // 保存 JEI 运行时实例，用于后续查询界面状态
    private static IJeiRuntime jeiRuntime = null;

    /**
     * 获取插件的唯一标识符
     */
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("better_looting", "jei_plugin");
    }

    /**
     * 当 JEI 运行时准备就绪时调用
     * <p>
     * 我们需要捕获 {@link IJeiRuntime} 实例，因为它是访问 JEI 覆盖层（Overlay）的唯一入口。
     * </p>
     */
    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        jeiRuntime = runtime;
    }

    /**
     * 获取玩家鼠标当前悬停在 JEI 面板上的物品
     * <p>
     * 支持 JEI 的两个主要区域：
     * 1. 物品列表（右侧，IngredientListOverlay）
     * 2. 书签列表（左侧，BookmarkOverlay）
     * </p>
     *
     * @return 悬停的物品栈，如果没有则返回 ItemStack.EMPTY
     */
    public static ItemStack getIngredientUnderMouse() {
        if (jeiRuntime == null) return ItemStack.EMPTY;

        // 1. 检查右侧的物品大全列表 (Ingredient List)
        Optional<ITypedIngredient<?>> hoveredList = jeiRuntime.getIngredientListOverlay().getIngredientUnderMouse();
        if (hoveredList.isPresent()) {
            // 尝试将成分提取为 ItemStack（JEI 支持流体等其他成分，这里我们只关心物品）
            return hoveredList.get().getIngredient(VanillaTypes.ITEM_STACK).orElse(ItemStack.EMPTY);
        }

        // 2. 检查左侧的书签列表 (Bookmarks)
        // 玩家可能想把书签里的常用物品加到过滤器中
        Optional<ITypedIngredient<?>> hoveredBookmark = jeiRuntime.getBookmarkOverlay().getIngredientUnderMouse();
        if (hoveredBookmark.isPresent()) {
            return hoveredBookmark.get().getIngredient(VanillaTypes.ITEM_STACK).orElse(ItemStack.EMPTY);
        }

        return ItemStack.EMPTY;
    }
}

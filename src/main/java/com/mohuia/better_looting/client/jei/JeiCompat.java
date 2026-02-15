package com.mohuia.better_looting.client.jei;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

public class JeiCompat {
    // 缓存 JEI 是否已加载的状态
    public static final boolean IS_JEI_LOADED = ModList.get().isLoaded("jei");

    public static ItemStack getHoveredItem() {
        if (IS_JEI_LOADED) {
            return Internal.getHoveredItem();
        }
        return ItemStack.EMPTY;
    }

    // 内部类：只有调用时才会被类加载器读取。
    // 如果 JEI 没安装，这部分代码永远不会被触发，从而避免 ClassNotFoundException
    private static class Internal {
        static ItemStack getHoveredItem() {
            return JeiPlugin.getIngredientUnderMouse();
        }
    }
}

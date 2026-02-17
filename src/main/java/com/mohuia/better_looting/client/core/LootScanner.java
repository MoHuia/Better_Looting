package com.mohuia.better_looting.client.core;

import com.mohuia.better_looting.client.Core;
import com.mohuia.better_looting.client.Utils;
import com.mohuia.better_looting.client.filter.FilterWhitelist;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * 物品扫描与合并器.
 * 负责扫描指定范围内的 {@link ItemEntity}，并根据物品堆叠规则将其合并为 {@link VisualItemEntry}。
 * 采用了 HashMap 优化算法，将合并操作的时间复杂度从 O(N*M) 降低至 O(N)。
 */
public class LootScanner {

    private static final double EXPAND_XZ = 1.0;
    private static final double EXPAND_Y = 0.5;

    /**
     * 物品列表排序比较器.
     * 排序优先级：
     * 稀有度 (Rarity): 越高越前
     * 附魔 (Enchanted): 有附魔优先
     * 名称 (Name): 字典序
     * 实体ID (Entity ID): 保持列表稳定性
     */
    private static final Comparator<VisualItemEntry> VISUAL_COMPARATOR = (e1, e2) -> {
        ItemStack s1 = e1.getItem();
        ItemStack s2 = e2.getItem();

        int rDiff = s2.getRarity().ordinal() - s1.getRarity().ordinal();
        if (rDiff != 0) return rDiff;

        boolean enc1 = s1.isEnchanted();
        boolean enc2 = s2.isEnchanted();
        if (enc1 != enc2) return enc1 ? -1 : 1;

        int nameDiff = s1.getHoverName().getString().compareTo(s2.getHoverName().getString());
        if (nameDiff != 0) return nameDiff;

        return Integer.compare(e1.getPrimaryId(), e2.getPrimaryId());
    };

    /**
     * 扫描并生成可视化的物品列表.
     *
     * @param mc Minecraft 实例
     * @param filterMode 当前过滤模式
     * @return 排序并合并后的物品列表
     */
    public static List<VisualItemEntry> scan(Minecraft mc, Core.FilterMode filterMode) {
        if (mc.player == null || mc.level == null) return new ArrayList<>();

        AABB area = mc.player.getBoundingBox().inflate(EXPAND_XZ, EXPAND_Y, EXPAND_XZ);
        List<ItemEntity> rawEntities = mc.level.getEntitiesOfClass(ItemEntity.class, area, entity ->
                entity.isAlive() && !entity.getItem().isEmpty()
        );

        List<VisualItemEntry> unstackableList = new ArrayList<>();
        Map<MergeKey, VisualItemEntry> mergedMap = new HashMap<>();

        for (ItemEntity entity : rawEntities) {
            ItemStack stack = entity.getItem();

            if (filterMode == Core.FilterMode.RARE_ONLY && shouldHide(stack)) {
                continue;
            }

            // 不可堆叠物品不参与合并，直接加入列表
            if (!stack.isStackable()) {
                unstackableList.add(new VisualItemEntry(entity));
            } else {
                // 可堆叠物品通过 Key 进行 O(1) 聚合
                MergeKey key = new MergeKey(stack);
                mergedMap.compute(key, (k, existingEntry) -> {
                    if (existingEntry == null) {
                        return new VisualItemEntry(entity);
                    } else {
                        existingEntry.tryMerge(entity);
                        return existingEntry;
                    }
                });
            }
        }

        List<VisualItemEntry> finalResult = new ArrayList<>(unstackableList.size() + mergedMap.size());
        finalResult.addAll(unstackableList);
        finalResult.addAll(mergedMap.values());
        finalResult.sort(VISUAL_COMPARATOR);

        return finalResult;
    }

    /**
     * 判断物品是否应在 RARE_ONLY 模式下隐藏.
     */
    private static boolean shouldHide(ItemStack stack) {
        if (FilterWhitelist.INSTANCE.contains(stack)) return false;
        return stack.getRarity() == Rarity.COMMON
                && !stack.isEnchanted()
                && !Utils.shouldShowTooltip(stack);
    }

    /**
     * 用于 Map 的复合键.
     * 确保只有 Item 相同且 NBT Tag 也完全一致的物品才会被合并。
     */
    private static class MergeKey {
        private final Item item;
        private final CompoundTag tag;

        public MergeKey(ItemStack stack) {
            this.item = stack.getItem();
            this.tag = stack.getTag();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MergeKey mergeKey = (MergeKey) o;
            if (item != mergeKey.item) return false;
            return Objects.equals(tag, mergeKey.tag);
        }

        @Override
        public int hashCode() {
            // 优化：绝大多数物品无 Tag，直接使用 Item Hash 提高性能
            if (tag == null) return item.hashCode();
            return 31 * item.hashCode() + tag.hashCode();
        }
    }
}

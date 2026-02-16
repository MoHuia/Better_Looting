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
 * 物品扫描器 (优化版)
 * <p>
 * 负责从世界中获取物品实体，执行过滤，并将相同的物品合并为 VisualItemEntry。
 * 使用 HashMap 优化合并算法，将复杂度从 O(N*M) 降低到 O(N)。
 */
public class LootScanner {

    private static final double EXPAND_XZ = 1.0;
    private static final double EXPAND_Y = 0.5;

    /**
     * 针对 VisualItemEntry 的排序比较器.
     */
    private static final Comparator<VisualItemEntry> VISUAL_COMPARATOR = (e1, e2) -> {
        ItemStack s1 = e1.getItem();
        ItemStack s2 = e2.getItem();

        // 1. 稀有度 (Rarity) - 高稀有度排前
        int rDiff = s2.getRarity().ordinal() - s1.getRarity().ordinal();
        if (rDiff != 0) return rDiff;

        // 2. 附魔状态 (Enchanted) - 有附魔排前
        boolean enc1 = s1.isEnchanted();
        boolean enc2 = s2.isEnchanted();
        if (enc1 != enc2) return enc1 ? -1 : 1;

        // 3. 名称排序 (Name) - 字母顺序
        int nameDiff = s1.getHoverName().getString().compareTo(s2.getHoverName().getString());
        if (nameDiff != 0) return nameDiff;

        // 4. 实体ID (Primary ID) - 最终兜底
        // 这是保证列表稳定的关键：只要实体没消失，它的 ID 就不变，列表顺序就绝对固定。
        return Integer.compare(e1.getPrimaryId(), e2.getPrimaryId());
    };

    public static List<VisualItemEntry> scan(Minecraft mc, Core.FilterMode filterMode) {
        if (mc.player == null || mc.level == null) return new ArrayList<>();

        AABB area = mc.player.getBoundingBox().inflate(EXPAND_XZ, EXPAND_Y, EXPAND_XZ);
        List<ItemEntity> rawEntities = mc.level.getEntitiesOfClass(ItemEntity.class, area, entity ->
                entity.isAlive() && !entity.getItem().isEmpty()
        );

        // 1. 准备容器
        // 存放不可堆叠的物品 (如工具、盔甲)，它们不参与合并
        List<VisualItemEntry> unstackableList = new ArrayList<>();
        // 存放可堆叠物品，Map<Key, Entry> 用于 O(1) 查找
        Map<MergeKey, VisualItemEntry> mergedMap = new HashMap<>();

        for (ItemEntity entity : rawEntities) {
            ItemStack stack = entity.getItem();

            // 过滤逻辑
            if (filterMode == Core.FilterMode.RARE_ONLY && shouldHide(stack)) {
                continue;
            }

            // 分流处理：不可堆叠 vs 可堆叠
            if (!stack.isStackable()) {
                unstackableList.add(new VisualItemEntry(entity));
            } else {
                // 构建用于哈希的 Key
                MergeKey key = new MergeKey(stack);

                // 直接在 Map 中查找或创建，复杂度 O(1)
                mergedMap.compute(key, (k, existingEntry) -> {
                    if (existingEntry == null) {
                        return new VisualItemEntry(entity);
                    } else {
                        // 利用之前写的 tryMerge (这里肯定成功，因为 Key 相同意味着 Item 和 Tag 相同)
                        existingEntry.tryMerge(entity);
                        return existingEntry;
                    }
                });
            }
        }

        // 2. 整合结果
        List<VisualItemEntry> finalResult = new ArrayList<>(unstackableList.size() + mergedMap.size());
        finalResult.addAll(unstackableList);
        finalResult.addAll(mergedMap.values());

        // 3. 排序
        finalResult.sort(VISUAL_COMPARATOR);
        return finalResult;
    }

    private static boolean shouldHide(ItemStack stack) {
        if (FilterWhitelist.INSTANCE.contains(stack)) return false;
        return stack.getRarity() == Rarity.COMMON
                && !stack.isEnchanted()
                && !Utils.shouldShowTooltip(stack);
    }

    /**
     * 内部辅助记录：用于 Map 的键，确保只有 Item 和 NBT 都相同的物品才会合并。
     */
    private static class MergeKey {
        private final Item item;
        private final CompoundTag tag;

        public MergeKey(ItemStack stack) {
            this.item = stack.getItem();
            this.tag = stack.getTag(); // 可能为 null
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MergeKey mergeKey = (MergeKey) o;
            // 比较 Item 对象引用
            if (item != mergeKey.item) return false;
            // 比较 NBT 内容 (CompoundTag 实现了 equals)
            return Objects.equals(tag, mergeKey.tag);
        }

        @Override
        public int hashCode() {
            // 注意：CompoundTag 在原版中 hashCode 实现比较慢或者不完善，
            // 但在客户端数据量较小的情况下是可以接受的。
            // 为了极致性能，对于没有 Tag 的物品（绝大多数），直接返回 Item 的 hash。
            if (tag == null) return item.hashCode();
            return 31 * item.hashCode() + tag.hashCode();
        }
    }
}

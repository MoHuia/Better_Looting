package com.mohuia.better_looting.client.filter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 过滤白名单管理器
 * <p>
 * 负责管理玩家选定的过滤物品，并将其持久化存储到 JSON 配置文件中。
 * 支持严格的 NBT 匹配。
 * </p>
 */
public class FilterWhitelist {
    public static final FilterWhitelist INSTANCE = new FilterWhitelist();
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // 使用自定义 Entry 类来存储 ID 和 NBT，确保 Set 去重逻辑正确
    private final Set<WhitelistEntry> entries = new LinkedHashSet<>();
    private Path configPath;

    /**
     * 初始化配置文件路径并加载数据
     */
    public void init() {
        Path configDir = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("better_looting");
        try {
            if (!Files.exists(configDir)) Files.createDirectories(configDir);
            this.configPath = configDir.resolve("whitelist.json");
            load();
        } catch (IOException e) {
            LOGGER.error("Failed to initialize filter whitelist", e);
        }
    }

    /**
     * 添加物品到白名单（包含 NBT 数据）
     * @param stack 要添加的物品栈
     */
    public void add(ItemStack stack) {
        if (stack.isEmpty()) return;

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null) {
            // 获取 NBT 字符串，如果没有 NBT 则为 null
            String nbtStr = stack.hasTag() ? stack.getTag().toString() : null;
            WhitelistEntry entry = new WhitelistEntry(id.toString(), nbtStr);

            if (entries.add(entry)) {
                save();
            }
        }
    }

    /**
     * 从白名单移除物品（必须 ID 和 NBT 均匹配）
     */
    public void remove(ItemStack stack) {
        if (stack.isEmpty()) return;

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null) {
            String nbtStr = stack.hasTag() ? stack.getTag().toString() : null;
            WhitelistEntry entry = new WhitelistEntry(id.toString(), nbtStr);

            if (entries.remove(entry)) {
                save();
            }
        }
    }

    /**
     * 清空所有白名单数据
     */
    public void clear() {
        if (entries.isEmpty()) return;
        entries.clear();
        save();
    }

    /**
     * 检查白名单是否包含该物品（严格比对 NBT）
     * @param stack 待检查物品
     * @return true 如果存在于白名单中
     */
    public boolean contains(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // 遍历检查
        for (WhitelistEntry entry : entries) {
            if (entry.matches(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取用于 GUI 显示的 ItemStack 列表。
     * <p>
     * 注意：此方法会为每个 Entry 重新创建 ItemStack 实例，建议仅在渲染循环外调用或缓存结果。
     * </p>
     * @return 包含 NBT 数据的物品集合
     */
    public Set<ItemStack> getDisplayItems() {
        Set<ItemStack> stacks = new LinkedHashSet<>();
        for (WhitelistEntry entry : entries) {
            ItemStack stack = entry.createStack();
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    private void save() {
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(entries, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save whitelist", e);
        }
    }

    private void load() {
        if (!Files.exists(configPath)) return;
        try (Reader reader = Files.newBufferedReader(configPath)) {
            Set<WhitelistEntry> loaded = GSON.fromJson(reader, new TypeToken<Set<WhitelistEntry>>(){}.getType());
            if (loaded != null) {
                entries.clear();
                entries.addAll(loaded);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load whitelist", e);
        }
    }

    // --- 内部数据类 ---

    /**
     * 序列化辅助类，用于 JSON 存储和哈希比对
     */
    public static class WhitelistEntry {
        public String id;
        public String nbt; // 存储 NBT 的字符串形式

        public WhitelistEntry() {}

        public WhitelistEntry(String id, String nbt) {
            this.id = id;
            this.nbt = nbt;
        }

        /**
         * 判断给定 ItemStack 是否与此条目匹配
         */
        public boolean matches(ItemStack stack) {
            ResourceLocation stackId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (stackId == null || !stackId.toString().equals(this.id)) {
                return false;
            }

            // NBT 比对逻辑
            CompoundTag stackTag = stack.getTag();
            if (this.nbt == null || this.nbt.isEmpty()) {
                // 白名单没 NBT -> 物品也不能有 NBT (或者为空)
                return stackTag == null || stackTag.isEmpty();
            } else {
                // 白名单有 NBT -> 物品必须有完全相同的 NBT
                if (stackTag == null) return false;
                return stackTag.toString().equals(this.nbt);
            }
        }

        /**
         * 根据 ID 和 NBT 字符串重建 ItemStack
         */
        public ItemStack createStack() {
            ResourceLocation loc = ResourceLocation.tryParse(id);
            if (loc == null || !ForgeRegistries.ITEMS.containsKey(loc)) return ItemStack.EMPTY;

            var item = ForgeRegistries.ITEMS.getValue(loc);
            if (item == null || item == Items.AIR) return ItemStack.EMPTY;

            ItemStack stack = new ItemStack(item);
            if (nbt != null && !nbt.isEmpty()) {
                try {
                    CompoundTag tag = TagParser.parseTag(nbt);
                    stack.setTag(tag);
                } catch (Exception e) {
                    LOGGER.error("Failed to parse NBT for whitelist item: " + id, e);
                }
            }
            return stack;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WhitelistEntry that = (WhitelistEntry) o;
            return Objects.equals(id, that.id) && Objects.equals(nbt, that.nbt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, nbt);
        }
    }
}

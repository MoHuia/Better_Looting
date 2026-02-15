package com.mohuia.better_looting.client.filter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public class FilterWhitelist {
    public static final FilterWhitelist INSTANCE = new FilterWhitelist();
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // 使用 LinkedHashSet 保持插入顺序
    private final Set<String> whitelistIds = new LinkedHashSet<>();
    private final Set<Item> itemCache = new LinkedHashSet<>();

    private Path configPath;

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

    public void add(Item item) {
        if (item == Items.AIR) return;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id != null) {
            whitelistIds.add(id.toString());
            itemCache.add(item);
            save();
        }
    }

    public void remove(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id != null) {
            whitelistIds.remove(id.toString());
            itemCache.remove(item);
            save();
        }
    }

    /**
     * 清空所有白名单
     */
    public void clear() {
        if (whitelistIds.isEmpty()) return;
        whitelistIds.clear();
        itemCache.clear();
        save();
    }

    public boolean contains(Item item) {
        return itemCache.contains(item);
    }

    public Set<Item> getItems() {
        return itemCache;
    }

    private void save() {
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(whitelistIds, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save whitelist", e);
        }
    }

    private void load() {
        if (!Files.exists(configPath)) return;
        try (Reader reader = Files.newBufferedReader(configPath)) {
            Set<String> loaded = GSON.fromJson(reader, new TypeToken<Set<String>>(){}.getType());
            if (loaded != null) {
                whitelistIds.clear();
                whitelistIds.addAll(loaded);
                rebuildCache();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load whitelist", e);
        }
    }

    private void rebuildCache() {
        itemCache.clear();
        for (String id : whitelistIds) {
            // [修复] 使用 tryParse 替代 new ResourceLocation(String)
            // tryParse 会处理异常格式并返回 null，而不是抛出异常
            ResourceLocation loc = ResourceLocation.tryParse(id);

            if (loc != null) {
                // 如果 ID 指向的物品在当前整合包中不存在 (比如删除了某个 Mod)，getValue 会返回 null 或 Air
                if (ForgeRegistries.ITEMS.containsKey(loc)) {
                    Item item = ForgeRegistries.ITEMS.getValue(loc);
                    if (item != null && item != Items.AIR) {
                        itemCache.add(item);
                    }
                }
            }
        }
    }
}

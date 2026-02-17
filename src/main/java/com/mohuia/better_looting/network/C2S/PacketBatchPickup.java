package com.mohuia.better_looting.network.C2S;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 批量拾取物品数据包 (C2S: Client to Server).
 * <p>
 * 客户端发送欲拾取的物品实体 ID 列表，服务端验证并执行拾取逻辑。
 * 包含针对大量物品拾取的性能优化（反射读取 pickupDelay）。
 */
public class PacketBatchPickup {
    private static final Logger LOGGER = LoggerFactory.getLogger(PacketBatchPickup.class);

    // =========================================
    //            反射缓存字段
    // =========================================

    private static Field PICKUP_DELAY_FIELD;
    private static boolean reflectionFailed = false;

    /*
     * 静态初始化块：预先查找 ItemEntity 的 pickupDelay 字段。
     * 相比于 NBT 序列化检查，反射读取 int 字段在大量物品场景下性能提升显著。
     */
    static {
        try {
            // 1. 尝试开发环境映射 (MojMap/Official)
            PICKUP_DELAY_FIELD = ItemEntity.class.getDeclaredField("pickupDelay");
            PICKUP_DELAY_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            try {
                // 2. 尝试生产环境混淆名 (SRG Name)
                // 注意：field_70292_b 是 1.20.1 对应的 SRG 字段名，若映射表更新需核对
                PICKUP_DELAY_FIELD = ItemEntity.class.getDeclaredField("field_70292_b");
                PICKUP_DELAY_FIELD.setAccessible(true);
            } catch (NoSuchFieldException ex) {
                LOGGER.error("BetterLooting: 无法通过反射找到 pickupDelay 字段，将回退到 NBT 检查模式。", ex);
                reflectionFailed = true;
            }
        }
    }

    // =========================================
    //            数据包结构
    // =========================================

    private final List<Integer> entityIds;
    private final boolean isAuto;         // 是否为自动拾取（非手动按键触发）
    private final boolean limitToMaxStack; // 是否限制单次拾取量（如仅拾取一组）

    public PacketBatchPickup(List<Integer> entityIds, boolean isAuto, boolean limitToMaxStack) {
        this.entityIds = entityIds;
        this.isAuto = isAuto;
        this.limitToMaxStack = limitToMaxStack;
    }

    /**
     * 解码构造函数 (从 ByteBuf 读取数据).
     */
    public PacketBatchPickup(FriendlyByteBuf buf) {
        this.isAuto = buf.readBoolean();
        this.limitToMaxStack = buf.readBoolean();
        int count = buf.readVarInt();
        this.entityIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.entityIds.add(buf.readInt());
        }
    }

    /**
     * 编码方法 (写入数据到 ByteBuf).
     */
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(isAuto);
        buf.writeBoolean(limitToMaxStack);
        buf.writeVarInt(entityIds.size());
        for (int id : entityIds) {
            buf.writeInt(id);
        }
    }

    // =========================================
    //            业务处理逻辑
    // =========================================

    /**
     * 处理网络包逻辑 (在服务端主线程执行).
     */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        // enqueueWork 确保代码在服务端主线程运行，而非网络 IO 线程，防止并发修改世界数据导致崩溃
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            int remainingQuota = limitToMaxStack ? 64 : Integer.MAX_VALUE;
            boolean anySuccess = false;
            boolean anyFull = false;

            for (int entityId : entityIds) {
                if (remainingQuota <= 0) break;

                Entity target = player.level().getEntity(entityId);

                // 验证：目标必须是物品实体、存活且在玩家 8 格范围内 (8^2 = 64)
                if (target instanceof ItemEntity itemEntity && itemEntity.isAlive() && player.distanceToSqr(target) < 64.0) {

                    // 检查物品是否处于“捡起冷却”状态
                    if (!canPickup(itemEntity)) {
                        continue;
                    }

                    ItemStack groundStack = itemEntity.getItem();
                    int amountToTake = Math.min(groundStack.getCount(), remainingQuota);

                    // 模拟拾取：复制物品栈并尝试加入玩家背包
                    ItemStack stackToPickup = groundStack.copy();
                    stackToPickup.setCount(amountToTake);

                    if (player.getInventory().add(stackToPickup)) {
                        anySuccess = true;

                        // 计算实际进入背包的数量（以防背包只能装下一部分）
                        int actuallyPickedUp = amountToTake - stackToPickup.getCount();
                        remainingQuota -= actuallyPickedUp;

                        // 更新服务端数据：触发捡起统计、扣除地面物品数量
                        player.take(itemEntity, actuallyPickedUp);
                        groundStack.shrink(actuallyPickedUp);

                        if (groundStack.isEmpty()) {
                            itemEntity.discard(); // 物品被捡完，移除实体
                        } else {
                            itemEntity.setItem(groundStack); // 更新剩余数量
                            // 如果还有剩余没捡起来（说明背包满了），标记状态
                            if (!stackToPickup.isEmpty()) anyFull = true;
                        }
                    } else {
                        anyFull = true; // 添加失败，背包已满
                    }
                }
            }

            // 播放音效与提示
            if (anySuccess) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F,
                        ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);
            }

            // 如果背包满且是手动操作，给予玩家提示
            if (anyFull && !isAuto) {
                if (!anySuccess) {
                    player.playNotifySound(SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5f, 1.2f);
                }
                player.displayClientMessage(
                        Component.translatable("message.better_looting.inventory_full").withStyle(ChatFormatting.RED),
                        true
                );
            }
        });

        ctx.get().setPacketHandled(true);
    }

    /**
     * 检查物品是否可以被拾取 (PickupDelay <= 0).
     * 优先使用反射直接读取字段，失败则回退到 NBT。
     */
    private boolean canPickup(ItemEntity itemEntity) {
        if (reflectionFailed || PICKUP_DELAY_FIELD == null) {
            // Fallback: 仅当反射初始化失败时，才执行较昂贵的 NBT 读写
            CompoundTag tag = new CompoundTag();
            itemEntity.saveWithoutId(tag);
            return tag.getShort("PickupDelay") <= 0;
        }

        try {
            // Fast Path: 直接内存读取 int
            int delay = PICKUP_DELAY_FIELD.getInt(itemEntity);
            return delay <= 0;
        } catch (IllegalAccessException e) {
            // 理论上 setAccessible(true) 后不会发生，但若发生则拒绝拾取以保安全
            return false;
        }
    }
}

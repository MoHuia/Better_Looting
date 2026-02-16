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
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 统一拾取数据包 (Client -> Server)。
 * <p>
 * 优化版：使用反射缓存替代 NBT 序列化来检查拾取延迟，极大提升大量物品时的性能。
 */
public class PacketBatchPickup {
    private static final Logger LOGGER = LoggerFactory.getLogger(PacketBatchPickup.class);

    // --- 反射缓存 ---
    private static Field PICKUP_DELAY_FIELD;
    private static boolean reflectionFailed = false;

    static {
        try {
            // 尝试获取 pickupDelay 字段 (适应 MojMap 映射)
            // 如果是 SRG 环境，可能需要改为 "field_70292_b"
            // 为了稳健，可以尝试遍历查找 int 类型的字段，但这里先假定是官方映射
            PICKUP_DELAY_FIELD = ItemEntity.class.getDeclaredField("pickupDelay");
            PICKUP_DELAY_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            try {
                // 备用：尝试 SRG 常用名 (如果开发环境与生产环境映射不同)
                PICKUP_DELAY_FIELD = ItemEntity.class.getDeclaredField("field_70292_b");
                PICKUP_DELAY_FIELD.setAccessible(true);
            } catch (NoSuchFieldException ex) {
                LOGGER.error("BetterLooting: Failed to find pickupDelay field via reflection.", ex);
                reflectionFailed = true;
            }
        }
    }

    private final List<Integer> entityIds;
    private final boolean isAuto;
    private final boolean limitToMaxStack;

    public PacketBatchPickup(List<Integer> entityIds, boolean isAuto, boolean limitToMaxStack) {
        this.entityIds = entityIds;
        this.isAuto = isAuto;
        this.limitToMaxStack = limitToMaxStack;
    }

    public PacketBatchPickup(FriendlyByteBuf buf) {
        this.isAuto = buf.readBoolean();
        this.limitToMaxStack = buf.readBoolean();
        int count = buf.readVarInt();
        this.entityIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.entityIds.add(buf.readInt());
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(isAuto);
        buf.writeBoolean(limitToMaxStack);
        buf.writeVarInt(entityIds.size());
        for (int id : entityIds) {
            buf.writeInt(id);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            int remainingQuota = limitToMaxStack ? 64 : Integer.MAX_VALUE;
            boolean anySuccess = false;
            boolean anyFull = false;

            for (int entityId : entityIds) {
                if (remainingQuota <= 0) break;

                Entity target = player.level().getEntity(entityId);

                // 距离与类型检查
                if (target instanceof ItemEntity itemEntity && itemEntity.isAlive() && player.distanceToSqr(target) < 64.0) {

                    // --- 优化：通过反射检查 PickupDelay ---
                    if (!canPickup(itemEntity)) {
                        continue;
                    }

                    ItemStack groundStack = itemEntity.getItem();
                    int amountToTake = Math.min(groundStack.getCount(), remainingQuota);

                    ItemStack stackToPickup = groundStack.copy();
                    stackToPickup.setCount(amountToTake);

                    if (player.getInventory().add(stackToPickup)) {
                        anySuccess = true;
                        int actuallyPickedUp = amountToTake - stackToPickup.getCount();
                        remainingQuota -= actuallyPickedUp;

                        player.take(itemEntity, actuallyPickedUp);
                        groundStack.shrink(actuallyPickedUp);

                        if (groundStack.isEmpty()) {
                            itemEntity.discard();
                        } else {
                            itemEntity.setItem(groundStack);
                            if (!stackToPickup.isEmpty()) anyFull = true;
                        }
                    } else {
                        anyFull = true;
                    }
                }
            }

            if (anySuccess) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F,
                        ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);
            }

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
     * 辅助方法：检查物品是否可以被拾取 (冷却时间归零)
     */
    private boolean canPickup(ItemEntity itemEntity) {
        // 如果反射初始化失败，为了安全起见，禁止快速拾取，或者退化为 NBT 检查(这里选择简单的放行或禁止，根据你的需求)
        // 建议：如果反射坏了，就默认允许拾取，或者在此处退化为 NBT 检查。
        if (reflectionFailed || PICKUP_DELAY_FIELD == null) {
            // 退化逻辑 (Fallback): 仅当反射失败时才执行昂贵的 NBT 检查
            CompoundTag tag = new CompoundTag();
            itemEntity.saveWithoutId(tag);
            return tag.getShort("PickupDelay") <= 0;
        }

        try {
            // 极速检查：直接读取 int 字段
            int delay = PICKUP_DELAY_FIELD.getInt(itemEntity);
            return delay <= 0;
        } catch (IllegalAccessException e) {
            return false;
        }
    }
}

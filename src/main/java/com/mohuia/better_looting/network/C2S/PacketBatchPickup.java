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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 批量拾取数据包 (Client -> Server)。
 * <p>
 * 用于处理玩家一次性拾取多个物品的请求（手动按键或自动吸附）。
 */
public class PacketBatchPickup {
    private final List<Integer> entityIds;
    private final boolean isAuto; // 标记是否为自动触发（影响反馈音效和提示）

    // --- 构造函数 ---

    public PacketBatchPickup(List<Integer> entityIds) {
        this(entityIds, false);
    }

    public PacketBatchPickup(List<Integer> entityIds, boolean isAuto) {
        this.entityIds = entityIds;
        this.isAuto = isAuto;
    }

    /** 从 ByteBuf 解码 (服务端接收时调用) */
    public PacketBatchPickup(FriendlyByteBuf buf) {
        this.isAuto = buf.readBoolean();
        int count = buf.readVarInt();
        this.entityIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.entityIds.add(buf.readInt());
        }
    }

    /** 编码到 ByteBuf (客户端发送时调用) */
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(isAuto);
        buf.writeVarInt(entityIds.size());
        for (int id : entityIds) {
            buf.writeInt(id);
        }
    }

    /**
     * 处理包逻辑。
     * <p>
     * <b>重要：</b> Netty 网络线程不能直接操作游戏世界（会导致并发崩溃）。
     * 必须使用 {@code ctx.enqueueWork} 将任务调度到服务器主线程执行。
     */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            boolean anySuccess = false;
            boolean anyFull = false;

            for (int entityId : entityIds) {
                Entity target = player.level().getEntity(entityId);

                // 安全检查：确认实体存在、是掉落物、且距离足够近（防作弊/延迟容错）
                if (target instanceof ItemEntity itemEntity && itemEntity.isAlive() && player.distanceToSqr(target) < 64.0) {

                    // 检查 PickupDelay (例如玩家刚扔出去的物品不能马上捡回来)
                    CompoundTag tag = new CompoundTag();
                    itemEntity.saveWithoutId(tag);
                    if (tag.getShort("PickupDelay") > 0) {
                        continue;
                    }

                    ItemStack stack = itemEntity.getItem().copy();
                    int originalCount = stack.getCount();

                    // 尝试将物品加入玩家背包
                    if (player.getInventory().add(stack)) {
                        anySuccess = true;

                        // 扣除掉落物实体中的数量
                        // 如果 stack.getCount() > 0，说明背包满了只捡了一部分
                        player.take(itemEntity, originalCount - stack.getCount());

                        if (stack.isEmpty()) {
                            itemEntity.discard(); // 全部捡完，删除实体
                        } else {
                            itemEntity.getItem().setCount(stack.getCount()); // 更新剩余数量
                            anyFull = true; // 标记背包满了
                        }
                    } else {
                        anyFull = true; // 完全捡不起来
                    }
                }
            }

            // --- 反馈处理 ---

            if (anySuccess) {
                // 播放随机音调的 "啵" 声
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F,
                        ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);
            }

            if (anyFull) {
                // 如果是自动拾取模式，背包满时不报错（防止刷屏干扰）
                // 只有手动按键时才提示 "Inventory Full"
                if (!isAuto) {
                    if (!anySuccess) {
                        player.playNotifySound(SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5f, 1.2f);
                    }
                    sendInventoryFullMessage(player);
                }
            }
        });

        ctx.get().setPacketHandled(true);
    }

    private void sendInventoryFullMessage(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable("message.better_looting.inventory_full").withStyle(ChatFormatting.RED),
                true // true = 显示在操作栏 (Action Bar)，false = 显示在聊天框
        );
    }
}

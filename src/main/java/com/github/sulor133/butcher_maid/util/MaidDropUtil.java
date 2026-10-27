package com.github.sulor133.butcher_maid.util;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/** 把刚掉出来的产物装进女仆可用背包，装不下的留在地上。 */
public class MaidDropUtil {
    //只捡最近这么多 tick 内掉出来的东西，玩家丢在旁边的杂物不碰
    private static final int FRESH_DROP_TICKS = 5;
    private static final double COLLECT_RADIUS = 3.0D;

    private MaidDropUtil() {
    }

    public static void collectFreshDrops(ServerLevel level, EntityMaid maid, BlockEntity station) {
        collectFreshDrops(level, maid, new AABB(station.getBlockPos()).inflate(COLLECT_RADIUS));
    }

    public static void collectFreshDrops(ServerLevel level, EntityMaid maid, Entity source) {
        collectFreshDrops(level, maid, source.getBoundingBox().inflate(COLLECT_RADIUS));
    }

    public static void collectFreshDrops(ServerLevel level, EntityMaid maid, AABB box) {
        //背包优先：产物别往主手塞，否则会把刚换好的工具挤掉
        IItemHandler inventory = maid.getAvailableInv(false);
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (!drop.isAlive() || drop.getAge() > FRESH_DROP_TICKS) {
                continue;
            }
            //先并同类格子，再找空格，符合原生车万女仆写法
            ItemStack remaining = ItemHandlerHelper.insertItemStacked(inventory, drop.getItem(), false);
            if (remaining.isEmpty()) {
                drop.discard();
            } else {
                drop.setItem(remaining);
            }
        }
    }
}

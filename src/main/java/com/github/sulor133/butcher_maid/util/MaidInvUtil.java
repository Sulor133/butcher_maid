package com.github.sulor133.butcher_maid.util;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** 女仆背包的通用操作：找槽、取件塞进容器、换主手工具。 */
public class MaidInvUtil {
    private MaidInvUtil() {
    }

    /** 可用背包里第一个满足条件的槽位，没有则 -1；空槽一律跳过 */
    public static int findSlot(EntityMaid maid, Predicate<ItemStack> filter) {
        IItemHandler inventory = maid.getAvailableInv(true);
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty() && filter.test(stack)) {
                return i;
            }
        }
        return -1;
    }

    /** 从指定槽取最多 count 件塞进去，塞不下的余量原位放回；返回实际塞进去的数量 */
    public static int insertUpTo(EntityMaid maid, int slot, int count, UnaryOperator<ItemStack> inserter) {
        if (slot < 0) {
            return 0;
        }
        IItemHandler inventory = maid.getAvailableInv(true);
        ItemStack taken = inventory.extractItem(slot, count, false);
        if (taken.isEmpty()) {
            return 0;
        }
        int before = taken.getCount();
        //inserter 接收要放进容器的栈，返回没能放进去的余量
        ItemStack left = inserter.apply(taken);
        if (!left.isEmpty()) {
            inventory.insertItem(slot, left, false);
        }
        return before - left.getCount();
    }

    /** 只取一件塞进去；返回是否塞成功 */
    public static boolean insertOne(EntityMaid maid, int slot, UnaryOperator<ItemStack> inserter) {
        return insertUpTo(maid, slot, 1, inserter) > 0;
    }

    /** 把整摞都塞进去；返回是否有任何一件被收下 */
    public static boolean insertStack(EntityMaid maid, int slot, UnaryOperator<ItemStack> inserter) {
        if (slot < 0) {
            return false;
        }
        int count = maid.getAvailableInv(true).getStackInSlot(slot).getCount();
        return insertUpTo(maid, slot, count, inserter) > 0;
    }

    /**
     * 把当前工序要的工具拿到主手：手里已经是就不动，否则和背包里直接对调
     * （换下来的回背包，不会掉地上）。
     */
    public static boolean holdTool(EntityMaid maid, Ingredient tool, int needCount) {
        ItemStack main = maid.getMainHandItem();
        if (matches(tool, main, needCount)) {
            return true;
        }
        //必须用 getMaidInv()：getAvailableInv() 是 CombinedInvWrapper，没有 setStackInSlot
        ItemStackHandler backpack = maid.getMaidInv();
        for (int i = 0; i < backpack.getSlots(); i++) {
            ItemStack candidate = backpack.getStackInSlot(i);
            if (!matches(tool, candidate, needCount)) {
                continue;
            }
            backpack.setStackInSlot(i, main.isEmpty() ? ItemStack.EMPTY : main);
            maid.setItemInHand(InteractionHand.MAIN_HAND, candidate);
            return true;
        }
        return false;           //缺少工具不干活
    }

    private static boolean matches(Ingredient tool, ItemStack stack, int needCount) {
        return !stack.isEmpty() && stack.getCount() >= needCount && tool.test(stack);
    }

    /**
     * 这个容器收不收这件物品。
     * 案板 / 肉钩的 ITEM_HANDLER 只对非 DOWN 面暴露，传 Direction.DOWN 会拿到空 Optional。
     */
    public static boolean canInsert(BlockEntity station, ItemStack stack) {
        return station.getCapability(ForgeCapabilities.ITEM_HANDLER, (Direction) null)
                .map(handler -> handler.isItemValid(0, stack))
                .orElse(false);
    }
}

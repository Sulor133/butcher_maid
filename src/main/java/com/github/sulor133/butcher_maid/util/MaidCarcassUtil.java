package com.github.sulor133.butcher_maid.util;

import com.github.sulor133.butcher_maid.tag.MaidButcherTags;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

public class MaidCarcassUtil {
    private MaidCarcassUtil() {
    }

    //标签判断胴体
    public static boolean isCarcass(ItemStack itemStack) {
        return !itemStack.isEmpty() && itemStack.is(MaidButcherTags.CARCASSES);
    }

    //背包胴体总数
    public static int count(EntityMaid maid) {
        IItemHandler inventory = maid.getAvailableInv(true);
        int count = 0;
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (isCarcass(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    //第一个胴体所在槽位，没有则-1
    public static int firstSlot(EntityMaid maid) {
        IItemHandler inventory = maid.getAvailableInv(true);
        for (int i = 0; i < inventory.getSlots(); i++){
            if (isCarcass(inventory.getStackInSlot(i))){
                return i;
            }
        }
        return -1;
    }

    //没有空格即视为满载，获得胴体任务终止
    public static boolean isFull(EntityMaid maid) {
        IItemHandler inventory = maid.getAvailableInv(true);
        for (int i = 0; i < inventory.getSlots(); i++){
            if (inventory.getStackInSlot(i).isEmpty()){
                return false;
            }
        }
        return true;
    }
}



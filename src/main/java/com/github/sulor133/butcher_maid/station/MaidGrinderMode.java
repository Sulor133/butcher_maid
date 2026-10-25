package com.github.sulor133.butcher_maid.station;

import com.lance5057.butchercraft.ButchercraftItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * 绞肉机的模式由配件决定：grinder_tip 磨绞肉，extruder_tip 灌香肠。
 * 配件槽只放得下一个，所以同一台机器只能有一种模式。
 */
public enum MaidGrinderMode {
    //碎肉->绞肉,一轮5次
    GRIND(ButchercraftItems.GRINDER_TIP, false),
    //绞肉+肠衣->香肠,一轮17次
    EXTRUDE(ButchercraftItems.EXTRUDER_TIP, true);

    private final Supplier<? extends Item> tip;

    //灌肠必须往肠衣槽放东西
    public final boolean needsCasing;

    MaidGrinderMode(Supplier<? extends Item> tip,boolean needsCasing) {
        this.tip = tip;
        this.needsCasing = needsCasing;
    }

    //任务图标
    public ItemStack createTip() {
        return new ItemStack(this.tip.get());
    }

    public boolean isTip(ItemStack stack) {
        return !stack.isEmpty() && stack.is(this.tip.get());
    }
}

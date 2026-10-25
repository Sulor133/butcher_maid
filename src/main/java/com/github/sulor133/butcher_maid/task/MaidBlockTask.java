package com.github.sulor133.butcher_maid.task;

import com.github.sulor133.butcher_maid.behavior.MaidBlockWorkBehavior;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.mojang.datafixers.util.Pair;
import com.google.common.collect.Lists;
import com.lance5057.butchercraft.ButchercraftItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;



import java.util.List;

/** 案板切配：把整块肉、动物头、连串香肠切成成品 */
public class MaidBlockTask implements IMaidTask {
    public static final ResourceLocation UID = ResourceLocation.tryBuild("butcher_maid","board");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    //任务图标
    @Override
    public ItemStack getIcon() {
        return new ItemStack(ButchercraftItems.BUTCHER_BLOCK_BLOCK_ITEM.get());
    }

    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return InitSounds.MAID_ATTACK.get();
    }

    //工作站任务
    @Override
    public boolean workPointTask(EntityMaid maid) {
        return true;
    }

    @Override
    public boolean enableLookAndRandomWalk(EntityMaid maid) {
        return false;
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        BehaviorControl<? super EntityMaid> board = new MaidBlockWorkBehavior();
        return Lists.newArrayList(Pair.of(5, board));
    }
}

package com.github.sulor133.butcher_maid.task;

import com.github.sulor133.butcher_maid.behavior.MaidHookWorkBehavior;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.google.common.collect.Lists;
import com.lance5057.butchercraft.ButchercraftItems;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** 挂肉钩：守着肉钩把胴体做成成品肉（放血 -> 剥皮 -> 锯骨 -> 取脏 -> 屠宰） */
public class MaidHookTask implements IMaidTask {
    public static final ResourceLocation UID = ResourceLocation.tryBuild("butcher_maid", "hook");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    //任务图标
    @Override
    public ItemStack getIcon() {
        return new ItemStack(ButchercraftItems.HOOK_BLOCK_ITEM.get());
    }

    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return InitSounds.MAID_ATTACK.get();
    }

    //守着工作站干活，属于工作点任务
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
        BehaviorControl<? super EntityMaid> hook = new MaidHookWorkBehavior();
        return Lists.newArrayList(Pair.of(5,hook));
    }
}

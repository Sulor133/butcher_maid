package com.github.sulor133.butcher_maid.task;

import com.github.sulor133.butcher_maid.behavior.MaidGrinderWorkBehavior;
import com.github.sulor133.butcher_maid.station.MaidGrinderMode;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** 绞肉：把碎肉放进绞肉机磨成绞肉 */
public class MaidGrinderTask implements IMaidTask {
    public static final ResourceLocation UID = ResourceLocation.tryBuild("butcher_maid", "grind");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    //任务图标
    @Override
    public ItemStack getIcon() {
        return MaidGrinderMode.GRIND.createTip();
    }

    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return InitSounds.MAID_ATTACK.get();
    }

    //属于工作点任务
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
        BehaviorControl<? super EntityMaid> grind = new MaidGrinderWorkBehavior(MaidGrinderMode.GRIND);
        return Lists.newArrayList(Pair.of(5, grind));
    }

}

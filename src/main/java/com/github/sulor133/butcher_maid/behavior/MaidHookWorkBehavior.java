package com.github.sulor133.butcher_maid.behavior;

import com.github.sulor133.butcher_maid.station.MaidHookWork;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.lance5057.butchercraft.workstations.bases.recipes.AnimatedRecipeItemUse;
import com.lance5057.butchercraft.workstations.hook.MeatHookBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;

import java.util.Optional;

/** 挂肉钩：挂胴体 -> 按工序换工具 -> 一步一步获得成品 */
public class MaidHookWorkBehavior extends MaidStationBehavior<MeatHookBlockEntity> {

    public MaidHookWorkBehavior() {
        //肉钩挂上胴体后碰撞箱会往下扩两格，扫描范围要比另两个高
        super(MeatHookBlockEntity.class, 5);
    }

    //空钩子要有胴体可挂，有胴体要还有工序可做
    @Override
    protected MeatHookBlockEntity findStation(ServerLevel level, EntityMaid maid) {
        return findNearestStation(level, maid,
                be -> be.isEmpty() ? MaidHookWork.hasCarcass(be, maid) : MaidHookWork.canWork(be));
    }

    //钩子空了就挂下一只胴体
    @Override
    protected boolean canContinue(MeatHookBlockEntity hook, EntityMaid maid) {
        return MaidHookWork.canWork(hook) || MaidHookWork.hasCarcass(hook, maid);
    }

    @Override
    protected void work(ServerLevel level, EntityMaid maid, MeatHookBlockEntity hook) {
        //空钩子：先挂胴体
        if (hook.isEmpty()) {
            if (MaidHookWork.insertCarcass(hook, maid)) {
                resetWorkCooldown();
            }
            return;
        }

        //有胴体：按当前工序把工具拿到手上
        Optional<AnimatedRecipeItemUse> current = hook.getCurrentTool();
        if (current.isEmpty()) {
            return;                     //没配方
        }
        int needCount = Math.max(1, hook.toolCount);
        if (!MaidHookWork.holdTool(maid, current.get().tool, needCount)) {
            return;                     //缺工具
        }

        if (!readyToWork()) {
            return;
        }
        maid.swing(InteractionHand.MAIN_HAND);
        if (MaidHookWork.butcher(level, maid, hook)) {
            MaidHookWork.collectDrops(level, maid, hook);
        }
    }
}

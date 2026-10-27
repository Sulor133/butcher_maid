package com.github.sulor133.butcher_maid.behavior;

import com.github.sulor133.butcher_maid.station.MaidBlockWork;
import com.github.sulor133.butcher_maid.station.MaidGrinderWork;
import com.github.sulor133.butcher_maid.station.MaidHookWork;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.lance5057.butchercraft.workstations.bases.recipes.AnimatedRecipeItemUse;
import com.lance5057.butchercraft.workstations.butcherblock.ButcherBlockBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;

import java.util.Optional;

/** 案板切配：放料 -> 换工具 -> 一下一下切 -> 收产物 */
public class MaidBlockWorkBehavior extends MaidStationBehavior<ButcherBlockBlockEntity> {

    public MaidBlockWorkBehavior() {
        super(ButcherBlockBlockEntity.class, 4);
    }

    //空案板要有料可放，有料要还有工序可做，且没被别人占着
    @Override
    protected ButcherBlockBlockEntity findStation(ServerLevel level, EntityMaid maid) {
        return findNearestStation(level, maid,
                be -> be.isEmpty()
                        ? MaidBlockWork.hasInput(be, maid) && MaidBlockWork.isAvailable(be, maid)
                        : MaidBlockWork.canWork(be, maid),
                be -> MaidBlockWork.isMine(be, maid));
    }

    @Override
    protected boolean canContinue(ButcherBlockBlockEntity block, EntityMaid maid) {
        //被别人占了就撒手；自己是 owner 时 isAvailable 恒 true，不会误伤
        if (!MaidBlockWork.isAvailable(block, maid)) {
            return false;
        }
        //案板空了就放下一份料
        return MaidBlockWork.hasWork(block) || MaidBlockWork.hasInput(block, maid);
    }

    @Override
    protected boolean claimStation(ButcherBlockBlockEntity block, EntityMaid maid) {
        return MaidBlockWork.claim(block, maid);
    }

    //走人就把锁放掉，别人不用等 10 秒
    @Override
    protected void releaseStation(ButcherBlockBlockEntity block, EntityMaid maid) {
        MaidBlockWork.release(block, maid);
    }

    @Override
    protected void work(ServerLevel level, EntityMaid maid, ButcherBlockBlockEntity block) {
        //空案板先放一份料
        if (block.isEmpty()) {
            if (MaidBlockWork.insertInput(block, maid)) {
                MaidBlockWork.touch(block);         //放料成功也算一次有效动作
                resetWorkCooldown();
            }
            return;
        }

        //有料时按当前工序把工具拿到手上（count = 每刀消耗几个，不能用 toolCount，它不落盘）
        Optional<AnimatedRecipeItemUse> current = MaidBlockWork.currentTool(block);
        if (current.isEmpty()) {
            return;                     //没配方
        }
        int needCount = Math.max(1, current.get().count);
        if (!MaidHookWork.holdTool(maid, current.get().tool, needCount)) {
            return;                     //缺工具
        }

        if (!readyToWork()) {
            return;
        }
        maid.swing(InteractionHand.MAIN_HAND);
        if (MaidBlockWork.butcher(level, maid, block)) {
            MaidBlockWork.touch(block);                         //每次真的推进就续租
            MaidGrinderWork.collectDrops(level, maid, block.getBlockPos());
        }
    }
}

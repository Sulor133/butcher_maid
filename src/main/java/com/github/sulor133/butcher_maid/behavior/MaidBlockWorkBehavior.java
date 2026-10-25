package com.github.sulor133.butcher_maid.behavior;

import com.github.sulor133.butcher_maid.station.MaidBlockWork;
import com.github.sulor133.butcher_maid.station.MaidGrinderWork;
import com.github.sulor133.butcher_maid.station.MaidHookWork;
import com.github.sulor133.butcher_maid.util.MaidCarcassUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.lance5057.butchercraft.workstations.bases.recipes.AnimatedRecipeItemUse;
import com.lance5057.butchercraft.workstations.butcherblock.ButcherBlockBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;

import java.util.Map;
import java.util.Optional;

public class MaidBlockWorkBehavior extends Behavior<EntityMaid> {
    //八格内找案板
    private static final double SEARCH_RADIUS = 8.0D;
    private static final int VERTICAL_RANGE = 4;
    private static final double REACH_SQR = 9.0D;
    private static final int REPATH_INTERVAL = 10;
    //空闲时10tick扫描一次案板
    private static final int SCAN_INTERVAL = 10;
    //每10tick切一下
    private static final int WORK_INTERVAL = 10;

    private ButcherBlockBlockEntity block;
    private int repathCooldown;
    private int workCooldown;
    private int nextScanTick;

    public MaidBlockWorkBehavior() {
        super(Map.of());
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (maid.tickCount < this.nextScanTick) {
            return false;
        }
        this.nextScanTick = maid.tickCount + SCAN_INTERVAL;
        //背包满了不工作
        if (MaidCarcassUtil.isFull(maid)) {
            return false;
        }
        this.block = findBlock(level, maid);
        if (this.block == null) {
            return false;
        }
        //扫描阶段只读，选定后才占用
        if (!MaidBlockWork.claim(this.block, maid)) {
            this.block = null;
            return false;
        }
        this.workCooldown = 0;
        return true;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        //切到一半背包满了停止
        if (MaidCarcassUtil.isFull(maid)) {
            return false;
        }
        if (this.block == null || level.getBlockEntity(this.block.getBlockPos()) != this.block) {
            return false;           //案板被拆
        }
        //被其他酒狐占用时停止
        if (!MaidBlockWork.isAvailable(this.block, maid)) {
            return false;
        }
        //案板空了就放下一份料
        return MaidBlockWork.hasWork(this.block) || MaidBlockWork.hasInput(this.block, maid);
    }

    @Override
    protected void start(ServerLevel level,EntityMaid maid, long gameTime) {
        walkToBlock(maid);
        this.repathCooldown = 0;
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        if (this.block == null) {
            return;
        }
        BlockPos pos = this.block.getBlockPos();
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        maid.getLookControl().setLookAt(x, y, z, 30.0F, 30.0F);

        if (maid.distanceToSqr(x, y, z) > REACH_SQR) {
            if (--this.repathCooldown <= 0) {
                this.repathCooldown = REPATH_INTERVAL;
                walkToBlock(maid);
            }
            return;
        }
        maid.getNavigation().stop();

        //空案板先放一份料
        if (this.block.isEmpty()) {
            if (MaidBlockWork.insertInput(this.block, maid)) {
                MaidBlockWork.touch(this.block);
                this.workCooldown = 0;
            }
            return;
        }

        //有料时按当前工序把工具拿到手上
        Optional<AnimatedRecipeItemUse> current = MaidBlockWork.currentTool(this.block);
        if (current.isEmpty()) {
            return;         //没配方
        }
        int needCount = Math.max(1, current.get().count);
        if (!MaidHookWork.holdTool(maid, current.get().tool, needCount)) {
            return;         //缺工具
        }
        if (--this.workCooldown > 0) {
            return;
        }
        this.workCooldown = WORK_INTERVAL;

        maid.swing(InteractionHand.MAIN_HAND);
        if (MaidBlockWork.butcher(level, maid, this.block)) {
            MaidBlockWork.touch(this.block);                    //每次操作就刷新占用时间
            MaidGrinderWork.collectDrops(level, maid, pos);     //产物锚点就是方块本身
        }
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        maid.getNavigation().stop();
        if (this.block != null) {
            MaidBlockWork.release(this.block, maid);        //离开时解除占用
        }
        this.block = null;
    }

    //就近取案板：八格内、能走到旁边、并且现在有活可干
    private ButcherBlockBlockEntity findBlock(ServerLevel level, EntityMaid maid) {
        BlockPos origin = maid.blockPosition();
        BlockPos min = origin.offset((int) -SEARCH_RADIUS, -VERTICAL_RANGE, (int) -SEARCH_RADIUS);
        BlockPos max = origin.offset((int) SEARCH_RADIUS, VERTICAL_RANGE, (int) SEARCH_RADIUS);

        ButcherBlockBlockEntity best = null;
        double bestWeight = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!(level.getBlockEntity(pos) instanceof ButcherBlockBlockEntity be) || be.isRemoved()) {
                continue;
            }
            double distance = maid.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            if (distance > REACH_SQR && standPosNear(maid, pos) == null) {
                continue;               //无法到达案板且附近无法站立
            }
            //空案板要有料可放，有料要还有工序可做
            boolean usable = be.isEmpty()
                    ? MaidBlockWork.hasInput(be, maid) && MaidBlockWork.isAvailable(be, maid)
                    : MaidBlockWork.canWork(be, maid);
            if (!usable) {
                continue;
            }
            //优先使用已占用的案板
            double weight = MaidBlockWork.isMine(be, maid) ? distance * 0.5D : distance;
            if (weight < bestWeight) {
                bestWeight = weight;
                best = be;
            }

        }
        return best;
    }

    //案板是实心方块,只能到达附近
    private static BlockPos standPosNear(EntityMaid maid, BlockPos blockPos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = blockPos.relative(direction);
            if (maid.canPathReach(side)) {
                return side;
            }
        }
        if (maid.canPathReach(blockPos.below())) {
            return blockPos.below();
        }
        if (maid.canPathReach(blockPos.above())) {
            return blockPos.above();
        }
        return null;
    }

    private void walkToBlock(EntityMaid maid) {
        if (this.block == null) {
            return;
        }
        BlockPos target = standPosNear(maid, this.block.getBlockPos());
        if (target == null) {
            target = this.block.getBlockPos();              //旁边都去不了,对着方块本体再试一次
        }
        maid.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D,1.0D);
    }
}

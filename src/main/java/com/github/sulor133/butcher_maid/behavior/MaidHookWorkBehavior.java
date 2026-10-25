package com.github.sulor133.butcher_maid.behavior;

import com.github.sulor133.butcher_maid.station.MaidHookWork;
import com.github.sulor133.butcher_maid.util.MaidCarcassUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.lance5057.butchercraft.workstations.bases.recipes.AnimatedRecipeItemUse;
import com.lance5057.butchercraft.workstations.hook.MeatHookBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;

import java.util.Map;
import java.util.Optional;

/** 挂肉钩：找钩子 -> 走过去 -> 挂胴体 -> 换工具 -> 一步一步获得成品 */
public class MaidHookWorkBehavior extends Behavior<EntityMaid> {

    //八格内找肉钩
    private static final double SEARCH_RADIUS = 8.0D;
    private static final int VERTICAL_RANGE = 5;
    private static final double REACH_SQR = 9.0D;
    private static final int REPATH_INTERVAL = 10;
    //空闲时10tick扫描一次肉钩
    private static final int SCAN_INTERVAL = 10;
    private static final int WORK_INTERVAL = 10;

    private MeatHookBlockEntity hook;
    private int repathCooldown;
    private int workCooldown;
    private int nextScanTick;

    public MaidHookWorkBehavior() {
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

        this.hook = findHook(level, maid);
        if (this.hook == null) {
            return false;
        }

        this.workCooldown = 0;
        return true;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
    //砍到一半背包满了收手，钩子上的半成品留着
    if (MaidCarcassUtil.isFull(maid)) {
        return false;
    }
    if (this.hook == null || level.getBlockEntity(this.hook.getBlockPos()) != this.hook) {
        return false;           //钩子被拆了
    }
    //钩子空了就挂下一只胴体
    return MaidHookWork.canWork(this.hook) || MaidHookWork.hasCarcass(this.hook, maid);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gametime) {
        walkToHook(maid);
        this.repathCooldown = 0;
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        if (this.hook == null) {
            return;
        }
        BlockPos pos = this.hook.getBlockPos();
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        maid.getLookControl().setLookAt(x,y,z, 30.0F, 30.0F);

        if (maid.distanceToSqr(x,y,z) > REACH_SQR) {
            if (--this.repathCooldown <= 0) {
                this.repathCooldown = REPATH_INTERVAL;
                walkToHook(maid);
            }
            return;
        }
        maid.getNavigation().stop();

        //空钩子：先挂胴体
        if (this.hook.isEmpty()) {
            if (MaidHookWork.insertCarcass(this.hook, maid)) {
                this.workCooldown = 0;
            }
            return;
        }

        //有胴体：按当前工序把工具拿到手上
        Optional<AnimatedRecipeItemUse> currect = this.hook.getCurrentTool();
        if (currect.isEmpty()) {
            return;                     //没配方
        }
        int needCount = Math.max(1, this.hook.toolCount);
        if (!MaidHookWork.holdTool(maid, currect.get().tool, needCount)) {
            return;                     //缺工具
        }

        if (--this.workCooldown > 0) {
            return;
        }
        this.workCooldown = WORK_INTERVAL;

        maid.swing(InteractionHand.MAIN_HAND);
        if (MaidHookWork.butcher(level, maid, this.hook)) {
            MaidHookWork.collectDrops(level, maid, this.hook);
        }


    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        maid.getNavigation().stop();
        this.hook = null;
    }

    //就近取钩子：八格内、能走过去
    private MeatHookBlockEntity findHook(ServerLevel level, EntityMaid maid) {
        BlockPos origin = maid.blockPosition();
        BlockPos min = origin.offset((int) -SEARCH_RADIUS, -VERTICAL_RANGE, (int) -SEARCH_RADIUS);
        BlockPos max = origin.offset((int) SEARCH_RADIUS, VERTICAL_RANGE, (int) SEARCH_RADIUS);

        MeatHookBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(min,max)) {
            if (!(level.getBlockEntity(pos) instanceof MeatHookBlockEntity be) || be.isRemoved()) {
                continue;
            }
            double distance = maid.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() +0.5D);
            if (distance > REACH_SQR && standPosNear(maid, pos) == null) {
                continue;                       //够不着，旁边也没有能站人的格子
            }
            //空钩子要有胴体可挂，有胴体要还有工序可做
            boolean usable = be.isEmpty() ? MaidHookWork.hasCarcass(be, maid) : MaidHookWork.canWork(be);
            if (!usable) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = be;

            }
        }
        return best;
    }

    //钩子本体是实心方块，navigate / canPathReach 都到不了它自己；只认它旁边能站人的格子
    private static BlockPos standPosNear(EntityMaid maid, BlockPos hookPos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = hookPos.relative(dir);
            if (maid.canPathReach(side)) {
                return side;
            }
        }
        if (maid.canPathReach(hookPos.below())) {
            return hookPos.below();
        }
        if (maid.canPathReach(hookPos.above())) {
            return hookPos.above();
        }
        return null;
    }

    private void walkToHook(EntityMaid maid) {
        if (this.hook == null) {
            return;
        }
        BlockPos target = standPosNear(maid, this.hook.getBlockPos());
        if (target == null) {
            target = this.hook.getBlockPos();     //旁边都去不了：对着钩子本体再试一次
        }
        maid.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 1.0D);
    }
}

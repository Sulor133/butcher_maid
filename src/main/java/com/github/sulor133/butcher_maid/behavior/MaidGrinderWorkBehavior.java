package com.github.sulor133.butcher_maid.behavior;

import com.github.sulor133.butcher_maid.station.MaidGrinderMode;
import com.github.sulor133.butcher_maid.station.MaidGrinderWork;
import com.github.sulor133.butcher_maid.util.MaidCarcassUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.lance5057.butchercraft.workstations.grinder.GrinderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/** 绞肉 / 灌肠：找机器 -> 走位 -> 装配件 -> 补料 -> 摇把手 -> 收产物 */
public class MaidGrinderWorkBehavior extends Behavior<EntityMaid> {
    //八格内找绞肉机
    private static final double SEARCH_RADIUS = 8.0D;
    private static final int VERTICAL_RANGE = 4;

    private static final double REACH_SQR = 9.0D;
    private static final int REPATH_INTERVAL = 10;
    //空闲时10tick扫描一次机器
    private static final int SCAN_INTERVAL = 10;
    //每10tick摇一下把手
    private static final int WORK_INTERVAL = 10;

    private final MaidGrinderMode mode;

    private GrinderBlockEntity grinderBlockEntity;
    private int repathCooldown;
    private int workCooldown;
    private int nextScanTick;

    public MaidGrinderWorkBehavior(MaidGrinderMode mode) {
        super(Map.of());
        this.mode = mode;
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

        this.grinderBlockEntity = findGrinder(level, maid);
        if (this.grinderBlockEntity == null) {
            return false;
        }
        //扫描阶段只读，选定后才登记
        if (!MaidGrinderWork.claim(this.grinderBlockEntity, maid)) {
            this.grinderBlockEntity = null;
            return false;
        }

        this.workCooldown = 0;
        return true;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        if (MaidCarcassUtil.isFull(maid)) {
            return false;
        }
        if (this.grinderBlockEntity == null || level.getBlockEntity(this.grinderBlockEntity.getBlockPos()) != this.grinderBlockEntity) {
            return false; // 机器被拆了
        }
        return MaidGrinderWork.canWork(this.grinderBlockEntity, this.mode, maid);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        walkToGrinder(maid);
        this.repathCooldown = 0;
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        if (this.grinderBlockEntity == null) {
            return;
        }
        BlockPos pos = this.grinderBlockEntity.getBlockPos();
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        maid.getLookControl().setLookAt(x, y, z, 30.0F, 30.0F);

        if (maid.distanceToSqr(x, y, z) > REACH_SQR) {
            if (--this.repathCooldown <= 0) {
                this.repathCooldown = REPATH_INTERVAL;
                walkToGrinder(maid);
            }
            return;
        }
        maid.getNavigation().stop();

        //装配件
        ItemStack attachment = this.grinderBlockEntity.getAttachment();
        if (attachment.isEmpty()) {
            MaidGrinderWork.insertAttachment(this.grinderBlockEntity, this.mode, maid);
            return;
        }
        if (!this.mode.isTip(attachment)) {
            return;
        }

        //装原料
        if (this.grinderBlockEntity.getInsertedItem().isEmpty()) {
            MaidGrinderWork.insertInput(this.grinderBlockEntity, this.mode, maid);
            return;
        }

        //装肠衣
        if (this.mode.needsCasing && this.grinderBlockEntity.getCasing().isEmpty()) {
            MaidGrinderWork.insertCasing(this.grinderBlockEntity, maid);
            return;
        }

        //摇把手
        if (--this.workCooldown > 0) {
            return;
        }
        this.workCooldown = WORK_INTERVAL;

        maid.swing(InteractionHand.MAIN_HAND);
        if (MaidGrinderWork.crank(this.grinderBlockEntity)) {
            MaidGrinderWork.collectDrops(level, maid, pos);
        }
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        maid.getNavigation().stop();
        this.grinderBlockEntity = null;
    }

    //就近取机器：八格内、能站到旁边、并且它现在能为我干活
    private GrinderBlockEntity findGrinder(ServerLevel level, EntityMaid maid) {
        BlockPos origin = maid.blockPosition();
        BlockPos min = origin.offset((int) -SEARCH_RADIUS, -VERTICAL_RANGE,(int) -SEARCH_RADIUS);
        BlockPos max = origin.offset((int) SEARCH_RADIUS, VERTICAL_RANGE,(int) SEARCH_RADIUS);

        GrinderBlockEntity best = null;
        double bestWeight = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!(level.getBlockEntity(pos) instanceof GrinderBlockEntity be) || be.isRemoved()) {
                continue;
            }
            double distance = maid.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            if (distance > REACH_SQR && standPosNear(maid, pos) == null) {
                continue;                   //够不着，旁边也没有能站立的格子
            }
            if (!MaidGrinderWork.canWork(be, this.mode, maid)){
                continue;
            }
            //已经是我占的机器优先
            double weight = MaidGrinderWork.isMine(be, maid) ? distance * 0.5D : distance;
            if (weight < bestWeight) {
                bestWeight = weight;
                best = be;
            }
        }
        return best;
    }

    //机器本体是实心方块
    private static BlockPos standPosNear(EntityMaid maid, BlockPos stationsPos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = stationsPos.relative(direction);
            if (maid.canPathReach(side)) {
                return side;
            }
        }
        if (maid.canPathReach(stationsPos.below())) {
            return stationsPos.below();
        }
        if (maid.canPathReach(stationsPos.above())) {
            return stationsPos.above();
        }
        return null;
    }

    private void walkToGrinder(EntityMaid maid) {
        if (this.grinderBlockEntity == null) {
            return;
        }
        BlockPos target = standPosNear(maid, this.grinderBlockEntity.getBlockPos());
        if (target == null) {
            target = this.grinderBlockEntity.getBlockPos();     //旁边都去不了,对着机器本体再试一次
        }
        maid.getNavigation().moveTo(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D, 1.0D);
    }
}

package com.github.sulor133.butcher_maid.behavior;

import com.github.sulor133.butcher_maid.util.MaidCarcassUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;
import java.util.function.Predicate;

/**
 * 站台类任务的公共骨架：找站台 -> 走位 -> 每 N tick 干一下 -> 收尾。
 * 具体站台（肉钩 / 绞肉机 / 案板）只需要回答三个问题：
 * 怎么找（findStation）、现在还能不能干（canContinue）、干一下要做什么（work）。
 *
 * @param <T> 站台方块实体类型
 */
public abstract class MaidStationBehavior<T extends BlockEntity> extends Behavior<EntityMaid> {
    //八格内找站台
    protected static final double SEARCH_RADIUS = 8.0D;
    protected static final double REACH_SQR = 9.0D;
    //够不着时多久重算一次路径
    private static final int REPATH_INTERVAL = 10;
    //空闲时多久扫描一次站台
    private static final int SCAN_INTERVAL = 10;
    //两次真正动手之间的间隔
    private static final int WORK_INTERVAL = 10;

    private final Class<T> stationType;
    //垂直搜索范围：肉钩挂上胴体后碰撞箱会往下扩，所以比另两个高
    private final int verticalRange;

    protected T station;
    private int repathCooldown;
    private int workCooldown;
    private int nextScanTick;

    protected MaidStationBehavior(Class<T> stationType, int verticalRange) {
        super(Map.of());
        this.stationType = stationType;
        this.verticalRange = verticalRange;
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
        this.station = findStation(level, maid);
        if (this.station == null) {
            return false;
        }
        //扫描阶段只读，选定后才登记占用
        if (!claimStation(this.station, maid)) {
            this.station = null;
            return false;
        }
        this.workCooldown = 0;
        return true;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        //干到一半背包满了收手，站台上的半成品留着
        if (MaidCarcassUtil.isFull(maid)) {
            return false;
        }
        if (this.station == null || level.getBlockEntity(this.station.getBlockPos()) != this.station) {
            return false;           //站台被拆了
        }
        return canContinue(this.station, maid);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        walkToStation(maid);
        this.repathCooldown = 0;
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        if (this.station == null) {
            return;
        }
        BlockPos pos = this.station.getBlockPos();
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        maid.getLookControl().setLookAt(x, y, z, 30.0F, 30.0F);

        if (maid.distanceToSqr(x, y, z) > REACH_SQR) {
            if (--this.repathCooldown <= 0) {
                this.repathCooldown = REPATH_INTERVAL;
                walkToStation(maid);
            }
            return;
        }
        maid.getNavigation().stop();

        work(level, maid, this.station);
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        maid.getNavigation().stop();
        if (this.station != null) {
            releaseStation(this.station, maid);
        }
        this.station = null;
    }

    //子类要实现的三件事
    /** 就近找一台现在能为我干活的站台，找不到返回 null */
    protected abstract T findStation(ServerLevel level, EntityMaid maid);

    /** 站台还在，但还有没有活可干 / 还轮不轮得到我 */
    protected abstract boolean canContinue(T station, EntityMaid maid);

    /** 站台就在手边时，这一 tick 要做什么；动手前记得先问 readyToWork() */
    protected abstract void work(ServerLevel level, EntityMaid maid, T station);

    //可选
    /** 选定站台后登记占用；返回 false 表示抢不到，放弃这台 */
    protected boolean claimStation(T station, EntityMaid maid) {
        return true;
    }

    /** 离开站台时释放占用 */
    protected void releaseStation(T station, EntityMaid maid) {
    }

    /** 走位时的 Y 偏移。默认取方块脚下那一格的整数 Y */
    protected double standYOffset() {
        return 0.0D;
    }

    //公共工具
    /** 每 WORK_INTERVAL tick 才真正动一次手，其余 tick 直接返回 */
    protected boolean readyToWork() {
        if (--this.workCooldown > 0) {
            return false;
        }
        this.workCooldown = WORK_INTERVAL;
        return true;
    }

    /** 刚做了「放料」这类不算动手的动作，让下一次动手不用等 */
    protected void resetWorkCooldown() {
        this.workCooldown = 0;
    }

    /**
     * 扫描范围内的同类站台，留下「能站到旁边」且 usable 为真的，取最近的一台。
     * 已经归我占的站台距离打五折，避免为了更近的空站台丢掉做到一半的活。
     */
    protected T findNearestStation(ServerLevel level, EntityMaid maid, Predicate<T> usable) {
        return findNearestStation(level, maid, usable, be -> false);
    }

    protected T findNearestStation(ServerLevel level, EntityMaid maid,
                                   Predicate<T> usable, Predicate<T> mine) {
        BlockPos origin = maid.blockPosition();
        BlockPos min = origin.offset((int) -SEARCH_RADIUS, -this.verticalRange, (int) -SEARCH_RADIUS);
        BlockPos max = origin.offset((int) SEARCH_RADIUS, this.verticalRange, (int) SEARCH_RADIUS);

        T best = null;
        double bestWeight = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!this.stationType.isInstance(be) || be.isRemoved()) {
                continue;
            }
            T candidate = this.stationType.cast(be);
            double distance = maid.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            if (distance > REACH_SQR && standPosNear(maid, pos) == null) {
                continue;               //够不着，旁边也没有能站人的格子
            }
            if (!usable.test(candidate)) {
                continue;
            }
            double weight = mine.test(candidate) ? distance * 0.5D : distance;
            if (weight < bestWeight) {
                bestWeight = weight;
                best = candidate;
            }
        }
        return best;
    }

    /**站台本体是实心方块，navigate / canPathReach 都到不了它自己；只认它旁边能站人的格子 */
    protected static BlockPos standPosNear(EntityMaid maid, BlockPos stationPos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = stationPos.relative(dir);
            if (maid.canPathReach(side)) {
                return side;
            }
        }
        if (maid.canPathReach(stationPos.below())) {
            return stationPos.below();
        }
        if (maid.canPathReach(stationPos.above())) {
            return stationPos.above();
        }
        return null;
    }

    private void walkToStation(EntityMaid maid) {
        if (this.station == null) {
            return;
        }
        BlockPos target = standPosNear(maid, this.station.getBlockPos());
        if (target == null) {
            target = this.station.getBlockPos();     //旁边都去不了：对着方块本体再试一次
        }
        maid.getNavigation().moveTo(target.getX() + 0.5D, target.getY() + standYOffset(),
                target.getZ() + 0.5D, 1.0D);
    }

}

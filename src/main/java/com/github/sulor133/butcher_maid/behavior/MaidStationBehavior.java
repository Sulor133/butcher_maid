package com.github.sulor133.butcher_maid.behavior;

import com.github.sulor133.butcher_maid.util.MaidCarcassUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidPathFindingBFS;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
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
    //一次扫描里最多做几次真正的可达性判定，防止一堆够不着的站台把 TPS 吃光
    private static final int MAX_REACH_CHECKS = 3;

    private final Class<T> stationType;
    //垂直搜索范围：肉钩挂上胴体后碰撞箱会往下扩，所以比另两个高
    private final int verticalRange;

    protected T station;
    //选定站台时算好的落脚点，走位复用，避免每 10 tick 再跑 6 次 A*
    private BlockPos standPos;
    private int repathCooldown;
    private int workCooldown;
    private int nextScanTick;

    /**
     * 一次扫描的候选：distance 用来判断「够得着就不用验证走位」，
     * weight 用来排序（自己的站台打五折）。
     */
    private record Pending<S>(S station, double distance, double weight) {
    }

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
        //加个 id 偏移错峰，避免一群女仆在同一 tick 一起扫描
        this.nextScanTick = maid.tickCount + SCAN_INTERVAL + maid.getId() % SCAN_INTERVAL;
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
            this.standPos = null;
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
        this.repathCooldown = 0;
        if (this.station == null) {
            return;
        }
        BlockPos pos = this.station.getBlockPos();
        //已经在手边就别动，省掉 standPosNear 的六次寻路
        if (maid.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > REACH_SQR) {
            walkToStation(maid);
        }
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
        this.standPos = null;
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

    /**
     * 两段式：
     * 1) 只遍历搜索盒覆盖到的区块里的方块实体表（O(实体数)，不是 O(体积)），
     *    过一遍便宜的 usable + 距离，攒成候选；
     * 2) 按权重从近到远排序，逐个做「真的走得到吗」，第一个通过的就是答案，后面的不再算。
     * 贵的就只有第 2 段，而且通常只做一次。
     */
    protected T findNearestStation(ServerLevel level, EntityMaid maid,
                                   Predicate<T> usable, Predicate<T> mine) {
        this.standPos = null;

        int r = (int) SEARCH_RADIUS;
        BlockPos origin = maid.blockPosition();
        int minX = origin.getX() - r;
        int maxX = origin.getX() + r;
        int minY = origin.getY() - this.verticalRange;
        int maxY = origin.getY() + this.verticalRange;
        int minZ = origin.getZ() - r;
        int maxZ = origin.getZ() + r;
        double mx = maid.getX();
        double my = maid.getY();
        double mz = maid.getZ();

        //1) 区块当天然的空间桶：17x17 最多覆盖 4 个区块，每个区块只翻它自己的方块实体表
        List<Pending<T>> pending = new ArrayList<>(4);
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                //getChunkNow 不会强加载 / 生成区块，未加载直接返回 null
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!this.stationType.isInstance(be) || be.isRemoved()) {
                        continue;
                    }
                    BlockPos pos = be.getBlockPos();
                    if (pos.getX() < minX || pos.getX() > maxX
                            || pos.getY() < minY || pos.getY() > maxY
                            || pos.getZ() < minZ || pos.getZ() > maxZ) {
                        continue;                   //同区块但出了搜索盒
                    }
                    T candidate = this.stationType.cast(be);
                    if (!usable.test(candidate)) {
                        continue;
                    }
                    double dx = mx - (pos.getX() + 0.5D);
                    double dy = my - (pos.getY() + 0.5D);
                    double dz = mz - (pos.getZ() + 0.5D);
                    double distance = dx * dx + dy * dy + dz * dz;
                    double weight = mine.test(candidate) ? distance * 0.5D : distance;
                    pending.add(new Pending<>(candidate, distance, weight));
                }
            }
        }
        if (pending.isEmpty()) {
            return null;
        }
        //候选通常个位数，排序开销可以忽略；相同权重保持原顺序（稳定排序）
        pending.sort((a, b) -> Double.compare(a.weight(), b.weight()));

        //2) 贵的那一步只对排在前面的候选做，命中即停
        MaidPathFindingBFS reach = null;
        try {
            int checks = 0;
            for (Pending<T> p : pending) {
                if (p.distance() <= REACH_SQR) {
                    return p.station();             //够得着，不用验证走位
                }
                if (++checks > MAX_REACH_CHECKS) {
                    break;                          //剩下的下一轮扫描再说
                }
                if (reach == null) {
                    //一次 BFS 扩散服务本次扫描的所有候选，比每个点各跑一遍 A* 便宜得多
                    reach = new MaidPathFindingBFS(
                            maid.getNavigation().getNodeEvaluator(), level, maid,
                            (float) SEARCH_RADIUS, this.verticalRange);
                }
                BlockPos stand = standPosNear(reach::canPathReach, p.station().getBlockPos());
                if (stand != null) {
                    this.standPos = stand;          //落脚点存下来，走位时直接复用
                    return p.station();
                }
            }
            return null;
        } finally {
            if (reach != null) {
                reach.finish();                     //必须还：它握着 PathNavigationRegion
            }
        }
    }

    /**站台本体是实心方块，navigate / canPathReach 都到不了它自己；只认它旁边能站人的格子 */
    protected static BlockPos standPosNear(EntityMaid maid, BlockPos stationPos) {
        return standPosNear(maid::canPathReach, stationPos);
    }

    /**
     * 同上，但可达性由调用方提供：可以传 maid::canPathReach（每个点一次 A*），
     * 也可以传 MaidPathFindingBFS::canPathReach（多个点共享一次 BFS 扩散）。
     */
    protected static BlockPos standPosNear(Predicate<BlockPos> reachable, BlockPos stationPos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = stationPos.relative(dir);
            if (reachable.test(side)) {
                return side;
            }
        }
        if (reachable.test(stationPos.below())) {
            return stationPos.below();
        }
        if (reachable.test(stationPos.above())) {
            return stationPos.above();
        }
        return null;
    }

    private void walkToStation(EntityMaid maid) {
        if (this.station == null) {
            return;
        }
        BlockPos target = this.standPos;
        if (target == null) {
            //只有落脚点没算过 / 上次走不通才重新找，正常情况这里不会跑 A*
            target = standPosNear(maid, this.station.getBlockPos());
            if (target == null) {
                target = this.station.getBlockPos();     //旁边都去不了：对着方块本体再试一次
            }
        }
        boolean ok = maid.getNavigation().moveTo(target.getX() + 0.5D, target.getY() + standYOffset(),
                target.getZ() + 0.5D, 1.0D);
        if (!ok) {
            this.standPos = null;    //这个落脚点走不通，下次重新挑
        }
    }

}

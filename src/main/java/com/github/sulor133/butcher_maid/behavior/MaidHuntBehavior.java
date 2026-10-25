package com.github.sulor133.butcher_maid.behavior;

import com.github.sulor133.butcher_maid.station.MaidSlaughter;
import com.github.sulor133.butcher_maid.util.MaidCarcassUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.lance5057.butchercraft.items.ButcherKnifeItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.phys.AABB;

import java.util.Map;

/** 狩猎：找猎物 -> 走过去 -> 一刀取胴体 -> 掉落进包 */
public class MaidHuntBehavior extends Behavior<EntityMaid> {

    private static final double SEARCH_RADIUS = 24.0D;
    //两格范围内动刀
    private static final double REACH_SQR = 4.0D;
    private static final int REPATH_INTERVAL = 10;
    //空闲时每10tick扫描一次猎物
    private static final int SCAN_INTERVAL = 10;

    private Mob prey;
    private int repathCooldown;
    private int nextScanTick;

    public MaidHuntBehavior() {
        super(Map.of());
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!holdsKnife(maid) || MaidCarcassUtil.isFull(maid)) {
            return false;
        }
        if (maid.tickCount < this.nextScanTick) {
            return false;
        }
        this.nextScanTick = maid.tickCount + SCAN_INTERVAL;
        this.prey = findPrey(level, maid);
        return this.prey != null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return this.prey != null
                && MaidSlaughter.canSlaughter(this.prey)
                && !MaidCarcassUtil.isFull(maid);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        maid.getNavigation().moveTo(this.prey, 1.0D);
        this.repathCooldown = 0;
    }


    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        if (this.prey == null) {
            return;
        }
        maid.getLookControl().setLookAt(this.prey, 30.0F, 30.0F);

        if (maid.distanceToSqr(this.prey) > REACH_SQR) {
            if (--this.repathCooldown <= 0) {
                this.repathCooldown = REPATH_INTERVAL;
                maid.getNavigation().moveTo(this.prey, 1.0D);
            }
            return;
        }
        maid.getNavigation().stop();
        if (!maid.hasLineOfSight(this.prey)) {
            return;
        }

        maid.swing(InteractionHand.MAIN_HAND);
        if (!MaidSlaughter.slaughter(level, maid, this.prey)) {
            this.prey = null;            //取不到胴体就放弃
        }
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        maid.getNavigation().stop();
        this.prey = null;
    }

    //必须手持屠刀才能狩猎
    private static boolean holdsKnife(EntityMaid maid) {
        return maid.getMainHandItem().getItem() instanceof ButcherKnifeItem;
    }

    private Mob findPrey(ServerLevel level, EntityMaid maid) {
        AABB box = maid.getBoundingBox().inflate(SEARCH_RADIUS);
        Mob best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, MaidHuntBehavior::isWildPrey)) {
            if (!maid.canPathReach(mob)) {
                continue;
            }
            if (!MaidSlaughter.canProduceCarcass(level, mob)) {
                continue;       //便签里有但没有战利品表的生物
            }
            double distance = maid.distanceToSqr(mob);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = mob;
            }
        }
        return best;
    }

    private static boolean isWildPrey(Mob mob) {
        if (!MaidSlaughter.canSlaughter(mob)) {
            return false;
        }
        return !(mob instanceof TamableAnimal tamableAnimal) || !tamableAnimal.isTame();
    }
}

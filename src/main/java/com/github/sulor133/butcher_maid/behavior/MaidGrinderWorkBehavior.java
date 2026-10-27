package com.github.sulor133.butcher_maid.behavior;

import com.github.sulor133.butcher_maid.station.MaidGrinderMode;
import com.github.sulor133.butcher_maid.station.MaidGrinderWork;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.lance5057.butchercraft.workstations.grinder.GrinderBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** 绞肉 / 灌肠：装配件 -> 补料 -> 摇把手 -> 收产物 */
public class MaidGrinderWorkBehavior extends MaidStationBehavior<GrinderBlockEntity> {

    private final MaidGrinderMode mode;

    public MaidGrinderWorkBehavior(MaidGrinderMode mode) {
        super(GrinderBlockEntity.class, 4);
        this.mode = mode;
    }

    @Override
    protected GrinderBlockEntity findStation(ServerLevel level, EntityMaid maid) {
        return findNearestStation(level, maid,
                be -> MaidGrinderWork.canWork(be, this.mode, maid),
                be -> MaidGrinderWork.isMine(be, maid));
    }

    @Override
    protected boolean canContinue(GrinderBlockEntity grinder, EntityMaid maid) {
        return MaidGrinderWork.canWork(grinder, this.mode, maid);
    }

    //扫描阶段只读，选定后才登记占用
    @Override
    protected boolean claimStation(GrinderBlockEntity grinder, EntityMaid maid) {
        return MaidGrinderWork.claim(grinder, maid);
    }

    //历史差异：绞肉机原本用 Y+0.5 走位，与另两个站台不同，这里原样保留
    @Override
    protected double standYOffset() {
        return 0.5D;
    }

    @Override
    protected void work(ServerLevel level, EntityMaid maid, GrinderBlockEntity grinder) {
        //装配件
        ItemStack attachment = grinder.getAttachment();
        if (attachment.isEmpty()) {
            MaidGrinderWork.insertAttachment(grinder, this.mode, maid);
            return;
        }
        if (!this.mode.isTip(attachment)) {
            return;                     //装了别的配件，绝不拆别人的
        }

        //装原料
        if (grinder.getInsertedItem().isEmpty()) {
            MaidGrinderWork.insertInput(grinder, this.mode, maid);
            return;
        }

        //装肠衣
        if (this.mode.needsCasing && grinder.getCasing().isEmpty()) {
            MaidGrinderWork.insertCasing(grinder, maid);
            return;
        }

        //摇把手
        if (!readyToWork()) {
            return;
        }
        maid.swing(InteractionHand.MAIN_HAND);
        if (MaidGrinderWork.crank(grinder)) {
            MaidGrinderWork.collectDrops(level, maid, grinder.getBlockPos());
        }
    }
}

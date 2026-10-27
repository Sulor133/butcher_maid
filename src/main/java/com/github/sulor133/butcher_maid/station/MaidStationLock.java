package com.github.sulor133.butcher_maid.station;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 站台「当前归谁用」的登记，落在方块实体的 ForgeData 里。
 * 无主 / 是我 / 上一个占用者超时，三种情况才算可用。
 */
public final class MaidStationLock {
    //占用登记的键
    private static final String OWNER = "butcher_maid_owner";
    private static final String TOUCHED = "butcher_maid_touched";
    //10秒没有操作自动放弃
    private static final long LOCK_TIMEOUT = 200L;

    private MaidStationLock() {
    }

    public static boolean isMine(BlockEntity station, EntityMaid maid) {
        CompoundTag data = station.getPersistentData();
        return data.hasUUID(OWNER) && data.getUUID(OWNER).equals(maid.getUUID());
    }

    public static boolean isAvailable(BlockEntity station, EntityMaid maid) {
        CompoundTag data = station.getPersistentData();
        if (!data.hasUUID(OWNER)) {
            return true;
        }
        if (data.getUUID(OWNER).equals(maid.getUUID())) {
            return true;
        }
        return station.getLevel() != null
                && station.getLevel().getGameTime() - data.getLong(TOUCHED) > LOCK_TIMEOUT;
    }

    /** 选定站台后调用；抢不到返回 false */
    public static boolean claim(BlockEntity station, EntityMaid maid) {
        if (!isAvailable(station, maid)) {
            return false;
        }
        station.getPersistentData().putUUID(OWNER, maid.getUUID());
        touch(station);
        station.setChanged();       //让 Forge 把 ForgeData 写回 NBT
        return true;
    }

    /** 续租：每次有效动作刷新一次 */
    public static void touch(BlockEntity station) {
        if (station.getLevel() == null) {
            return;
        }
        station.getPersistentData().putLong(TOUCHED, station.getLevel().getGameTime());
    }

    /** 离开时主动清除占用 */
    public static void release(BlockEntity station, EntityMaid maid) {
        CompoundTag data = station.getPersistentData();
        if (data.hasUUID(OWNER) && data.getUUID(OWNER).equals(maid.getUUID())) {
            data.remove(OWNER);
            data.remove(TOUCHED);
            station.setChanged();
        }
    }
}

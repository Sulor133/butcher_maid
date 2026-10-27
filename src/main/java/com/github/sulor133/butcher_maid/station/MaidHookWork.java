package com.github.sulor133.butcher_maid.station;

import com.github.sulor133.butcher_maid.util.MaidCarcassUtil;
import com.github.sulor133.butcher_maid.util.MaidFakePlayerUtil;
import com.github.sulor133.butcher_maid.util.MaidInvUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.lance5057.butchercraft.workstations.hook.MeatHookBlockEntity;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;

/**
 * 肉钩加工：挂胴体 -> 按工序换工具 -> 一刀一刀推进 -> 产物进包。
 * 女仆不是 Player，但 butcher(Player, ItemStack) 只把 Player 用在掉落表上下文（工具 / 幸运值 / 服务端）、
 * 装备栏 0~3 判定、playSound 的 except 参数上，所以借一个 Forge FakePlayer 顶替。
 */
public class MaidHookWork {
    private static final GameProfile FAKE_PROFILE = MaidFakePlayerUtil.profile("hook");

    private MaidHookWork() {
    }

    //钩子上挂着东西，而且有配方可做
    public static boolean canWork(MeatHookBlockEntity hook) {
        //注：和案板同理，配方段数变化时 stage 可能越界，这里维持原行为不自愈
        return !hook.isEmpty() && hook.getCurrentTool().isPresent();
    }

    //背包里有没有钩子能挂的胴体
    public static boolean hasCarcass(MeatHookBlockEntity hook, EntityMaid maid) {
        int slot = MaidCarcassUtil.firstSlot(maid);
        return slot >= 0 && MaidInvUtil.canInsert(hook, maid.getAvailableInv(true).getStackInSlot(slot));
    }

    //把背包里胴体挂上去
    public static boolean insertCarcass(MeatHookBlockEntity hook, EntityMaid maid) {
        int slot = MaidCarcassUtil.firstSlot(maid);
        if (slot < 0 || !MaidInvUtil.canInsert(hook, maid.getAvailableInv(true).getStackInSlot(slot))) {
            return false;
        }
        //insertItem(ItemStack) 是 void 的，塞不进去的余量写回同一个栈
        return MaidInvUtil.insertOne(maid, slot, stack -> {
            hook.insertItem(stack);
            return stack;
        });
    }

    /**
     * 借 FakePlayer 砍一刀。传进去的是主手，磨损 / 消耗直接作用在女仆手上的工具。
     *
     * @return 这一刀是否被接受（false = 工具不对 / 配方不匹配）
     */
    public static boolean butcher(ServerLevel level, EntityMaid maid, MeatHookBlockEntity hook) {
        return MaidFakePlayerUtil.withMainHand(level, FAKE_PROFILE, maid,
                (fakePlayer, tool) -> hook.butcher(fakePlayer, tool).consumesAction());
    }
}

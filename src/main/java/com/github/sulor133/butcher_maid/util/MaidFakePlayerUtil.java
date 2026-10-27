package com.github.sulor133.butcher_maid.util;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.BiPredicate;

/** 女仆不是 Player，需要 Player 参数的地方借一个 Forge FakePlayer 顶替。 */
public class MaidFakePlayerUtil {
    private MaidFakePlayerUtil() {
    }

    /** 按站台类型各配一个身份，别让不同机器的掉落上下文混在一起 */
    public static GameProfile profile(String key) {
        return new GameProfile(
                UUID.nameUUIDFromBytes(("butcher_maid:" + key).getBytes(StandardCharsets.UTF_8)),
                "[butcher_maid]");
    }

    /**
     * 让假玩家主手拿着女仆当前物品执行一次操作，结束后还原。
     * 传进去的是女仆主手那个 ItemStack 本体，磨损 / 消耗直接作用在它上面。
     */
    public static boolean withMainHand(ServerLevel level, GameProfile profile, EntityMaid maid,
                                       BiPredicate<FakePlayer, ItemStack> action) {
        ItemStack tool = maid.getMainHandItem();
        FakePlayer fakePlayer = FakePlayerFactory.get(level, profile);
        fakePlayer.setItemSlot(EquipmentSlot.MAINHAND, tool);
        try {
            return action.test(fakePlayer, tool);
        } finally {
            fakePlayer.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            if (maid.getMainHandItem().isEmpty()) {
                //瓶和桶这类不可损坏的物品是被 setCount(0) 消耗的，槽里会留一摞空栈
                maid.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        }
    }
}

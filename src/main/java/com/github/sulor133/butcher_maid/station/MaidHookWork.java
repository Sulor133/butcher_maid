package com.github.sulor133.butcher_maid.station;

import com.github.sulor133.butcher_maid.util.MaidCarcassUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.lance5057.butchercraft.workstations.hook.MeatHookBlockEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.types.templates.Hook;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static java.util.regex.Pattern.matches;

/**
 * 肉钩加工：挂胴体 -> 按工序换工具 -> 一刀一刀推进 -> 产物进包。
 * 女仆不是 Player，但 butcher(Player, ItemStack) 只把 Player 用在掉落表上下文（工具 / 幸运值 / 服务端）、
 * 装备栏 0~3 判定、playSound 的 except 参数上，所以借一个 Forge FakePlayer 顶替，
 */
public class MaidHookWork {
    //只捡刚掉出来的产物，玩家丢在钩子旁的杂物不碰
    private static final int FRESH_DROP_TICKS = 5;

    private static final GameProfile FAKE_PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("butcher_maid:hook".getBytes(StandardCharsets.UTF_8))
            ,"[butcher_maid]");

    private MaidHookWork(){

    }

    //钩子上挂着东西，而且有配方可做
    public static boolean canWork(MeatHookBlockEntity hook) {
        return !hook.isEmpty() && hook.getCurrentTool().isPresent();
    }

    //背包里有没有钩子能挂的胴体
    public static boolean hasCarcass(MeatHookBlockEntity hook, EntityMaid maid) {
        int slot = MaidCarcassUtil.firstSlot(maid);
        return slot >= 0 && acceptsCarcass(hook, maid.getAvailableInv(true).getStackInSlot(slot));
    }

    /**
     * 钩子能不能挂这只胴体。
     * 直接用 Butchercraft 自己的 isItemValid
     * 它的 ITEM_HANDLER 只对非 DOWN 面暴露，传 null 才拿得到。
     */

    private static boolean acceptsCarcass(MeatHookBlockEntity hook, ItemStack stack) {
        return hook.getCapability(ForgeCapabilities.ITEM_HANDLER, (Direction) null)
                .map(handler -> handler.isItemValid(0,stack))
                .orElse(false);
    }

    //把背包里胴体挂上去
    public static boolean insertCarcass(MeatHookBlockEntity hook, EntityMaid maid) {
        int slot = MaidCarcassUtil.firstSlot(maid);
        if (slot < 0){
            return false;
        }
        IItemHandler inventory = maid.getAvailableInv(true);
        if (!acceptsCarcass(hook, inventory.getStackInSlot(slot))) {
            return false;
        }
        ItemStack carcass = inventory.extractItem(slot, 1, false);
        if (carcass.isEmpty()) {
            return false;
        }
        hook.insertItem(carcass);
        if (!carcass.isEmpty()) {
            inventory.insertItem(slot, carcass, false);
            return false;
        }
        return true;
    }

    /**
     * 把当前工序要的工具拿到主手：手里已经是就不动，否则和背包里直接对调
     * （换下来的回背包，不会掉地上）。
     */

    public static boolean holdTool(EntityMaid maid, Ingredient tool, int needCount) {
        ItemStack main = maid.getMainHandItem();
        if (matches(tool, main, needCount)) {
            return true;
        }
        //必须用 getMaidInv()：getAvailableInv() 是 CombinedInvWrapper，没有 setStackInSlot
        ItemStackHandler backpack = maid.getMaidInv();
        for (int i = 0; i < backpack.getSlots(); i++) {
            ItemStack candidate = backpack.getStackInSlot(i);
            if (!matches(tool, candidate, needCount)) {
                continue;
            }
            backpack.setStackInSlot(i, main.isEmpty() ? ItemStack.EMPTY : main);
            maid.setItemInHand(InteractionHand.MAIN_HAND, candidate);
            return true;
        }
        return false;           //缺少工具不干活
    }

    private static boolean matches(Ingredient tool, ItemStack stack, int needCount) {
        return !stack.isEmpty() && stack.getCount() >= needCount && tool.test(stack);
    }

    /**
     * 借 FakePlayer 砍一刀。传进去的是主手，磨损 / 消耗直接作用在女仆手上的工具。
     * @return 这一刀是否被接受（false = 工具不对 / 配方不匹配）
     */

    public static boolean butcher(ServerLevel level, EntityMaid maid, MeatHookBlockEntity hook) {
        ItemStack tool = maid.getMainHandItem();
        FakePlayer fakePlayer = FakePlayerFactory.get(level, FAKE_PROFILE);
        fakePlayer.setItemSlot(EquipmentSlot.MAINHAND, tool);
        try {
            return hook.butcher(fakePlayer, tool).consumesAction();
        }finally {
            fakePlayer.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            if (maid.getMainHandItem().isEmpty()) {
                //桶这类不可损坏的工具是被 setCount(0) 消耗的，槽里会留一摞空栈
                maid.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        }
    }
    /** 把钩子附近刚掉出来的产物装进女仆可用背包（装不下的留在地上） */
    public static void collectDrops(ServerLevel level, EntityMaid maid, MeatHookBlockEntity hook) {
        //背包优先：产物别往主手塞，否则会把刚换好的工具挤掉
        IItemHandler inventory = maid.getAvailableInv(false);
        BlockPos pos = hook.getBlockPos();
        AABB box = new AABB(pos).inflate(3.0D);
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class,box)) {
            if (!drop.isAlive() || drop.getAge() > FRESH_DROP_TICKS) {
                continue;
            }
            //先并同类格子，再找空格，符合原生车万女仆写法
            ItemStack remaining = ItemHandlerHelper.insertItemStacked(inventory, drop.getItem(), false);
            if (remaining.isEmpty()) {
                drop.discard();
            }
            else {
                drop.setItem(remaining);
            }
        }
    }
}

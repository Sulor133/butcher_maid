package com.github.sulor133.butcher_maid.station;

import com.github.sulor133.butcher_maid.util.MaidInvUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.lance5057.butchercraft.tags.ButchercraftItemTags;
import com.lance5057.butchercraft.workstations.grinder.GrinderBlockEntity;
import com.lance5057.butchercraft.workstations.grinder.GrinderRecipe;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * 绞肉机：装对配件 -> 补够原料 -> （灌肠补肠衣）-> 摇把手。
 * 战利品表读取屠宰工艺的，以及给这台机器登记一个「当前归谁用」的占用。
 */
public class MaidGrinderWork {
    private MaidGrinderWork() {
    }

    /**
     * 这台机器现在能不能为我干活。
     * 照着 grind() 的四个 PASS 出口写：任一条件不满足，摇多少下都不会有产物。
     */
    public static boolean canWork(GrinderBlockEntity grinder, MaidGrinderMode mode, EntityMaid maid) {
        //被别人占着的机器不使用
        if (!isAvailable(grinder, maid)) {
            return false;
        }
        ItemStack attachment = grinder.getAttachment();
        //装了别的配件 -> 别的酒狐的机器不拆
        if (!attachment.isEmpty() && !mode.isTip(attachment)) {
            return false;
        }
        //空机器却连配件都带不上去
        if (attachment.isEmpty() && MaidInvUtil.findSlot(maid, mode::isTip) < 0) {
            return false;
        }
        //配件还没装时，去试装了配件的配方
        ItemStack prode = attachment.isEmpty() ? mode.createTip() : attachment;

        ItemStack input = grinder.getInsertedItem();
        if (input.isEmpty()) {
            return inputSlot(grinder, prode, maid) >= 0;
        }
        Optional<GrinderRecipe> recipe = grinder.matchRecipe(input, prode);
        //料跟配件不匹配，或者数量不够配方要的 itemsUsed -> 这台机器已经卡住
        if (recipe.isEmpty() || input.getCount() < recipe.get().count) {
            return false;
        }
        //灌肠缺肠衣时 grind() 直接跳过
        return !mode.needsCasing
                || !grinder.getCasing().isEmpty()
                || MaidInvUtil.findSlot(maid, MaidGrinderWork::isCasing) >= 0;
    }

    //装配件时一次一个，插不进去就还回原槽
    public static boolean insertAttachment(GrinderBlockEntity grinder, MaidGrinderMode mode, EntityMaid maid) {
        return MaidInvUtil.insertOne(maid, MaidInvUtil.findSlot(maid, mode::isTip), grinder::insertItem);
    }

    //补原料：机器一槽最多收 8 个，剩下的退回背包
    public static boolean insertInput(GrinderBlockEntity grinder, MaidGrinderMode mode, EntityMaid maid) {
        ItemStack attachment = grinder.getAttachment();
        ItemStack prode = attachment.isEmpty() ? mode.createTip() : attachment;
        return MaidInvUtil.insertStack(maid, inputSlot(grinder, prode, maid), grinder::insertItem);
    }

    //补肠衣：一次一个，肠衣槽上限是 1
    public static boolean insertCasing(GrinderBlockEntity grinder, EntityMaid maid) {
        return MaidInvUtil.insertOne(maid,
                MaidInvUtil.findSlot(maid, MaidGrinderWork::isCasing), grinder::insertItem);
    }

    /**
     * 摇一下把手。grind() 里 Player 只用在 playSound 的 except 参数上（该参数可空），
     * 所以女仆不需要借 Fakeplayer，直接传 null。
     *
     * @return 这一下是否被接受（false = 缺配件 / 料不够 / 缺肠衣）
     */
    public static boolean crank(GrinderBlockEntity grinder) {
        boolean accepted = grinder.grind(null, grinder.getBlockState()).consumesAction();
        if (accepted) {
            //边摇边续原料
            touch(grinder);
        }
        return accepted;
    }

    /** 判断这台机器现在是不是当前酒狐占的 */
    public static boolean isMine(GrinderBlockEntity grinder, EntityMaid maid) {
        return MaidStationLock.isMine(grinder, maid);
    }

    /** 无主 / 是我 / 已经超时，三种情况才算可用 */
    public static boolean isAvailable(GrinderBlockEntity grinder, EntityMaid maid) {
        return MaidStationLock.isAvailable(grinder, maid);
    }

    /** 选中这台机器后再调用；抢不到返回 false */
    public static boolean claim(GrinderBlockEntity grinder, EntityMaid maid) {
        return MaidStationLock.claim(grinder, maid);
    }

    /** 续租：每次有效动作刷新一次 */
    public static void touch(GrinderBlockEntity grinder) {
        MaidStationLock.touch(grinder);
    }

    /** 离开时主动释放占用（原来没有，只能靠 200 tick 超时） */
    public static void release(GrinderBlockEntity grinder, EntityMaid maid) {
        MaidStationLock.release(grinder, maid);
    }

    //背包里能放进这台机器、且数量够一轮的原料槽位，没有则 -1
    private static int inputSlot(GrinderBlockEntity grinder, ItemStack prode, EntityMaid maid) {
        return MaidInvUtil.findSlot(maid, stack -> grinder.matchRecipe(stack, prode)
                .filter(recipe -> stack.getCount() >= recipe.count)
                .isPresent());
    }

    private static boolean isCasing(ItemStack stack) {
        return stack.is(ButchercraftItemTags.SAUSAGE_CASING);
    }
}

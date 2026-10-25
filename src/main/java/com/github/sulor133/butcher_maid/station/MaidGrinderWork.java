package com.github.sulor133.butcher_maid.station;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.lance5057.butchercraft.tags.ButchercraftItemTags;
import com.lance5057.butchercraft.workstations.grinder.GrinderBlockEntity;
import com.lance5057.butchercraft.workstations.grinder.GrinderRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.Optional;


/**
 * 绞肉机：装对配件 -> 补够原料 -> （灌肠补肠衣）-> 摇把手。
 * 战利品表读取屠宰工艺的
 * 以及给这台机器登记一个「当前归谁用」的占用。
 */
public class MaidGrinderWork {
    //只捡刚掉出来的产物
    private static final int FRESH_DROP_TICKS = 5;

    //占用登记的键
    private static final String OWNER = "butcher_maid_owner";
    private static final String TOUCHED = "butcher_maid_touched";

    //10秒没有操作自动放弃这台机器
    private static final long LOCK_TIMEOUT = 200L;

    private MaidGrinderWork(){

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
        if (attachment.isEmpty() && firstTipSlot(maid, mode) < 0) {
            return false;
        }
        //配件还没装时，去试装了配件的配方
        ItemStack prode = attachment.isEmpty() ? mode.createTip() : attachment;

        ItemStack input = grinder.getInsertedItem();
        if (input.isEmpty()) {
            return findInputSlot(grinder, prode, maid) >= 0;
        }
        Optional<GrinderRecipe> recipe = grinder.matchRecipe(input, prode);
        //料跟配件不匹配，或者数量不够配方要的 itemsUsed -> 这台机器已经卡住
        if (recipe.isEmpty() || input.getCount() < recipe.get().count) {
            return false;
        }
        //灌肠缺肠衣时grind()直接跳过
        return !mode.needsCasing
                || !grinder.getCasing().isEmpty()
                || firstCasingSlot(maid) >= 0;
    }

    //装配件时一次一个，插不进去就还回原槽
    public static boolean insertAttachment(GrinderBlockEntity grinderBlockEntity, MaidGrinderMode mode, EntityMaid maid) {
        int slot = firstTipSlot(maid , mode);
        if (slot < 0){
            return false;
        }
        return moveOne(maid, slot, grinderBlockEntity);
    }

    //补原料：机器一槽最多收 8 个，剩下的退回背包
    public static boolean insertInput(GrinderBlockEntity grinderBlockEntity, MaidGrinderMode mode, EntityMaid maid) {
        ItemStack attachment = grinderBlockEntity.getAttachment();
        ItemStack prode = attachment.isEmpty() ? mode.createTip() : attachment;
        int slot = findInputSlot(grinderBlockEntity, prode, maid);
        if (slot < 0) {
            return false;
        }
        IItemHandler inventory = maid.getAvailableInv(true);
        int count = inventory.getStackInSlot(slot).getCount();
        ItemStack taken = inventory.extractItem(slot, count, false);
        if (taken.isEmpty()) {
            return false;
        }
        ItemStack left = grinderBlockEntity.insertItem(taken);
        if (!left.isEmpty()) {
            inventory.insertItem(slot, left, false);
        }
        return taken.getCount() != left.getCount();
    }

    //补肠衣：一次一个，肠衣槽上限是1
    public static boolean insertCasing(GrinderBlockEntity grinderBlockEntity, EntityMaid maid) {
        int slot = firstCasingSlot(maid);
        if (slot < 0) {
            return false;
        }
        return moveOne(maid, slot, grinderBlockEntity);
    }

    /**
     * 摇一下把手。grind() 里 Player 只用在 playSound 的 except 参数上（该参数可空），
     * 所以女仆不需要借Fakeplayer，直接传 null。
     * @return 这一下是否被接受（false = 缺配件 / 料不够 / 缺肠衣）
     */
    public static boolean crank(GrinderBlockEntity grinderBlockEntity) {
        boolean accepted = grinderBlockEntity.grind(null, grinderBlockEntity.getBlockState()).consumesAction();
        if (accepted) {
            //边摇边续原料
            touch(grinderBlockEntity);
        }
        return accepted;
    }

    /** 把机器附近刚掉出来的产物装进女仆可用背包（装不下的留在地上） */
    public static void collectDrops(ServerLevel level, EntityMaid maid, BlockPos pos) {
        //背包优先
        IItemHandler inventory = maid.getAvailableInv(false);
        AABB box = new AABB(pos).inflate(3.0D);
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, box)) {
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

    /** 判断这台机器现在是不是当前酒狐占的 */
    public static boolean isMine(GrinderBlockEntity grinderBlockEntity, EntityMaid maid) {
        CompoundTag data = grinderBlockEntity.getPersistentData();
        return data.hasUUID(OWNER) && data.getUUID(OWNER).equals(maid.getUUID());
    }

    /** 无主 / 是我 / 已经超时，三种情况才算可用 */
    public static boolean isAvailable(GrinderBlockEntity grinderBlockEntity, EntityMaid maid) {
        CompoundTag data = grinderBlockEntity.getPersistentData();
        if (!data.hasUUID(OWNER)) {
            return true;
        }
        if (data.getUUID(OWNER).equals(maid.getUUID())) {
            return true;
        }

        return grinderBlockEntity.getLevel() != null
                && grinderBlockEntity.getLevel().getGameTime() - data.getLong(TOUCHED) > LOCK_TIMEOUT;
    }

    /** 选中这台机器后再调用；抢不到返回 false */
    public static boolean claim(GrinderBlockEntity grinderBlockEntity, EntityMaid maid) {
        if (!isAvailable(grinderBlockEntity, maid)) {
            return false;
        }
        grinderBlockEntity.getPersistentData().putUUID(OWNER, maid.getUUID());
        touch(grinderBlockEntity);
        //让Forge把ForgeData写回NBT
        grinderBlockEntity.setChanged();
        return true;
    }

    /** 续租：每次有效动作刷新一次*/
    public static void touch(GrinderBlockEntity grinderBlockEntity) {
        if (grinderBlockEntity.getLevel() == null) {
            return;
        }
        grinderBlockEntity.getPersistentData().putLong(TOUCHED, grinderBlockEntity.getLevel().getGameTime());
    }

    //背包里第一个本模式的配件槽位，没有则返回-1
    private static int firstTipSlot(EntityMaid maid, MaidGrinderMode mode) {
        IItemHandler inventory = maid.getAvailableInv(true);
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (mode.isTip(inventory.getStackInSlot(i))) {
                return i;
            }
        }
        return -1;
    }

    //背包里能放进这台机器、且数量够一轮的原料槽位,没有则返回-1
    private static int findInputSlot(GrinderBlockEntity grinderBlockEntity, ItemStack prode, EntityMaid maid) {
        IItemHandler inventory = maid.getAvailableInv(true);
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            Optional<GrinderRecipe> recipe = grinderBlockEntity.matchRecipe(stack, prode);
            if (recipe.isPresent() && stack.getCount() >= recipe.get().count) {
                return i;
            }
        }
        return -1;
    }

    //背包里第一个肠衣槽位，没有则返回-1
    private static int firstCasingSlot(EntityMaid maid) {
        IItemHandler inventory = maid.getAvailableInv(true);
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (inventory.getStackInSlot(i).is(ButchercraftItemTags.SAUSAGE_CASING)) {
                return i;
            }
        }
        return -1;
    }

    //从背包取一个塞进机器，塞不下就放回原格
    private static boolean moveOne(EntityMaid maid, int slot, GrinderBlockEntity grinderBlockEntity) {
        IItemHandler inventory = maid.getAvailableInv(true);
        ItemStack taken = inventory.extractItem(slot, 1, false);
        if (taken.isEmpty()) {
            return false;
        }
        ItemStack left = grinderBlockEntity.insertItem(taken);
        if (!left.isEmpty()) {
            inventory.insertItem(slot, left, false);
            return false;
        }
        return true;
    }


}

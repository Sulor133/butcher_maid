package com.github.sulor133.butcher_maid.station;

import com.github.sulor133.butcher_maid.tag.MaidButcherTags;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.lance5057.butchercraft.workstations.bases.recipes.AnimatedRecipeItemUse;
import com.lance5057.butchercraft.workstations.butcherblock.ButcherBlockBlockEntity;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.items.IItemHandler;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * 案板切配：放一份料 -> 按工序换工具 -> 一下一下切 -> 产物进包。
 * 案板一次只放一份料，切完整份料会被吃掉、产物全掉在方块上，所以每轮都要重新放料。
 */
public class MaidBlockWork {
    //Fakeplayer操作
    private static final GameProfile FAKE_PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("butcher_maid:block".getBytes(StandardCharsets.UTF_8))
            ,"[butcher_maid]");

    //占用登记的键
    private static final String OWNER = "butcher_maid_owner";
    private static final String TOUCHED = "butcher_maid_touched";
    //10秒没有操作自动放弃这块案板
    private static final long LOCK_TIMEOUT = 200L;

    private MaidBlockWork() {

    }
    //判断案板是否可用
    public static boolean canWork(ButcherBlockBlockEntity block, EntityMaid maid) {
        return isAvailable(block, maid) && hasWork(block);
    }

    //板上有料，而且还有工序可做
    public static boolean hasWork(ButcherBlockBlockEntity block) {
        return !block.isEmpty() && currentTool(block).isPresent();
    }

    /**
     * 读当前工序。getCurrentTool()内部是 recipeToolsIn.get(stage)，stage越界会抛IndexOutOfBounds异常；
     * 而 NBT里恰好只存了stage（不存 progress/curTool/toolCount），所以配方段数一变就会越界。
     * 越界时归零自愈：料留在板上，下一 tick从第一段重新开始，这一tick先让酒狐停手。
     */
    public static Optional<AnimatedRecipeItemUse> currentTool(ButcherBlockBlockEntity block) {
        try {
            return block.getCurrentTool();
        }
        catch (RuntimeException runtimeException)
        {
            if (block.stage != 0) {
                block.zeroProgress();
                block.setChanged();
            }
            return Optional.empty();
        }
    }

    //判断背包是否有原料
    public static boolean hasInput(ButcherBlockBlockEntity block, EntityMaid maid) {
        return firstInputSlot(block, maid) >= 0;
    }

    //往案板上放一份原料
    public static boolean insertInput(ButcherBlockBlockEntity block, EntityMaid maid) {
        int slot = firstInputSlot(block, maid);
        if (slot < 0) {
            return false;
        }
        IItemHandler inventory = maid.getAvailableInv(true);
        ItemStack taken = inventory.extractItem(slot, 1, false);
        if (taken.isEmpty()) {
            return false;
        }
        //insertItem(ItemStack)是void 的，内部自带isItemValid，塞不进去的余量写回taken
        block.insertItem(taken);
        if (!taken.isEmpty()) {
            inventory.insertItem(slot, taken, false);
            return false;
        }
        return true;
    }

    /**
     * FakePlayer顶替酒狐操作
     * 案板的 butcher()无论真操作了还是被拒（手上工具数量不够），最后都返回 SUCCESS，
     * 不能像肉钩那样看返回值，只能比progress/stage有没有变。
     * @return 这一次操作是否真的推进了
     */
    public static boolean butcher(ServerLevel level, EntityMaid maid, ButcherBlockBlockEntity block) {
        ItemStack tool = maid.getMainHandItem();
        FakePlayer fakePlayer = FakePlayerFactory.get(level, FAKE_PROFILE);
        fakePlayer.setItemSlot(EquipmentSlot.MAINHAND, tool);
        int progressBefore = block.progress;
        int stageBefore = block.stage;
        try {
            block.butcher(fakePlayer, tool);
        }
        finally {
            fakePlayer.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            if (maid.getMainHandItem().isEmpty()) {
                //瓶和桶这类不可损坏的物品是被setCount(0)消耗的
                maid.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        }
        //处理完所有步骤，进度清空
        return block.isEmpty() || block.progress != progressBefore || block.stage != stageBefore;
    }

    //判断这块案板现在是不是当前酒狐占的
    public static boolean isMine(BlockEntity blockEntity, EntityMaid maid) {
        CompoundTag data = blockEntity.getPersistentData();
        return data.hasUUID(OWNER) && data.getUUID(OWNER).equals(maid.getUUID());
    }

    //无主、是当前酒狐和已经超时才可用
    public static boolean isAvailable(BlockEntity blockEntity, EntityMaid maid) {
        CompoundTag data = blockEntity.getPersistentData();
        if (!data.hasUUID(OWNER)) {
            return true;
        }
        if (data.getUUID(OWNER).equals(maid.getUUID())) {
            return true;
        }
        return blockEntity.getLevel() != null
                && blockEntity.getLevel().getGameTime() - data.getLong(TOUCHED) > LOCK_TIMEOUT;
    }

    //选中这块案板后再调用，否则返回false
    public static boolean claim(BlockEntity blockEntity, EntityMaid maid) {
        if (!isAvailable(blockEntity,maid)) {
            return false;
        }
        blockEntity.getPersistentData().putUUID(OWNER, maid.getUUID());
        touch(blockEntity);
        blockEntity.setChanged();       //让Forge将ForgeData写回NBT
        return true;
    }

    //每次操作刷新一次时间
    public static void touch(BlockEntity blockEntity) {
        if (blockEntity.getLevel() == null) {
            return;
        }
        blockEntity.getPersistentData().putLong(TOUCHED, blockEntity.getLevel().getGameTime());
    }

    //离开时主动清除占用
    public static void release(BlockEntity blockEntity, EntityMaid maid) {
        CompoundTag data = blockEntity.getPersistentData();
        if (data.hasUUID(OWNER) && data.getUUID(OWNER).equals(maid.getUUID())) {
            data.remove(OWNER);
            data.remove(TOUCHED);
            blockEntity.setChanged();
        }
    }

    //查找酒狐背包内是否有在标签内案板原料，没有返回-1
    private static int firstInputSlot(ButcherBlockBlockEntity block, EntityMaid maid) {
        IItemHandler inventory = maid.getAvailableInv(true);
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.is(MaidButcherTags.CUTTABLE) && acceptsInput(block, stack)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 检测背包内的物品是否有案板配方
     * 它的ITEM_HANDLER只对非DOWN面暴露，传null才拿得到
     * 传 Direction.DOWN会拿到空Optional。
     */
    private static boolean acceptsInput(ButcherBlockBlockEntity block, ItemStack stack) {
        return block.getCapability(ForgeCapabilities.ITEM_HANDLER, (Direction) null)
                .map(handler -> handler.isItemValid(0, stack))
                .orElse(false);
    }
}

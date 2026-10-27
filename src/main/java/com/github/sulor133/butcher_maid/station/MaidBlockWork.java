package com.github.sulor133.butcher_maid.station;

import com.github.sulor133.butcher_maid.tag.MaidButcherTags;
import com.github.sulor133.butcher_maid.util.MaidFakePlayerUtil;
import com.github.sulor133.butcher_maid.util.MaidInvUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.lance5057.butchercraft.workstations.bases.recipes.AnimatedRecipeItemUse;
import com.lance5057.butchercraft.workstations.butcherblock.ButcherBlockBlockEntity;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Optional;

/**
 * 案板切配：放一份料 -> 按工序换工具 -> 一下一下切 -> 产物进包。
 * 案板一次只放一份料，切完整份料会被吃掉、产物全掉在方块上，所以每轮都要重新放料。
 */
public class MaidBlockWork {
    //FakePlayer 操作
    private static final GameProfile FAKE_PROFILE = MaidFakePlayerUtil.profile("block");

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
     * 读当前工序。getCurrentTool() 内部是 recipeToolsIn.get(stage)，stage 越界会抛 IndexOutOfBounds 异常；
     * 而 NBT 里恰好只存了 stage（不存 progress/curTool/toolCount），所以配方段数一变就会越界。
     * 越界时归零自愈：料留在板上，下一 tick 从第一段重新开始，这一 tick 先让酒狐停手。
     */
    public static Optional<AnimatedRecipeItemUse> currentTool(ButcherBlockBlockEntity block) {
        try {
            return block.getCurrentTool();
        } catch (RuntimeException runtimeException) {
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
        //insertItem(ItemStack) 是 void 的，内部自带 isItemValid，塞不进去的余量写回同一个栈
        return MaidInvUtil.insertOne(maid, firstInputSlot(block, maid), stack -> {
            block.insertItem(stack);
            return stack;
        });
    }

    /**
     * FakePlayer 顶替酒狐操作。
     * 案板的 butcher() 无论真操作了还是被拒（手上工具数量不够），最后都返回 SUCCESS，
     * 不能像肉钩那样看返回值，只能比 progress / stage 有没有变。
     *
     * @return 这一次操作是否真的推进了
     */
    public static boolean butcher(ServerLevel level, EntityMaid maid, ButcherBlockBlockEntity block) {
        int progressBefore = block.progress;
        int stageBefore = block.stage;
        MaidFakePlayerUtil.withMainHand(level, FAKE_PROFILE, maid, (fakePlayer, tool) -> {
            block.butcher(fakePlayer, tool);
            return true;
        });
        //处理完所有步骤，进度清空
        return block.isEmpty() || block.progress != progressBefore || block.stage != stageBefore;
    }

    //判断这块案板现在是不是当前酒狐占的
    public static boolean isMine(BlockEntity blockEntity, EntityMaid maid) {
        return MaidStationLock.isMine(blockEntity, maid);
    }

    //无主、是当前酒狐和已经超时才可用
    public static boolean isAvailable(BlockEntity blockEntity, EntityMaid maid) {
        return MaidStationLock.isAvailable(blockEntity, maid);
    }

    //选中这块案板后再调用，否则返回 false
    public static boolean claim(BlockEntity blockEntity, EntityMaid maid) {
        return MaidStationLock.claim(blockEntity, maid);
    }

    //每次操作刷新一次时间
    public static void touch(BlockEntity blockEntity) {
        MaidStationLock.touch(blockEntity);
    }

    //离开时主动清除占用
    public static void release(BlockEntity blockEntity, EntityMaid maid) {
        MaidStationLock.release(blockEntity, maid);
    }

    //查找酒狐背包内是否有在标签内、且案板收得下的原料，没有返回 -1
    private static int firstInputSlot(ButcherBlockBlockEntity block, EntityMaid maid) {
        return MaidInvUtil.findSlot(maid,
                stack -> stack.is(MaidButcherTags.CUTTABLE) && MaidInvUtil.canInsert(block, stack));
    }
}

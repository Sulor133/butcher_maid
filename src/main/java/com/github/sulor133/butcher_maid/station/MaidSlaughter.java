package com.github.sulor133.butcher_maid.station;

import com.github.sulor133.butcher_maid.tag.MaidButcherTags;
import com.github.sulor133.butcher_maid.util.MaidDropUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 屠刀取胴体：换表 -> 归因 -> 秒杀 -> 收掉落。
 * 女仆不是 Player，归因用 setLastHurtByMob + mobAttack（setLastHurtByPlayer 只收 Player）。
 */
public class MaidSlaughter {
    //直接的秒杀，保证死亡
    private static final float KILL_DAMAGE = 99999.0F;

    private MaidSlaughter() {
    }

    //判断生物是否可被女仆屠宰
    public static boolean canSlaughter(Mob mob) {
        return mob.isAlive() && mob.getType().is(MaidButcherTags.HUNTABLE);
    }

    //标签里的生物不一定都有战利品表
    public static boolean canProduceCarcass(ServerLevel level, Mob mob) {
        return canSlaughter(mob) && lootTableId(level, mob) != null;
    }

    /**
     * 一刀取胴体，掉落直接进女仆背包（装不下的留在地上）。
     *
     * @return 是否真的动了刀。表不存在时返回 false 且不杀
     */
    public static boolean slaughter(ServerLevel level, EntityMaid maid, Mob mob) {
        if (!canSlaughter(mob)) {
            return false;
        }
        ResourceLocation tableId = lootTableId(level, mob);
        if (tableId == null) {
            return false;
        }

        level.playSound(null, mob.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.0F);
        mob.lootTable = tableId;
        mob.setLastHurtByMob(maid);
        mob.hurt(level.damageSources().mobAttack(maid), KILL_DAMAGE);
        MaidDropUtil.collectFreshDrops(level, maid, mob);
        return true;
    }

    /**
     * 自己的表优先（butcher_maid:butcher_knife/...），没有则回退屠宰工艺。
     * 两边都没有 = 这只生物不产出胴体，返回 null 不杀。
     */
    private static ResourceLocation lootTableId(ServerLevel level, Mob mob) {
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (typeId == null) {
            return null;
        }
        String path = typeId.getPath();
        if (mob instanceof Rabbit rabbit) {
            String suffix = rabbitSuffix(rabbit.getVariant());
            if (suffix == null) {
                return null;
            }
            path = "rabbit_" + suffix;
        }

        ResourceLocation own = ResourceLocation.tryBuild("butcher_maid", "butcher_knife/" + path);
        if (own != null && hasTable(level, own)) {
            return own;
        }
        ResourceLocation butchercraft = ResourceLocation.tryBuild("butchercraft", "butcher_knife/" + path);
        if (butchercraft != null && hasTable(level, butchercraft)) {
            return butchercraft;
        }
        return null;
    }

    private static boolean hasTable(ServerLevel level, ResourceLocation id) {
        return level.getServer().getLootData().getLootTable(id) != LootTable.EMPTY;
    }

    //战利品表名：white_splotched 变体在表里叫 splotched
    private static String rabbitSuffix(Rabbit.Variant variant) {
        return switch (variant) {
            case BROWN -> "brown";
            case WHITE -> "white";
            case BLACK -> "black";
            case GOLD -> "gold";
            case SALT -> "salt";
            case WHITE_SPLOTCHED -> "splotched";
            case EVIL -> null;
        };
    }
}

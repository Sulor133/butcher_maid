package com.github.sulor133.butcher_maid.task;

import com.github.sulor133.butcher_maid.behavior.MaidHuntBehavior;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import com.lance5057.butchercraft.Butchercraft;
import com.lance5057.butchercraft.ButchercraftItems;
import com.lance5057.butchercraft.items.ButcherKnifeItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class MaidHuntTask implements IMaidTask{
    public static final ResourceLocation UID = ResourceLocation.tryBuild("butcher_maid", "hunt");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(ButchercraftItems.BUTCHER_KNIFE.get());
    }

    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return InitSounds.MAID_ATTACK.get();
    }

    //走到哪里杀到哪里
    @Override
    public boolean workPointTask(EntityMaid maid){
        return false;
    }

    @Override
    public float searchRadius(EntityMaid maid) {
        return 24.0F;
    }

    //额外条件：主手持有屠刀，判定与 MaidHuntBehavior.holdsKnife 一致
    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        Predicate<EntityMaid> holdsKnife = m -> m.getMainHandItem().getItem() instanceof ButcherKnifeItem;
        return Collections.singletonList(Pair.of("hold_butcher_knife", holdsKnife));
    }

    @Override
    public List<Pair<Integer,BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid){
        BehaviorControl<? super EntityMaid> hunt = new MaidHuntBehavior();
        return Lists.newArrayList(Pair.of(5,hunt));
    }
}

package com.github.sulor133.butcher_maid.datagen;

import com.github.sulor133.butcher_maid.Butcher_Maid;
import com.github.sulor133.butcher_maid.tag.MaidButcherTags;
import com.lance5057.butchercraft.ButchercraftItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;

public class MaidButcherItemTags extends IntrinsicHolderTagsProvider<Item> {

    public MaidButcherItemTags(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> lookup){
        super(packOutput, Registries.ITEM,lookup,
                item -> item.builtInRegistryHolder().key(), Butcher_Maid.MODID,null);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(MaidButcherTags.CARCASSES).add(
                ButchercraftItems.COW_CARCASS.get(),
                ButchercraftItems.PIG_CARCASS.get(),
                ButchercraftItems.SHEEP_CARCASS.get(),
                ButchercraftItems.CHICKEN_CARCASS.get(),
                ButchercraftItems.GOAT_CARCASS.get(),
                ButchercraftItems.BLACK_RABBIT_CARCASS.get(),
                ButchercraftItems.BROWN_RABBIT_CARCASS.get(),
                ButchercraftItems.SPLOTCHED_RABBIT_CARCASS.get(),
                ButchercraftItems.GOLD_RABBIT_CARCASS.get(),
                ButchercraftItems.SALT_RABBIT_CARCASS.get(),
                ButchercraftItems.WHITE_RABBIT_CARCASS.get());

        tag(MaidButcherTags.CUTTABLE).add(
                Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN, Items.RABBIT,
                ButchercraftItems.COW_HEAD_ITEM.get(),
                ButchercraftItems.PIG_HEAD_ITEM.get(),
                ButchercraftItems.SHEEP_HEAD_ITEM.get(),
                ButchercraftItems.GOAT_HEAD_ITEM.get(),
                ButchercraftItems.CHICKEN_HEAD_ITEM.get(),
                ButchercraftItems.RABBIT_BLACK_HEAD_ITEM.get(),
                ButchercraftItems.RABBIT_BROWN_HEAD_ITEM.get(),
                ButchercraftItems.RABBIT_GOLD_HEAD_ITEM.get(),
                ButchercraftItems.RABBIT_SALT_HEAD_ITEM.get(),
                ButchercraftItems.RABBIT_SPLOTCHED_HEAD_ITEM.get(),
                ButchercraftItems.RABBIT_WHITE_HEAD_ITEM.get(),
                ButchercraftItems.SAUSAGE_LINKED.get(),
                ButchercraftItems.BLOOD_SAUSAGE_LINKED.get(),
                ButchercraftItems.CHICKEN_CARCASS.get(),
                ButchercraftItems.BLACK_RABBIT_CARCASS.get(),
                ButchercraftItems.BROWN_RABBIT_CARCASS.get(),
                ButchercraftItems.GOLD_RABBIT_CARCASS.get(),
                ButchercraftItems.SALT_RABBIT_CARCASS.get(),
                ButchercraftItems.SPLOTCHED_RABBIT_CARCASS.get(),
                ButchercraftItems.WHITE_RABBIT_CARCASS.get(),
                ButchercraftItems.TRIPE.get());
    }

    @Override
    public String getName() {
        return "Butter Maid Item Tags";
    }

}

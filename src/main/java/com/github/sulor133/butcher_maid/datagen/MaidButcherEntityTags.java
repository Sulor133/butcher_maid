package com.github.sulor133.butcher_maid.datagen;

import com.github.sulor133.butcher_maid.Butcher_Maid;
import com.github.sulor133.butcher_maid.tag.MaidButcherTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.world.entity.EntityType;

import java.util.concurrent.CompletableFuture;

public class MaidButcherEntityTags extends EntityTypeTagsProvider {
    public MaidButcherEntityTags(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> lookup){
        super(packOutput,lookup, Butcher_Maid.MODID,null);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(MaidButcherTags.HUNTABLE).add(
                EntityType.COW,
                EntityType.SHEEP,
                EntityType.PIG,
                EntityType.CHICKEN,
                EntityType.GOAT,
                EntityType.RABBIT);
    }

    @Override
    public String getName() {
        return "Butter Maid Entity Tags";

    }
}

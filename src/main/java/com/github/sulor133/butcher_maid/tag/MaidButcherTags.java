package com.github.sulor133.butcher_maid.tag;

import com.github.sulor133.butcher_maid.Butcher_Maid;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import java.util.Objects;


public class MaidButcherTags {
    //可狩猎实体
    public static final TagKey<EntityType<?>> HUNTABLE = entity("huntable");

    //胴体物品
    public static final TagKey<Item> CARCASSES = item("carcasses");

    //案板可切配的物品
    public static final TagKey<Item> CUTTABLE = item("cuttable");

    private MaidButcherTags() {
    }

    private static TagKey<EntityType<?>> entity(String string) {
        return TagKey.create(Registries.ENTITY_TYPE,id(string));
    }

    private static TagKey<Item> item(String string) {
        return TagKey.create(Registries.ITEM,id(string));
    }

    private static ResourceLocation id(String string){
        return Objects.requireNonNull(ResourceLocation.tryBuild(Butcher_Maid.MODID, string));
    }
}

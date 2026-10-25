package com.github.sulor133.butcher_maid.datagen;

import com.github.sulor133.butcher_maid.Butcher_Maid;
import net.minecraft.data.DataGenerator;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Butcher_Maid.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DataGenerators {

    private DataGenerators() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();

        generator.addProvider(event.includeServer(),
                new MaidButcherEntityTags(generator.getPackOutput(), event.getLookupProvider()));
        generator.addProvider(event.includeServer(),
                new MaidButcherItemTags(generator.getPackOutput(), event.getLookupProvider()));

        //任务名与描述的 lang json 由数据生成产出
        generator.addProvider(event.includeClient(),
                new MaidButcherLanguage(generator.getPackOutput(), "zh_cn"));
        generator.addProvider(event.includeClient(),
                new MaidButcherLanguage(generator.getPackOutput(), "en_us"));
    }
}

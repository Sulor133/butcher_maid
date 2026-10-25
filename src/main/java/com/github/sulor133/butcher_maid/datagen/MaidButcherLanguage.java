package com.github.sulor133.butcher_maid.datagen;

import com.github.sulor133.butcher_maid.Butcher_Maid;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

/**
 * 一个实例只出一种语言，zh_cn 和 en_us 各注册一次。
 */
public class MaidButcherLanguage extends LanguageProvider {
    private final String localeName;

    public MaidButcherLanguage(PackOutput output, String locale) {
        super(output, Butcher_Maid.MODID, locale);
        this.localeName = locale;
    }

    @Override
    protected void addTranslations() {
        if ("zh_cn".equals(this.localeName)) {
            addChinese();
        } else {
            addEnglish();
        }
    }

    private void addChinese() {
        add("task.butcher_maid.hunt", "狩猎");
        add("task.butcher_maid.hunt.desc", "在附近寻找可屠宰的动物，用屠刀取下胴体");
        add("task.butcher_maid.hunt.condition.hold_butcher_knife", "主手持有屠刀");

        add("task.butcher_maid.hook", "挂肉钩屠宰");
        add("task.butcher_maid.hook.desc", "把背包里的胴体挂上肉钩，按工序换工具完成放血、剥皮、锯骨、穿肠、屠宰");

        add("task.butcher_maid.grind", "绞肉");
        add("task.butcher_maid.grind.desc", "把碎肉放进绞肉机磨成绞肉");

        add("task.butcher_maid.extrude", "灌肠");
        add("task.butcher_maid.extrude.desc", "用灌肠配件把绞肉做成香肠");

        add("task.butcher_maid.board", "案板切配");
        add("task.butcher_maid.board.desc", "在案板上把整块肉、动物头、连串香肠切成成品");
    }

    private void addEnglish() {
        add("task.butcher_maid.hunt", "Hunting");
        add("task.butcher_maid.hunt.desc", "Hunts nearby animals and takes their carcasses with a butcher knife");
        add("task.butcher_maid.hunt.condition.hold_butcher_knife", "Holding a butcher knife in the main hand");

        add("task.butcher_maid.hook", "Meat Hook Butchering");
        add("task.butcher_maid.hook.desc", "Hangs carcasses on a meat hook and works through bleeding, skinning, sawing, gutting and butchering");

        add("task.butcher_maid.grind", "Grinding");
        add("task.butcher_maid.grind.desc", "Grinds scraps into ground meat in a grinder");

        add("task.butcher_maid.extrude", "Sausage Making");
        add("task.butcher_maid.extrude.desc", "Extrudes ground meat into sausages with the extruder attachment");

        add("task.butcher_maid.board", "Block Cutting");
        add("task.butcher_maid.board.desc", "Cuts whole meat, animal heads and linked sausages into products on a butcher block");
    }

    @Override
    public String getName() {
        return "Butcher Maid Language: " + this.localeName;
    }
}

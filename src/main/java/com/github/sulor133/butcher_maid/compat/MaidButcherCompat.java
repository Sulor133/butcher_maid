package com.github.sulor133.butcher_maid.compat;

import com.github.sulor133.butcher_maid.task.*;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;

@LittleMaidExtension
public class MaidButcherCompat implements ILittleMaid {

    @Override
    public void addMaidTask(TaskManager taskManager){
        taskManager.add(new MaidHuntTask());        //获得胴体
        taskManager.add(new MaidHookTask());        //挂肉钩
        taskManager.add(new MaidGrinderTask());     //绞肉
        taskManager.add(new MaidExtrudeTask());     //灌肠
        taskManager.add(new MaidBlockTask());       //案板切配
    }
}


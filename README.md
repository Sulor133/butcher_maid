# Butcher Maid

Vanilla drops only a few cuts of meat from an animal and wastes the rest. **ButcherCraft** adds a process

```
Hunting → Meat Hook → Butcher Block → Grinding → Sausage Making → Butcher Block (unlinking)
```

## Features

| Task | What the maid does |
|---|---|
| **Hunting** | Looks for animals that can be butchered nearby, takes their carcasses with a butcher knife and puts them straight into her backpack |
| **Meat Hook Butchering** | Hangs carcasses on a meat hook and swaps tools by herself to work through bleeding, skinning, sawing, gutting and butchering |
| **Grinding** | Fits the grinder attachment, feeds in scraps and turns the crank, collecting ground meat into her backpack |
| **Sausage Making** | Fits the extruder attachment, keeps 8 ground meat and 1 casing on hand, and extrudes them into links of sausage |
| **Block Cutting** | Cuts whole meat, animal heads and sausage links on the butcher block; fetches glass bottles and water buckets from her backpack when they run out |

- The five tasks are fully independent
- Output goes straight into her backpack; she pauses when it is full and resumes on her own once there is room

## Architecture

```
com.github.sulor133.butcher_maid
├── Butcher_Maid / Config          mod entry and config
├── compat/                        @LittleMaidExtension — registers the five tasks
├── task/                          one IMaidTask per job, declares icon + brain only
├── behavior/                      the AI layer
│   ├── MaidStationBehavior<T>     shared skeleton: find station → walk → act every N ticks
│   ├── MaidHookWorkBehavior
│   ├── MaidGrinderWorkBehavior
│   ├── MaidBlockWorkBehavior
│   └── MaidHuntBehavior           not station-bound, kept separate
├── station/                       domain logic, all static, no AI involved
├── util/                          backpack helpers
├── tag/                           3 tags: huntable / carcasses / cuttable
└── datagen/                       tags and lang are generated
```

## Requirements

| | |
|---|---|
| Minecraft | **1.20.1** |
| Forge | **47.x** |
| [Touhou Little Maid](https://modrinth.com/mod/touhou-little-maid) | **1.5.3+** |
| [ButcherCraft](https://modrinth.com/mod/butchercraft) | **2.4.1 ~ 2.4.x** |

## Credits

- **TartaricAcid** and the Touhou Little Maid team — the maid framework, task API and addon examples
- **Lance5057** — ButcherCraft, along with its workstations and recipes
- **ZUN** — Touhou Project

## License

Code: [MIT](LICENSE). See [LICENSE](LICENSE) for the full text.

---

# 屠夫女仆

原版杀一只动物只掉几块肉，剩下的都浪费了。**屠宰工艺**补上了完整的解体流程，
而这个附属把任务交给女仆

```
狩猎 → 挂肉钩 → 案板 → 绞肉 → 灌肠 → 案板（拆串）
```

## 特性

| 任务 | 女仆会做什么 |
|---|---|
| **狩猎** | 在附近寻找可屠宰的动物，用屠刀取下胴体并直接收进背包 |
| **挂肉钩屠宰** | 把胴体挂上肉钩，按工序自己换工具，完成放血、剥皮、锯骨、穿肠、屠宰 |
| **绞肉** | 自己装上绞肉配件、补碎肉、摇把手，把绞肉收进背包 |
| **灌肠** | 自己装上灌肠配件，保持 8 份绞肉和 1 个肠衣，把肉灌成连串香肠 |
| **案板切配** | 在案板上切整块肉、动物头和连串香肠；缺玻璃瓶和水桶时会自己去背包里拿 |

- 五个任务互相独立
- 产物自动进背包，背包满了会停工，腾出空间后自己复工

## 项目架构

```
com.github.sulor133.butcher_maid
├── Butcher_Maid / Config          模组入口与配置
├── compat/                        @LittleMaidExtension，注册五个任务
├── task/                          每个任务一个 IMaidTask，只声明图标和 brain
├── behavior/                      AI 层
│   ├── MaidStationBehavior<T>     站台通用骨架：找站台 → 走位 → 每 N tick 动手
│   ├── MaidHookWorkBehavior
│   ├── MaidGrinderWorkBehavior
│   ├── MaidBlockWorkBehavior
│   └── MaidHuntBehavior           不受工作点约束，单独一支
├── station/                       领域逻辑，全静态，与 AI 无关
├── util/                          背包相关工具
├── tag/                           三个标签：huntable / carcasses / cuttable
└── datagen/                       标签与语言全部由数据生成
```

## 前置

| | |
|---|---|
| Minecraft | **1.20.1** |
| Forge | **47.x** |
| [车万女仆](https://modrinth.com/mod/touhou-little-maid) | **1.5.3+** |
| [屠宰工艺](https://modrinth.com/mod/butchercraft) | **2.4.1 ~ 2.4.x** |

## 致谢

- **TartaricAcid** 与车万女仆团队 —— 女仆框架、任务 API 与附属示例
- **Lance5057** —— 屠宰工艺，以及它的工作站与配方
- **ZUN** —— 东方 Project

## 许可

代码：[MIT](LICENSE)，完整文本见 [LICENSE](LICENSE)。
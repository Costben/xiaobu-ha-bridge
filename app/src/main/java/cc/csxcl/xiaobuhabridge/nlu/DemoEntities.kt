package cc.csxcl.xiaobuhabridge.nlu

import cc.csxcl.xiaobuhabridge.model.HaEntity

object DemoEntities {
    val all: List<HaEntity> = listOf(
        HaEntity("light.bedroom_ceiling", "light", "卧室吸顶灯", "卧室", listOf("主卧灯", "主卧吸顶灯", "大圆盘")),
        HaEntity("light.bedroom_bedside", "light", "床头台灯", "卧室", listOf("台灯", "床头灯")),
        HaEntity("light.living_main", "light", "客厅主灯", "客厅", listOf("客厅灯", "客厅吸顶灯")),
        HaEntity("light.study_desk", "light", "书房台灯", "书房", listOf("书房灯")),
        HaEntity("climate.living_ac", "climate", "客厅空调", "客厅", listOf("空调")),
        HaEntity("climate.bedroom_ac", "climate", "卧室空调", "卧室", listOf("主卧空调")),
        HaEntity("cover.living_curtain", "cover", "客厅窗帘", "客厅", listOf("窗帘")),
        HaEntity("fan.study_fan", "fan", "书房风扇", "书房", listOf("风扇", "电扇")),
        HaEntity("switch.entryway_outlet", "switch", "玄关插座", "玄关", listOf("插座")),
        HaEntity("scene.good_night", "scene", "晚安", "", listOf("睡觉模式", "我要睡觉了")),
        HaEntity("scene.leave_home", "scene", "离家", "", listOf("出门模式", "我出门了"))
    )
}

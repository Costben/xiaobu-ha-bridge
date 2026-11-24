package cc.csxcl.xiaobuhabridge.nlu

import cc.csxcl.xiaobuhabridge.config.BridgeConfig
import cc.csxcl.xiaobuhabridge.model.ActionType
import cc.csxcl.xiaobuhabridge.model.HaEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNluEngineTest {

    private val engine = LocalNluEngine(
        entities = DemoEntities.all,
        defaultRoom = "卧室",
        customAliases = mapOf("西定灯" to "吸顶灯")
    )

    @Test
    fun `turn off light variations and duplicate verb`() {
        val samples = listOf(
            "帮我把卧室吸顶灯关了",
            "小布小布，关关我的卧室吸顶灯吧",
            "卧室吸顶灯关掉",
            "把卧室那个大圆盘熄了",
            "卧室吸顶灯不用亮着了"
        )
        for (s in samples) {
            val res = engine.parse(s)
            assertNotNull("Expected match for: $s", res)
            assertEquals(ActionType.TURN_OFF, res!!.action)
            assertEquals("light.bedroom_ceiling", res.matchedEntities.first().entityId)
            assertEquals("turn_off", res.serviceCalls.first().service)
            assertEquals("turn_on", res.undoCall?.service)
        }
    }

    @Test
    fun `homophone pinyin tolerance matches misheard device`() {
        val res = engine.parse("把卧室西顶灯关了")
        assertNotNull(res)
        assertEquals("light.bedroom_ceiling", res!!.matchedEntities.first().entityId)
        assertEquals(ActionType.TURN_OFF, res.action)
    }

    @Test
    fun `room wide and house wide off light`() {
        val allLights = engine.parse("全屋关灯")
        assertNotNull(allLights)
        assertEquals(4, allLights!!.matchedEntities.size)

        val bedroomLights = engine.parse("把卧室所有灯关了")
        assertNotNull(bedroomLights)
        assertEquals(2, bedroomLights!!.matchedEntities.size)
        assertTrue(bedroomLights.matchedEntities.all { it.areaName == "卧室" })
    }

    @Test
    fun `climate temperature and brightness and curtain`() {
        val ac = engine.parse("把客厅空调调到26度")
        assertNotNull(ac)
        assertEquals(ActionType.SET_TEMPERATURE, ac!!.action)
        assertEquals("climate.living_ac", ac.matchedEntities.first().entityId)
        assertEquals(26, ac.serviceCalls.first().extraData["temperature"])

        val acCn = engine.parse("卧室空调设为二十五度")
        assertNotNull(acCn)
        assertEquals(25, acCn!!.serviceCalls.first().extraData["temperature"])
        assertEquals("climate.bedroom_ac", acCn.matchedEntities.first().entityId)

        val bright = engine.parse("把书房台灯亮度调到50%")
        assertNotNull(bright)
        assertEquals(ActionType.SET_BRIGHTNESS, bright!!.action)
        assertEquals(50, bright.serviceCalls.first().extraData["brightness_pct"])

        val curtain = engine.parse("拉开客厅窗帘")
        assertNotNull(curtain)
        assertEquals("open_cover", curtain!!.serviceCalls.first().service)
        assertEquals("close_cover", curtain.undoCall?.service)
    }

    @Test
    fun `scene trigger works`() {
        val res = engine.parse("我要睡觉了")
        assertNotNull(res)
        assertEquals(ActionType.ACTIVATE_SCENE, res!!.action)
        assertEquals("scene.good_night", res.matchedEntities.first().entityId)
    }

    @Test
    fun `non smart home and phone system commands must pass through to breeno`() {
        val shouldPassThrough = listOf(
            "播放电影《关灯后》",
            "为什么不要关灯玩手机",
            "今天天气怎么样",
            "打开手电筒",
            "关闭蓝牙",
            "把音量调低一点",
            "定明天早上七点的闹钟"
        )
        for (s in shouldPassThrough) {
            assertNull("Should NOT intercept: $s", engine.parse(s))
        }
    }

    @Test
    fun `entity filter handles domain allowlist, exclude keywords, and manual exclusions`() {
        val raw = listOf(
            HaEntity("light.bedroom_ceiling", "light", "卧室吸顶灯", "卧室"),
            HaEntity("light.router_indicator", "light", "路由器指示灯", "客厅"),
            HaEntity("switch.desk_plug", "switch", "书桌插座", "书房"),
            HaEntity("switch.sensor_calibration", "switch", "人体传感器校准", "走廊"),
            HaEntity("climate.living_ac", "climate", "客厅空调", "客厅")
        )
        val cfg = BridgeConfig(
            allowedDomainsRaw = "light,switch",
            excludedKeywordsRaw = "indicator,sensor,指示灯",
            excludedEntityIdsRaw = "switch.desk_plug"
        )
        val filtered = cfg.filterEntities(raw)
        assertEquals(listOf("light.bedroom_ceiling"), filtered.map { it.entityId })
    }

    @Test
    fun `redundant trailing entity type suffix is stripped from reply and card labels`() {
        val miotEntities = listOf(
            HaEntity("light.yeelight_lamp1_62d0_light", "light", "台灯 灯"),
            HaEntity("light.xi_ding_deng_kai_guan", "light", "吸顶灯 开关"),
            HaEntity("light.ping_mu_gua_deng_kai_guan", "light", "屏幕挂灯 开关")
        )
        val miotEngine = LocalNluEngine(entities = miotEntities)

        val lampOff = miotEngine.parse("把台灯关了")
        assertNotNull(lampOff)
        assertEquals("已关闭台灯", lampOff!!.replyText)
        assertEquals("已关闭 台灯", lampOff.cardTitle)
        assertEquals("台灯 · 已关闭", lampOff.capsuleShortText)

        val monitorOff = miotEngine.parse("关闭屏幕挂灯")
        assertNotNull(monitorOff)
        assertEquals("已关闭屏幕挂灯", monitorOff!!.replyText)
    }

    @Test
    fun `ha entity icon and color roundtrip and resolver work`() {
        val ac = HaEntity(
            entityId = "switch.giot_ac_switch",
            domain = "switch",
            name = "空调开关",
            areaName = "卧室",
            state = "off",
            icon = "mdi:air-conditioner",
            iconColor = "red"
        )
        val restored = HaEntity.fromJson(ac.toJson())
        assertNotNull(restored)
        assertEquals("mdi:air-conditioner", restored!!.icon)
        assertEquals("red", restored.iconColor)

        val redArgb = cc.csxcl.xiaobuhabridge.model.HaIconSpec.parseHaColorArgb("red")
        val orangeArgb = cc.csxcl.xiaobuhabridge.model.HaIconSpec.parseHaColorArgb("Orange")
        val hexArgb = cc.csxcl.xiaobuhabridge.model.HaIconSpec.parseHaColorArgb("#18BCF2")
        assertEquals(0xFFF44336.toInt(), redArgb)
        assertEquals(0xFFFF9800.toInt(), orangeArgb)
        assertEquals(0xFF18BCF2.toInt(), hexArgb)

        val pathAc = cc.csxcl.xiaobuhabridge.model.HaIconSpec.resolveMdiPathData("mdi:air-conditioner", "switch")
        val pathSwitch = cc.csxcl.xiaobuhabridge.model.HaIconSpec.resolveMdiPathData("", "switch")
        assertTrue(pathAc.isNotBlank() && pathAc != pathSwitch)
    }

    @Test
    fun `fluid cloud resolves custom color, domain fallback color, and dynamic icon uri`() {
        val customPayload = cc.csxcl.xiaobuhabridge.fluid.FluidCloudPayload(
            shortCapsuleText = "空调 · 已开",
            title = "已开启 空调",
            subtitle = "卧室",
            domain = "switch",
            isTurnOnStyle = true,
            icon = "mdi:air-conditioner",
            iconColor = "deep-orange"
        )
        assertEquals(
            0xFFFF5722.toInt(),
            cc.csxcl.xiaobuhabridge.fluid.FluidCloudNotifier.resolveAccentColorArgb(customPayload)
        )

        val domainOnPayload = cc.csxcl.xiaobuhabridge.fluid.FluidCloudPayload(
            shortCapsuleText = "风扇 · 已开",
            title = "已开启 风扇",
            subtitle = "书房",
            domain = "fan",
            isTurnOnStyle = true
        )
        assertEquals(
            0xFF009688.toInt(),
            cc.csxcl.xiaobuhabridge.fluid.FluidCloudNotifier.resolveAccentColorArgb(domainOnPayload)
        )

        val domainOffPayload = domainOnPayload.copy(isTurnOnStyle = false)
        assertEquals(
            cc.csxcl.xiaobuhabridge.model.HaIconSpec.INACTIVE_COLOR_ARGB,
            cc.csxcl.xiaobuhabridge.fluid.FluidCloudNotifier.resolveAccentColorArgb(domainOffPayload)
        )

        val customOffPayload = customPayload.copy(isTurnOnStyle = false)
        assertEquals(
            cc.csxcl.xiaobuhabridge.model.HaIconSpec.INACTIVE_COLOR_ARGB,
            cc.csxcl.xiaobuhabridge.fluid.FluidCloudNotifier.resolveAccentColorArgb(customOffPayload)
        )
        assertEquals(
            cc.csxcl.xiaobuhabridge.model.HaIconSpec.INACTIVE_COLOR_ARGB,
            cc.csxcl.xiaobuhabridge.model.HaIconSpec.resolveEntityColorArgb("teal", "fan", "off")
        )
        assertEquals(
            0xFF009688.toInt(),
            cc.csxcl.xiaobuhabridge.model.HaIconSpec.resolveEntityColorArgb("teal", "fan", "on")
        )

        val errorPayload = customPayload.copy(isError = true)
        assertEquals(
            0xFFE53935.toInt(),
            cc.csxcl.xiaobuhabridge.fluid.FluidCloudNotifier.resolveAccentColorArgb(errorPayload)
        )

        val dynamicUri = "icon/${cc.csxcl.xiaobuhabridge.fluid.FluidCloudNotifier.EXTRA_BADGE_ICON}"
        val capsule = cc.csxcl.xiaobuhabridge.fluid.FluidCloudNotifier.buildSymmetricCapsuleJson(
            iconUri = dynamicUri,
            leftText = "空调",
            rightText = "已开",
            rightTextHex = "#FF9E80",
            primaryHex = "#FF5722"
        )
        val card = cc.csxcl.xiaobuhabridge.fluid.FluidCloudNotifier.buildExpandedCardJson(
            iconUri = dynamicUri,
            title = "已开启 空调",
            subtitle = "卧室",
            primaryHex = "#FF5722",
            hasUndo = true
        )
        assertTrue(capsule.contains("\"icon\":\"$dynamicUri\""))
        assertTrue(!capsule.contains("\"intent\""))
        assertTrue(card.contains("\"icon\":\"$dynamicUri\""))
        assertTrue(card.contains("\"intent\":\"pi/ha_open_pi\""))

        val options = cc.csxcl.xiaobuhabridge.fluid.FluidCloudNotifier.buildLiveAlertOptionsJson(
            pkgName = "cc.csxcl.xiaobuhabridge",
            remindType = 0
        )
        assertTrue(options.contains("\"service_from\":3"))
        assertTrue(options.contains("\"remindType\":0"))
    }
}

package cc.csxcl.xiaobuhabridge.nlu

import cc.csxcl.xiaobuhabridge.model.ActionType
import cc.csxcl.xiaobuhabridge.model.HaEntity
import cc.csxcl.xiaobuhabridge.model.HaServiceCall
import cc.csxcl.xiaobuhabridge.model.NluResult

/**
 * 手机端纯本地中文智能家居 NLU 引擎（无外部大模型 API 依赖，< 2ms 解析）：
 * 句式骨架 × 动作同义词 × 噪音词剥离 × 房间/设备/别名动态词典 × 拼音同音纠错
 */
class LocalNluEngine(
    private val entities: List<HaEntity>,
    private val defaultRoom: String = "",
    private val customAliases: Map<String, String> = emptyMap()
) {
    private val controllableDomains = setOf(
        "light", "switch", "climate", "cover", "fan",
        "media_player", "vacuum", "humidifier", "scene",
        "script", "input_boolean", "remote", "water_heater", "lock"
    )

    private val activeEntities: List<HaEntity> = entities.filter { it.domain in controllableDomains }

    /** 明显属于百科/媒体/闲聊的负样本关键词，防止误拦截小布正常请求 */
    private val nonSmartHomeKeywords = listOf(
        "为什么", "怎么办", "是什么", "多少钱", "怎么买", "有什么区别", "区别", "原理",
        "教程", "怎么样", "天气", "新闻", "导航", "路线", "怎么走", "打电话", "拨打",
        "发微信", "发短信", "定闹钟", "设闹钟", "闹钟", "倒计时", "提醒我",
        "播放电影", "看电影", "放电影", "听歌", "放一首歌", "《", "》", "翻译", "写一首"
    )

    /** 手机本机系统控制关键词（除非精确命中 HA 实体全名，否则放行给小布原厂控机） */
    private val phoneSystemKeywords = listOf(
        "手电筒", "闪光灯", "蓝牙", "热点", "个人热点", "飞行模式", "静音", "勿扰",
        "无线网", "wifi", "wi-fi", "移动数据", "流量", "定位", "gps", "nfc",
        "屏幕亮度", "手机亮度", "音量", "媒体音量", "铃声", "自动旋转",
        "护眼模式", "暗色模式", "深色模式", "省电模式", "超级省电", "截屏", "录屏",
        "相机", "微信", "支付宝", "抖音", "设置", "计算器", "日历", "相册"
    )

    private val politePrefixes = listOf(
        "小布小布", "嘿小布", "小布", "你好小布",
        "麻烦帮我", "麻烦给我", "请帮我", "请给我", "帮我把", "给我把", "替我把",
        "麻烦", "请", "帮我", "给我", "替我", "能不能", "可以", "快点", "赶紧", "快", "顺便", "呃", "那个", "喂"
    )

    private val fillerSuffixes = listOf(
        "好不好", "行不行", "可以吗", "好吗", "谢谢你", "谢谢", "一下子", "一下", "吧", "呀", "啊", "嘛", "呗", "嘞", "呢"
    )

    private val determiners = listOf(
        "我的", "家里的", "咱家的", "屋里的", "头顶的", "房间里的", "这个", "那个", "这盏", "那盏", "这台", "那台", "那个"
    )

    private val allScopeWords = listOf("所有", "全部", "全屋", "全家", "整屋", "每一个", "统统", "全都", "全")

    private val builtinRooms = listOf(
        "主卧", "次卧", "卧室", "客厅", "书房", "厨房", "卫生间", "洗手间", "厕所", "浴室",
        "阳台", "玄关", "走廊", "过道", "餐厅", "饭厅", "衣帽间", "儿童房", "客房", "影音室", "车库", "地下室", "门厅"
    )

    private val domainSynonyms: Map<String, List<String>> = mapOf(
        "light" to listOf(
            "吸顶灯", "床头灯", "床头台灯", "落地灯", "小夜灯", "氛围灯", "化妆灯", "浴霸灯",
            "台灯", "主灯", "顶灯", "吊灯", "壁灯", "射灯", "筒灯", "灯带", "夜灯", "电灯", "照明", "灯光", "灯"
        ),
        "climate" to listOf("中央空调", "立式空调", "挂机空调", "空调", "冷气", "暖气", "地暖", "新风"),
        "cover" to listOf("电动窗帘", "百叶窗", "卷帘", "纱帘", "布帘", "窗纱", "窗帘", "帘子"),
        "fan" to listOf("循环扇", "落地扇", "空气循环扇", "吊扇", "电风扇", "电扇", "风扇"),
        "switch" to listOf("智能插座", "插线板", "智能开关", "总开关", "插座", "插排", "开关", "电源", "总闸"),
        "media_player" to listOf("投影仪", "电视机", "投影", "电视", "音箱", "音响", "功放"),
        "vacuum" to listOf("扫地机器人", "扫拖机器人", "扫地机", "洗地机"),
        "humidifier" to listOf("加湿器", "除湿机", "空气净化器", "净化器")
    )

    private val turnOffWords = listOf(
        "关闭", "关掉", "关上", "关了", "关", "熄灭", "熄掉", "熄了", "熄",
        "灭掉", "灭了", "灭", "断电", "断开", "停止", "停掉", "停下", "闭了", "合上", "拉上"
    )

    private val turnOnWords = listOf(
        "打开", "开启", "开了", "开着", "开", "点亮", "亮起来", "启动", "运行", "通电", "拉开", "拉起"
    )

    private val toggleWords = listOf("切换", "反向", "开关一下", "开关")

    fun parse(rawInput: String): NluResult? {
        if (activeEntities.isEmpty()) return null
        val trimmed = rawInput.trim()
        if (trimmed.length < 2 || trimmed.length > 42) return null

        // 1. 负样本拦截：百科、影音、导航等直接放行给小布
        if (nonSmartHomeKeywords.any { trimmed.contains(it) }) return null

        // 2. 清洗文本 + 自定义同义/纠错替换 + 叠词归一化
        val cleaned = normalizeText(trimmed)
        if (cleaned.length < 2) return null

        // 3. 手机系统命令防护（仅当完全匹配某个真实 HA 实体名/别名时才放行给 HA）
        val lowerCleaned = cleaned.lowercase()
        if (phoneSystemKeywords.any { lowerCleaned.contains(it) }) {
            val exactHaHit = activeEntities.any { e ->
                cleaned.contains(e.name) || e.aliases.any { a -> cleaned.contains(a) }
            }
            if (!exactHaHit) return null
        }

        // 4. 高优先：场景 / 脚本匹配（如“晚安”、“我出门了”、“开启回家模式”）
        matchSceneOrScript(trimmed, cleaned)?.let { return it }

        // 5. 数值/属性调节匹配（亮度、温度、窗帘比例、调亮/调暗、调高/调低）
        matchAttributeAdjustment(trimmed, cleaned)?.let { return it }

        // 6. 开关/复合词/否定意图匹配（关灯、把卧室吸顶灯关了、灯别开了、客厅窗帘拉开）
        matchSwitchOrCoverCommand(trimmed, cleaned)?.let { return it }

        return null
    }

    private fun normalizeText(raw: String): String {
        var s = raw
            .replace(Regex("[，。！？、,.!?~～\\s\"'“”‘’]+"), "")
            .trim()

        // 应用用户自定义纠错/别名
        customAliases.forEach { (k, v) ->
            if (s.contains(k)) s = s.replace(k, v)
        }

        // 剥离礼貌/唤醒前缀（多轮直到稳定）
        var changed = true
        while (changed) {
            changed = false
            for (prefix in politePrefixes) {
                if (s.startsWith(prefix) && s.length > prefix.length) {
                    s = s.removePrefix(prefix)
                    changed = true
                    break
                }
            }
        }

        // 剥离语气后缀
        changed = true
        while (changed) {
            changed = false
            for (suffix in fillerSuffixes) {
                if (s.endsWith(suffix) && s.length > suffix.length) {
                    s = s.removeSuffix(suffix)
                    changed = true
                    break
                }
            }
        }

        // 叠词动作归一化：关关→关、开开→开、灭灭→灭
        s = s.replace("关关", "关")
            .replace("开开", "开")
            .replace("灭灭", "灭")
            .replace("熄熄", "熄")

        return s
    }

    private fun matchSceneOrScript(original: String, cleaned: String): NluResult? {
        val sceneEntities = activeEntities.filter { it.domain == "scene" || it.domain == "script" }
        if (sceneEntities.isEmpty()) return null

        val stripped = cleaned
            .removePrefix("打开").removePrefix("开启").removePrefix("启动")
            .removePrefix("执行").removePrefix("运行").removePrefix("进入")
            .removeSuffix("模式").removeSuffix("场景")

        for (entity in sceneEntities) {
            val candidates = buildList {
                add(entity.name)
                add(entity.name.removeSuffix("模式").removeSuffix("场景"))
                entity.aliases.forEach {
                    add(it)
                    add(it.removeSuffix("模式").removeSuffix("场景"))
                }
            }.filter { it.isNotEmpty() }

            val hit = candidates.any { c ->
                cleaned == c || stripped == c ||
                    PinyinHelper.isPinyinSimilar(cleaned, c) ||
                    (stripped.length >= 2 && PinyinHelper.isPinyinSimilar(stripped, c))
            }
            if (hit) {
                val call = HaServiceCall(entity.domain, "turn_on", listOf(entity.entityId))
                val label = entity.displayName
                return NluResult(
                    originalText = original,
                    action = ActionType.ACTIVATE_SCENE,
                    matchedEntities = listOf(entity),
                    serviceCalls = listOf(call),
                    undoCall = null,
                    replyText = "已执行${label}",
                    capsuleShortText = "${shortName(label)} · 已触发",
                    cardTitle = "已执行 $label",
                    cardSubtitle = "场景触发完成",
                    isTurnOnStyle = true
                )
            }
        }
        return null
    }

    private fun matchAttributeAdjustment(original: String, cleaned: String): NluResult? {
        // 5.1 空调温度设定：把卧室空调调到26度 / 空调设二十五度
        val tempRegex = Regex("^(?:把|将)?(.*?)(?:温度)?(?:调到|调为|调成|调至|设为|设置为|设到|定在|改成|开到|调)?([0-9零一二两三四五六七八九十]{1,3})度$")
        tempRegex.find(cleaned)?.let { m ->
            val targetRaw = m.groupValues[1]
            val value = parseChineseOrArabicNumber(m.groupValues[2])
            if (value != null && value in 16..32) {
                val targets = resolveTargets(targetRaw, preferredDomain = "climate")
                if (targets.isNotEmpty()) {
                    val calls = listOf(
                        HaServiceCall("climate", "set_temperature", targets.map { it.entityId }, mapOf("temperature" to value))
                    )
                    val title = formatTargetsLabel(targets)
                    return NluResult(
                        originalText = original,
                        action = ActionType.SET_TEMPERATURE,
                        matchedEntities = targets,
                        serviceCalls = calls,
                        undoCall = null,
                        replyText = "已将${title}设为${value}度",
                        capsuleShortText = "${shortName(title)} · ${value}°C",
                        cardTitle = "$title 已设为 ${value}°C",
                        cardSubtitle = "空调温度调节",
                        isTurnOnStyle = true
                    )
                }
            }
        }

        // 5.2 亮度/窗帘百分比设定：把卧室灯亮度调到50% / 窗帘打开一半
        val pctRegex = Regex("^(?:把|将)?(.*?)(?:的)?(?:亮度|灯光|开合度|位置)?(?:调到|调为|调成|调至|设为|设置为|设到|改成|开到|打开到|打开)?([0-9零一二两三四五六七八九十百]{1,4}|一半)(?:%|百分之)?$")
        if (cleaned.contains("亮度") || cleaned.contains("%") || cleaned.contains("百分之") || cleaned.contains("一半")) {
            val normalizedPct = cleaned.replace(Regex("百分之([0-9零一二两三四五六七八九十百]+)"), "$1%")
            pctRegex.find(normalizedPct)?.let { m ->
                val targetRaw = m.groupValues[1]
                val numRaw = m.groupValues[2]
                val pct = if (numRaw == "一半") 50 else parseChineseOrArabicNumber(numRaw)
                if (pct != null && pct in 0..100) {
                    val isCoverHint = targetRaw.contains("帘") || targetRaw.contains("窗")
                    val domain = if (isCoverHint) "cover" else "light"
                    val targets = resolveTargets(targetRaw, preferredDomain = domain)
                    if (targets.isNotEmpty()) {
                        val title = formatTargetsLabel(targets)
                        return if (targets.first().domain == "cover") {
                            val calls = listOf(
                                HaServiceCall("cover", "set_cover_position", targets.map { it.entityId }, mapOf("position" to pct))
                            )
                            NluResult(
                                originalText = original,
                                action = ActionType.SET_COVER_POSITION,
                                matchedEntities = targets,
                                serviceCalls = calls,
                                undoCall = null,
                                replyText = "已将${title}调整到百分之${pct}",
                                capsuleShortText = "${shortName(title)} · ${pct}%",
                                cardTitle = "$title 已调至 ${pct}%",
                                cardSubtitle = "窗帘位置调节",
                                isTurnOnStyle = pct > 0
                            )
                        } else {
                            val calls = listOf(
                                HaServiceCall("light", "turn_on", targets.map { it.entityId }, mapOf("brightness_pct" to pct))
                            )
                            NluResult(
                                originalText = original,
                                action = ActionType.SET_BRIGHTNESS,
                                matchedEntities = targets,
                                serviceCalls = calls,
                                undoCall = null,
                                replyText = "已将${title}亮度调到百分之${pct}",
                                capsuleShortText = "${shortName(title)} · 亮度${pct}%",
                                cardTitle = "$title 亮度 ${pct}%",
                                cardSubtitle = "灯光亮度调节",
                                isTurnOnStyle = pct > 0
                            )
                        }
                    }
                }
            }
        }

        // 5.3 相对亮度调节：调亮一点 / 太暗了 / 调暗一点 / 太亮了 / 刺眼 / 最亮 / 最暗
        val brightUpWords = listOf("调亮一点", "亮一点", "更亮", "再亮些", "再亮一点", "调亮", "最亮", "开满", "太暗了", "太黑了")
        val brightDownWords = listOf("调暗一点", "暗一点", "更暗", "再暗些", "再暗一点", "调暗", "最暗", "微亮", "太亮了", "刺眼")
        for (w in brightUpWords) {
            if (cleaned.contains(w)) {
                val targetRaw = cleaned.replace(w, "").replace("亮度", "").removePrefix("把").removePrefix("将")
                val targets = resolveTargets(targetRaw, preferredDomain = "light")
                if (targets.isNotEmpty()) {
                    val isMax = w == "最亮" || w == "开满"
                    val data = if (isMax) mapOf("brightness_pct" to 100) else mapOf("brightness_step_pct" to 25)
                    val title = formatTargetsLabel(targets)
                    return NluResult(
                        originalText = original,
                        action = ActionType.BRIGHTNESS_UP,
                        matchedEntities = targets,
                        serviceCalls = listOf(HaServiceCall("light", "turn_on", targets.map { it.entityId }, data)),
                        undoCall = HaServiceCall("light", "turn_on", targets.map { it.entityId }, mapOf("brightness_step_pct" to -25)),
                        replyText = if (isMax) "已将${title}调至最亮" else "已将${title}调亮一点",
                        capsuleShortText = "${shortName(title)} · 已调亮",
                        cardTitle = "$title 已调亮",
                        cardSubtitle = if (isMax) "亮度 100%" else "亮度 +25%",
                        isTurnOnStyle = true
                    )
                }
            }
        }
        for (w in brightDownWords) {
            if (cleaned.contains(w)) {
                val targetRaw = cleaned.replace(w, "").replace("亮度", "").removePrefix("把").removePrefix("将")
                val targets = resolveTargets(targetRaw, preferredDomain = "light")
                if (targets.isNotEmpty()) {
                    val isMin = w == "最暗" || w == "微亮"
                    val data = if (isMin) mapOf("brightness_pct" to 10) else mapOf("brightness_step_pct" to -25)
                    val title = formatTargetsLabel(targets)
                    return NluResult(
                        originalText = original,
                        action = ActionType.BRIGHTNESS_DOWN,
                        matchedEntities = targets,
                        serviceCalls = listOf(HaServiceCall("light", "turn_on", targets.map { it.entityId }, data)),
                        undoCall = HaServiceCall("light", "turn_on", targets.map { it.entityId }, mapOf("brightness_step_pct" to 25)),
                        replyText = if (isMin) "已将${title}调至微亮" else "已将${title}调暗一点",
                        capsuleShortText = "${shortName(title)} · 已调暗",
                        cardTitle = "$title 已调暗",
                        cardSubtitle = if (isMin) "亮度 10%" else "亮度 -25%",
                        isTurnOnStyle = false
                    )
                }
            }
        }

        return null
    }

    private fun matchSwitchOrCoverCommand(original: String, cleaned: String): NluResult? {
        var action: ActionType? = null
        var targetPart = ""
        var preferredDomain: String? = null

        // A. 缩略复合词：关灯 / 熄灯 / 灭灯 / 开灯 / 亮灯（如 "卧室关灯"、"全屋熄灯"、"关灯"）
        val offLightRegex = Regex("^(.*?)(?:关灯|熄灯|灭灯)$")
        val onLightRegex = Regex("^(.*?)(?:开灯|亮灯)$")
        offLightRegex.find(cleaned)?.let { m ->
            action = ActionType.TURN_OFF
            targetPart = m.groupValues[1] + "灯"
            preferredDomain = "light"
        }
        if (action == null) {
            onLightRegex.find(cleaned)?.let { m ->
                action = ActionType.TURN_ON
                targetPart = m.groupValues[1] + "灯"
                preferredDomain = "light"
            }
        }

        // B. 否定意图式：卧室灯别开了 / 吸顶灯不用亮着了 -> TURN_OFF；灯别关了 -> TURN_ON
        if (action == null) {
            val negOffRegex = Regex("^(?:把|将)?(.+?)(?:不要|别|不用)(?:开|开了|开着|亮|亮着|运行)(?:了)?$")
            val negOnRegex = Regex("^(?:把|将)?(.+?)(?:不要|别|不用)(?:关|关了|关掉|熄|灭)(?:了)?$")
            negOffRegex.find(cleaned)?.let {
                action = ActionType.TURN_OFF
                targetPart = it.groupValues[1]
            }
            if (action == null) {
                negOnRegex.find(cleaned)?.let {
                    action = ActionType.TURN_ON
                    targetPart = it.groupValues[1]
                }
            }
        }

        // C. 把字句：把 + {TARGET} + [给]? + {ACTION}
        if (action == null && (cleaned.startsWith("把") || cleaned.startsWith("将"))) {
            val body = cleaned.substring(1)
            val parsed = extractTrailingAction(body)
            if (parsed != null) {
                action = parsed.first
                targetPart = parsed.second.removeSuffix("给")
            }
        }

        // D. 动宾式：{ACTION} + {TARGET}
        if (action == null) {
            val parsed = extractLeadingAction(cleaned)
            if (parsed != null) {
                action = parsed.first
                targetPart = parsed.second
            }
        }

        // E. 主谓倒装式：{TARGET} + {ACTION}（如 "卧室灯关掉"、"客厅窗帘拉开"）
        if (action == null) {
            val parsed = extractTrailingAction(cleaned)
            if (parsed != null) {
                action = parsed.first
                targetPart = parsed.second.removeSuffix("给")
            }
        }

        val finalAction = action ?: return null
        if (targetPart.isBlank()) return null

        val targets = resolveTargets(targetPart, preferredDomain = preferredDomain)
        if (targets.isEmpty()) return null

        return buildSwitchOrCoverResult(original, finalAction, targets)
    }

    private fun extractLeadingAction(text: String): Pair<ActionType, String>? {
        for (w in toggleWords) {
            if (text.startsWith(w) && text.length > w.length) {
                return ActionType.TOGGLE to text.removePrefix(w)
            }
        }
        for (w in turnOffWords) {
            if (text.startsWith(w) && text.length > w.length) {
                return ActionType.TURN_OFF to text.removePrefix(w)
            }
        }
        for (w in turnOnWords) {
            if (text.startsWith(w) && text.length > w.length) {
                return ActionType.TURN_ON to text.removePrefix(w)
            }
        }
        if ((text.startsWith("暂停") || text.startsWith("停住")) && text.length > 2) {
            return ActionType.PAUSE to text.substring(2)
        }
        return null
    }

    private fun extractTrailingAction(text: String): Pair<ActionType, String>? {
        for (w in toggleWords) {
            if (text.endsWith(w) && text.length > w.length) {
                return ActionType.TOGGLE to text.removeSuffix(w)
            }
        }
        for (w in turnOffWords) {
            if (text.endsWith(w) && text.length > w.length) {
                return ActionType.TURN_OFF to text.removeSuffix(w)
            }
        }
        for (w in turnOnWords) {
            if (text.endsWith(w) && text.length > w.length) {
                return ActionType.TURN_ON to text.removeSuffix(w)
            }
        }
        if ((text.endsWith("暂停") || text.endsWith("停住")) && text.length > 2) {
            return ActionType.PAUSE to text.substring(0, text.length - 2)
        }
        return null
    }

    /**
     * 核心实体解析器：
     * 1. 提取全局范围词（所有/全部/全屋）
     * 2. 提取房间槽位（动态 HA 房间 + 内置房间 + 拼音容错）
     * 3. 清洗限定词后，综合实体名称、别名、拼音相似度、品类同义词打分选出最优目标
     */
    private fun resolveTargets(rawTarget: String, preferredDomain: String? = null): List<HaEntity> {
        var text = rawTarget.trim()
        var isAllScope = false
        for (sw in allScopeWords) {
            if (text.contains(sw)) {
                isAllScope = true
                text = text.replace(sw, "")
            }
        }
        for (det in determiners) {
            text = text.replace(det, "")
        }
        text = text.replace("的", "").trim()

        // 动态收集所有已知房间名（优先用户 HA 实际同步的 areaName）
        val knownRooms = (activeEntities.map { it.areaName }.filter { it.isNotEmpty() } + builtinRooms)
            .distinct()
            .sortedByDescending { it.length }

        var matchedRoom: String? = null
        for (room in knownRooms) {
            if (text.contains(room)) {
                matchedRoom = room
                text = text.replace(room, "").trim()
                break
            }
        }
        // 拼音房间容错（例如把“玄关”听成“悬关”）
        if (matchedRoom == null && text.length >= 2) {
            for (room in knownRooms) {
                if (room.length >= 2 && text.length >= room.length) {
                    val prefix = text.substring(0, room.length)
                    if (PinyinHelper.isPinyinSimilar(prefix, room)) {
                        matchedRoom = room
                        text = text.substring(room.length).trim()
                        break
                    }
                }
            }
        }

        // 先尝试整串精确/别名/拼音匹配（包含房间名在内的完整命名，如“卧室吸顶灯”、“大圆盘”）
        val fullCleaned = rawTarget.replace("的", "").let { s ->
            var r = s
            determiners.forEach { r = r.replace(it, "") }
            allScopeWords.forEach { r = r.replace(it, "") }
            r.trim()
        }

        // 判断用户是否说了某个通用设备品类（如 "灯" / "空调" / "窗帘" / "风扇"）
        val detectedDomain = preferredDomain ?: detectDomainFromDeviceText(text)
            ?: detectDomainFromDeviceText(fullCleaned)

        // 场景 1：如果用户只说了“全屋关灯”或“把卧室所有灯关了”
        if (isAllScope && detectedDomain != null) {
            val filtered = activeEntities.filter { entity ->
                entity.domain == detectedDomain && (matchedRoom == null || roomMatches(entity, matchedRoom))
            }
            if (filtered.isNotEmpty()) return filtered
        }

        // 场景 2：逐个实体打分
        data class Scored(val entity: HaEntity, val score: Int)
        val scoredList = mutableListOf<Scored>()

        for (entity in activeEntities) {
            if (entity.domain == "scene" || entity.domain == "script") continue

            var score = 0
            val name = entity.name
            val displayName = entity.displayName
            val aliases = entity.aliases
            val roomHit = matchedRoom != null && roomMatches(entity, matchedRoom)

            // 名称 / 别名 完整匹配（最高权）
            if (fullCleaned.isNotEmpty() && (name == fullCleaned || displayName == fullCleaned || aliases.any { it == fullCleaned })) {
                score += 120
            } else if (text.isNotEmpty() && (name == text || displayName == text || aliases.any { it == text })) {
                score += 110
            } else if (fullCleaned.length >= 2 && (PinyinHelper.isPinyinSimilar(fullCleaned, name) || PinyinHelper.isPinyinSimilar(fullCleaned, displayName) || aliases.any { PinyinHelper.isPinyinSimilar(fullCleaned, it) })) {
                score += 100
            } else if (text.length >= 2 && (PinyinHelper.isPinyinSimilar(text, name) || PinyinHelper.isPinyinSimilar(text, displayName) || aliases.any { PinyinHelper.isPinyinSimilar(text, it) })) {
                score += 95
            } else if (text.length >= 2 && (name.contains(text) || aliases.any { it.contains(text) })) {
                score += 80
            } else if (text.length >= 2 && pinyinContains(name, text)) {
                score += 75
            } else if (detectedDomain != null && entity.domain == detectedDomain) {
                // 仅品类命中（例如说了“关卧室灯”或“关灯”）
                val isPureGeneric = isGenericDomainWord(text, detectedDomain) || text.isEmpty()
                if (isPureGeneric) {
                    score += 45
                }
            }

            if (score == 0) continue

            // 房间加权 / 强过滤
            if (matchedRoom != null) {
                if (roomHit) {
                    score += 40
                } else {
                    // 明确指定了其他房间，大幅降权（避免“关卧室灯”误关客厅灯）
                    score -= 70
                }
            } else if (defaultRoom.isNotEmpty() && roomMatches(entity, defaultRoom)) {
                // 没说房间时优先默认房间
                score += 20
            }

            // 品类一致微加分
            if (detectedDomain != null && entity.domain == detectedDomain) {
                score += 10
            }

            if (score >= 40) {
                scoredList.add(Scored(entity, score))
            }
        }

        if (scoredList.isEmpty()) return emptyList()

        scoredList.sortByDescending { it.score }
        val topScore = scoredList.first().score

        // 如果说了具体房间 + 泛指品类（如“关卧室灯”），该房间下所有同分灯一起控制；
        // 如果是“把卧室主灯关了”这种精确命中单个实体的，只控最高分那一个
        return if (topScore < 90 && (matchedRoom != null || isAllScope)) {
            scoredList.filter { it.score >= topScore - 5 }.map { it.entity }
        } else {
            listOf(scoredList.first().entity)
        }
    }

    private fun roomMatches(entity: HaEntity, room: String): Boolean {
        if (entity.areaName.equals(room, ignoreCase = true)) return true
        if (entity.areaName.isNotEmpty() && (entity.areaName.contains(room) || room.contains(entity.areaName))) return true
        if (entity.name.contains(room) || entity.aliases.any { it.contains(room) }) return true
        // 主卧 <-> 卧室 常见互通
        if ((room == "主卧" && entity.areaName == "卧室") || (room == "卧室" && entity.areaName == "主卧")) return true
        return false
    }

    private fun detectDomainFromDeviceText(text: String): String? {
        if (text.isEmpty()) return null
        for ((domain, words) in domainSynonyms) {
            if (words.any { text.contains(it) }) return domain
        }
        return null
    }

    private fun isGenericDomainWord(text: String, domain: String): Boolean {
        val words = domainSynonyms[domain] ?: return false
        return words.any { it == text }
    }

    private fun pinyinContains(entityName: String, query: String): Boolean {
        val pe = PinyinHelper.toPinyin(entityName)
        val pq = PinyinHelper.toPinyin(query)
        if (pe.isEmpty() || pq.length < 4) return false
        if (pe.contains(pq)) return true
        // 滑动窗口检查同长度片段编辑距离 <= 1（解决 "卧室西顶灯" 命中 "卧室吸顶灯"）
        if (query.length >= 2 && entityName.length >= query.length) {
            for (i in 0..(entityName.length - query.length)) {
                val sub = entityName.substring(i, i + query.length)
                if (PinyinHelper.isPinyinSimilar(sub, query)) return true
            }
        }
        return false
    }

    private fun buildSwitchOrCoverResult(
        original: String,
        action: ActionType,
        targets: List<HaEntity>
    ): NluResult {
        val grouped = targets.groupBy { it.domain }
        val calls = mutableListOf<HaServiceCall>()
        var undoCall: HaServiceCall? = null

        for ((domain, list) in grouped) {
            val ids = list.map { it.entityId }
            val (service, undoService) = when (domain) {
                "cover" -> when (action) {
                    ActionType.TURN_ON, ActionType.OPEN_COVER -> "open_cover" to "close_cover"
                    ActionType.TURN_OFF, ActionType.CLOSE_COVER -> "close_cover" to "open_cover"
                    ActionType.PAUSE, ActionType.STOP_COVER -> "stop_cover" to null
                    ActionType.TOGGLE -> "toggle" to "toggle"
                    else -> "open_cover" to "close_cover"
                }
                "vacuum" -> when (action) {
                    ActionType.TURN_ON -> "start" to "return_to_base"
                    ActionType.TURN_OFF -> "return_to_base" to "start"
                    ActionType.PAUSE -> "pause" to "start"
                    else -> "start" to "pause"
                }
                else -> when (action) {
                    ActionType.TURN_ON -> "turn_on" to "turn_off"
                    ActionType.TURN_OFF -> "turn_off" to "turn_on"
                    ActionType.TOGGLE -> "toggle" to "toggle"
                    ActionType.PAUSE -> "media_pause" to "media_play"
                    else -> "turn_on" to "turn_off"
                }
            }
            calls.add(HaServiceCall(domain, service, ids))
            if (undoCall == null && undoService != null) {
                undoCall = HaServiceCall(domain, undoService, ids)
            }
        }

        val label = formatTargetsLabel(targets)
        val (verbCn, shortStatus, isOnStyle) = when (action) {
            ActionType.TURN_ON, ActionType.OPEN_COVER -> Triple("已打开", "已开启", true)
            ActionType.TURN_OFF, ActionType.CLOSE_COVER -> Triple("已关闭", "已关闭", false)
            ActionType.TOGGLE -> Triple("已切换", "已切换", true)
            ActionType.PAUSE, ActionType.STOP_COVER -> Triple("已暂停", "已暂停", false)
            else -> Triple("已执行", "已完成", true)
        }

        val areaHint = targets.firstOrNull()?.areaName.orEmpty().ifBlank { "Home Assistant" }
        return NluResult(
            originalText = original,
            action = action,
            matchedEntities = targets,
            serviceCalls = calls,
            undoCall = undoCall,
            replyText = "${verbCn}${label}",
            capsuleShortText = "${shortName(label)} · $shortStatus",
            cardTitle = "$verbCn $label",
            cardSubtitle = if (targets.size > 1) "$areaHint · 共 ${targets.size} 个设备" else areaHint,
            isTurnOnStyle = isOnStyle
        )
    }

    private fun formatTargetsLabel(targets: List<HaEntity>): String {
        return when {
            targets.isEmpty() -> "设备"
            targets.size == 1 -> targets.first().displayName
            targets.size == 2 -> "${targets[0].displayName}和${targets[1].displayName}"
            else -> "${targets.first().areaName.ifBlank { "全部" }}${targets.size}个设备"
        }
    }

    private fun shortName(name: String): String {
        return if (name.length <= 6) name else name.take(6)
    }

    private fun parseChineseOrArabicNumber(raw: String): Int? {
        raw.toIntOrNull()?.let { return it }
        val digits = mapOf(
            '零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
        )
        if (raw == "一百") return 100
        if (raw == "十") return 10
        val tenIdx = raw.indexOf('十')
        if (tenIdx >= 0) {
            val tens = if (tenIdx == 0) 1 else (digits[raw[tenIdx - 1]] ?: return null)
            val ones = if (tenIdx == raw.lastIndex) 0 else (digits[raw[tenIdx + 1]] ?: return null)
            return tens * 10 + ones
        }
        if (raw.length == 1) return digits[raw[0]]
        return null
    }
}

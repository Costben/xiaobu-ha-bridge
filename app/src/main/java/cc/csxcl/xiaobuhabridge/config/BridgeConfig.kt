package cc.csxcl.xiaobuhabridge.config

import cc.csxcl.xiaobuhabridge.model.HaEntity

enum class FeedbackMode(val id: Int) {
    /** 极简无感：弹流体云 + 静默立刻收起小布（最像原生系统控灯） */
    FLUID_SILENT_DISMISS(0),

    /** 流体云 + 小布气泡 + 本地离线零延迟原厂 TTS + 播完自动收起 */
    FLUID_AND_LOCAL_TTS(1),

    /** 流体云 + 小布气泡 + 原厂当前默认（云端）音色 TTS + 播完自动收起 */
    FLUID_AND_CLOUD_TTS(2),

    /** 流体云 + 小布气泡（不播报语音，不强行收起） */
    FLUID_AND_BUBBLE_ONLY(3);

    companion object {
        fun fromId(id: Int): FeedbackMode = entries.firstOrNull { it.id == id } ?: FLUID_AND_LOCAL_TTS
    }
}

enum class FluidCloudDisplayMode(val id: Int, val remindType: Int) {
    /** 直接以展开卡片强提醒形式弹出（约 5 秒后自动折叠或随超时收回） */
    EXPANDED_CARD(0, 12),

    /** 摄像头两侧对称紧凑胶囊（左：图标+短设备名，右：短状态词，不遮挡屏幕） */
    SYMMETRIC_CAPSULE(1, 0),

    /** 停留期间持续以展开卡片展示，直到设定时长结束或手动收起 */
    ALWAYS_EXPANDED(2, 13);

    companion object {
        fun fromId(id: Int): FluidCloudDisplayMode = entries.firstOrNull { it.id == id } ?: EXPANDED_CARD
    }
}

data class BridgeConfig(
    val enabled: Boolean = true,
    val haLanUrl: String = "",
    val haExternalUrl: String = "",
    val haToken: String = "",
    val defaultRoom: String = "",
    val feedbackMode: FeedbackMode = FeedbackMode.FLUID_AND_LOCAL_TTS,
    val enableFluidCloud: Boolean = true,
    val fluidCloudDisplayMode: FluidCloudDisplayMode = FluidCloudDisplayMode.EXPANDED_CARD,
    val fluidCloudDurationMs: Long = 4500L,
    val enableLocalAssistFallback: Boolean = false,
    val assistTriggerPrefixes: String = "家里,全屋,查一下家里",
    val demoMode: Boolean = true,
    val customAliasesRaw: String = "",
    /** 允许控制与展示的实体类型（逗号分隔；留空表示全部可控类型） */
    val allowedDomainsRaw: String = "",
    /** 排除关键词（逗号/换行分隔，命中 entity_id 或名称即过滤，如 indicator,sensor,指示灯） */
    val excludedKeywordsRaw: String = "",
    /** 手动屏蔽的 entity_id 列表（换行分隔） */
    val excludedEntityIdsRaw: String = "",
    val debugLog: Boolean = true
) {
    fun hasRealHaConfigured(): Boolean =
        haToken.isNotBlank() && (haLanUrl.isNotBlank() || haExternalUrl.isNotBlank())

    fun normalizedUrls(): List<String> = listOf(haLanUrl, haExternalUrl)
        .map { it.trim().trimEnd('/') }
        .filter { it.startsWith("http://") || it.startsWith("https://") }
        .distinct()

    fun parsedCustomAliases(): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        customAliasesRaw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { line ->
                val idx = line.indexOf('=').takeIf { it > 0 } ?: line.indexOf('：').takeIf { it > 0 } ?: line.indexOf(':')
                if (idx > 0 && idx < line.lastIndex) {
                    val key = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1).trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) {
                        map[key] = value
                    }
                }
            }
        return map
    }

    fun parsedAssistPrefixes(): List<String> =
        assistTriggerPrefixes.split(',', '，', ';', '；')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun parsedAllowedDomains(): Set<String> =
        allowedDomainsRaw.split(',', '，', ';', '；', '\n', ' ')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toCollection(LinkedHashSet())

    fun parsedExcludedKeywords(): List<String> =
        excludedKeywordsRaw.split(',', '，', ';', '；', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

    fun parsedExcludedEntityIds(): Set<String> =
        excludedEntityIdsRaw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toCollection(LinkedHashSet())

    fun isEntityAllowedByDomain(entity: HaEntity): Boolean {
        val allowed = parsedAllowedDomains()
        return allowed.isEmpty() || entity.domain.lowercase() in allowed
    }

    fun isEntityExcludedByKeyword(entity: HaEntity): Boolean {
        val keywords = parsedExcludedKeywords()
        if (keywords.isEmpty()) return false
        val idLower = entity.entityId.lowercase()
        val nameLower = entity.name.lowercase()
        return keywords.any { kw ->
            val k = kw.lowercase()
            idLower.contains(k) || nameLower.contains(k)
        }
    }

    fun isEntityManuallyExcluded(entity: HaEntity): Boolean =
        entity.entityId in parsedExcludedEntityIds()

    /**
     * 统一实体过滤：类型白名单 -> 关键词排除 -> 手动屏蔽。
     * 同时用于前端展示、本地 NLU 调试台、以及小布语音 Hook 拦截范围。
     */
    fun filterEntities(raw: List<HaEntity>): List<HaEntity> {
        val allowed = parsedAllowedDomains()
        val keywords = parsedExcludedKeywords().map { it.lowercase() }
        val manualExcluded = parsedExcludedEntityIds()
        return raw.filter { entity ->
            if (allowed.isNotEmpty() && entity.domain.lowercase() !in allowed) return@filter false
            if (entity.entityId in manualExcluded) return@filter false
            if (keywords.isNotEmpty()) {
                val idLower = entity.entityId.lowercase()
                val nameLower = entity.name.lowercase()
                if (keywords.any { k -> idLower.contains(k) || nameLower.contains(k) }) {
                    return@filter false
                }
            }
            true
        }
    }

    fun toggleAllowedDomain(domain: String): BridgeConfig {
        val d = domain.trim().lowercase()
        val next = parsedAllowedDomains().toMutableSet()
        if (d in next) next.remove(d) else next.add(d)
        return copy(allowedDomainsRaw = next.joinToString(","))
    }

    fun toggleExcludedKeyword(keyword: String): BridgeConfig {
        val kw = keyword.trim()
        if (kw.isEmpty()) return this
        val current = parsedExcludedKeywords().toMutableList()
        val existingIdx = current.indexOfFirst { it.equals(kw, ignoreCase = true) }
        if (existingIdx >= 0) {
            current.removeAt(existingIdx)
        } else {
            current.add(kw)
        }
        return copy(excludedKeywordsRaw = current.joinToString(","))
    }

    fun toggleManualExcludeEntity(entityId: String): BridgeConfig {
        val eid = entityId.trim()
        if (eid.isEmpty()) return this
        val next = parsedExcludedEntityIds().toMutableSet()
        if (eid in next) next.remove(eid) else next.add(eid)
        return copy(excludedEntityIdsRaw = next.joinToString("\n"))
    }

    fun addManualExcludeEntities(entityIds: Collection<String>): BridgeConfig {
        if (entityIds.isEmpty()) return this
        val next = parsedExcludedEntityIds().toMutableSet()
        entityIds.map { it.trim() }.filter { it.isNotEmpty() }.forEach { next.add(it) }
        return copy(excludedEntityIdsRaw = next.joinToString("\n"))
    }

    fun removeManualExcludeEntities(entityIds: Collection<String>): BridgeConfig {
        if (entityIds.isEmpty()) return this
        val next = parsedExcludedEntityIds().toMutableSet()
        entityIds.map { it.trim() }.forEach { next.remove(it) }
        return copy(excludedEntityIdsRaw = next.joinToString("\n"))
    }
}

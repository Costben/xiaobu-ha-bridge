package cc.csxcl.xiaobuhabridge.model

import org.json.JSONArray
import org.json.JSONObject

data class HaEntity(
    val entityId: String,
    val domain: String,
    val name: String,
    val areaName: String = "",
    val aliases: List<String> = emptyList(),
    val state: String = "",
    val icon: String = "",
    val iconColor: String = ""
) {
    /** 去掉 HA 自动拼接的尾部空格+实体类型后缀（如「台灯 灯」->「台灯」、「吸顶灯 开关」->「吸顶灯」），用于播报与展示。 */
    val displayName: String
        get() = cleanEntityDisplayName(name)

    fun toJson(): JSONObject = JSONObject().apply {
        put("entity_id", entityId)
        put("domain", domain)
        put("name", name)
        put("area", areaName)
        put("aliases", JSONArray(aliases))
        put("state", state)
        if (icon.isNotBlank()) put("icon", icon)
        if (iconColor.isNotBlank()) put("icon_color", iconColor)
    }

    companion object {
        fun fromJson(obj: JSONObject): HaEntity? {
            val id = obj.optString("entity_id").trim()
            if (id.isEmpty() || !id.contains('.')) return null
            val domain = obj.optString("domain").ifBlank { id.substringBefore('.') }
            val name = obj.optString("name").ifBlank { id.substringAfter('.') }
            val area = obj.optString("area", "").trim()
            val aliasArr = obj.optJSONArray("aliases")
            val aliases = mutableListOf<String>()
            if (aliasArr != null) {
                for (i in 0 until aliasArr.length()) {
                    val a = aliasArr.optString(i).trim()
                    if (a.isNotEmpty()) aliases.add(a)
                }
            }
            return HaEntity(
                entityId = id,
                domain = domain,
                name = name,
                areaName = area,
                aliases = aliases,
                state = obj.optString("state", "").takeUnless { obj.isNull("state") || it == "null" }.orEmpty(),
                icon = obj.optString("icon", "").takeUnless { obj.isNull("icon") || it == "null" }.orEmpty().trim(),
                iconColor = obj.optString("icon_color", "").takeUnless { obj.isNull("icon_color") || it == "null" }.orEmpty().trim()
            )
        }

        fun listToJson(list: List<HaEntity>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(raw: String?): List<HaEntity> {
            if (raw.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(raw)
                buildList(arr.length()) {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { fromJson(it) }?.let { add(it) }
                    }
                }
            } catch (_: Throwable) {
                emptyList()
            }
        }

        private val TRAILING_ENTITY_TYPE_REGEX = Regex(
            "\\s+(?:灯|开关|风扇|空调|窗帘|插座|灯带|面板|按钮|light|switch|fan|cover|climate)$",
            RegexOption.IGNORE_CASE
        )

        fun cleanEntityDisplayName(raw: String): String {
            val trimmed = raw.trim()
            val stripped = trimmed.replace(TRAILING_ENTITY_TYPE_REGEX, "").trim()
            return stripped.ifEmpty { trimmed }
        }
    }
}

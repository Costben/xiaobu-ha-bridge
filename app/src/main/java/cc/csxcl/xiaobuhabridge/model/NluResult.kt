package cc.csxcl.xiaobuhabridge.model

import org.json.JSONObject

enum class ActionType {
    TURN_ON,
    TURN_OFF,
    TOGGLE,
    SET_BRIGHTNESS,
    BRIGHTNESS_UP,
    BRIGHTNESS_DOWN,
    SET_TEMPERATURE,
    TEMP_UP,
    TEMP_DOWN,
    OPEN_COVER,
    CLOSE_COVER,
    STOP_COVER,
    SET_COVER_POSITION,
    ACTIVATE_SCENE,
    PAUSE,
    RESUME
}

data class HaServiceCall(
    val domain: String,
    val service: String,
    val entityIds: List<String>,
    val extraData: Map<String, Any> = emptyMap()
) {
    fun toPayloadJson(): JSONObject = JSONObject().apply {
        if (entityIds.size == 1) {
            put("entity_id", entityIds.first())
        } else if (entityIds.isNotEmpty()) {
            put("entity_id", org.json.JSONArray(entityIds))
        }
        extraData.forEach { (k, v) -> put(k, v) }
    }
}

data class NluResult(
    val originalText: String,
    val action: ActionType,
    val matchedEntities: List<HaEntity>,
    val serviceCalls: List<HaServiceCall>,
    val undoCall: HaServiceCall? = null,
    val replyText: String,
    val capsuleShortText: String,
    val cardTitle: String,
    val cardSubtitle: String,
    val isTurnOnStyle: Boolean
)

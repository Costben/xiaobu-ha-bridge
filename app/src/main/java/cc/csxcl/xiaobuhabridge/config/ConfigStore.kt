package cc.csxcl.xiaobuhabridge.config

import android.content.Context
import android.content.SharedPreferences
import cc.csxcl.xiaobuhabridge.XiaobuHaBridge
import cc.csxcl.xiaobuhabridge.model.HaEntity
import cc.csxcl.xiaobuhabridge.nlu.DemoEntities

object ConfigStore {
    const val PREFS_NAME = "breeno_ha_bridge_prefs"

    private const val KEY_ENABLED = "breeno_ha_enabled"
    private const val KEY_HA_LAN_URL = "breeno_ha_lan_url"
    private const val KEY_HA_EXT_URL = "breeno_ha_ext_url"
    private const val KEY_HA_TOKEN = "breeno_ha_token"
    private const val KEY_DEFAULT_ROOM = "breeno_ha_default_room"
    private const val KEY_FEEDBACK_MODE = "breeno_ha_feedback_mode"
    private const val KEY_ENABLE_FLUID = "breeno_ha_enable_fluid"
    private const val KEY_FLUID_DISPLAY_MODE = "breeno_ha_fluid_display_mode"
    private const val KEY_FLUID_DURATION_MS = "breeno_ha_fluid_duration_ms"
    private const val KEY_ENABLE_ASSIST_FALLBACK = "breeno_ha_enable_assist_fallback"
    private const val KEY_ASSIST_PREFIXES = "breeno_ha_assist_prefixes"
    private const val KEY_DEMO_MODE = "breeno_ha_demo_mode"
    private const val KEY_CUSTOM_ALIASES = "breeno_ha_custom_aliases"
    private const val KEY_ALLOWED_DOMAINS = "breeno_ha_allowed_domains"
    private const val KEY_EXCLUDED_KEYWORDS = "breeno_ha_excluded_keywords"
    private const val KEY_EXCLUDED_ENTITY_IDS = "breeno_ha_excluded_entity_ids"
    private const val KEY_DEBUG_LOG = "breeno_ha_debug_log"
    private const val KEY_ENTITIES_JSON = "breeno_ha_entities_json"
    private const val KEY_LAST_SYNC_TIME = "breeno_ha_last_sync_time"

    fun getAppPrefs(context: Context): SharedPreferences =
        XiaobuHaBridge.prefsBackend.getAppWritePrefs(context)

    fun isWorldReadableSupported(): Boolean =
        XiaobuHaBridge.prefsBackend.isRemoteChannelReady()

    fun saveConfig(context: Context, config: BridgeConfig) {
        val values = getAppPrefs(context).edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putString(KEY_HA_LAN_URL, config.haLanUrl.trim())
            .putString(KEY_HA_EXT_URL, config.haExternalUrl.trim())
            .putString(KEY_HA_TOKEN, config.haToken.trim())
            .putString(KEY_DEFAULT_ROOM, config.defaultRoom.trim())
            .putInt(KEY_FEEDBACK_MODE, config.feedbackMode.id)
            .putBoolean(KEY_ENABLE_FLUID, config.enableFluidCloud)
            .putInt(KEY_FLUID_DISPLAY_MODE, config.fluidCloudDisplayMode.id)
            .putLong(KEY_FLUID_DURATION_MS, config.fluidCloudDurationMs)
            .putBoolean(KEY_ENABLE_ASSIST_FALLBACK, config.enableLocalAssistFallback)
            .putString(KEY_ASSIST_PREFIXES, config.assistTriggerPrefixes)
            .putBoolean(KEY_DEMO_MODE, config.demoMode)
            .putString(KEY_CUSTOM_ALIASES, config.customAliasesRaw)
            .putString(KEY_ALLOWED_DOMAINS, config.allowedDomainsRaw)
            .putString(KEY_EXCLUDED_KEYWORDS, config.excludedKeywordsRaw)
            .putString(KEY_EXCLUDED_ENTITY_IDS, config.excludedEntityIdsRaw)
            .putBoolean(KEY_DEBUG_LOG, config.debugLog)
        values.apply()

        // The fluid provider can be started before the LSPosed service is bound in the
        // module process. Keep a private mirror for that process-local fallback.
        val mirror = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (mirror !== getAppPrefs(context)) {
            mirror.edit()
                .putBoolean(KEY_ENABLED, config.enabled)
                .putString(KEY_HA_LAN_URL, config.haLanUrl.trim())
                .putString(KEY_HA_EXT_URL, config.haExternalUrl.trim())
                .putString(KEY_HA_TOKEN, config.haToken.trim())
                .putString(KEY_DEFAULT_ROOM, config.defaultRoom.trim())
                .putInt(KEY_FEEDBACK_MODE, config.feedbackMode.id)
                .putBoolean(KEY_ENABLE_FLUID, config.enableFluidCloud)
                .putInt(KEY_FLUID_DISPLAY_MODE, config.fluidCloudDisplayMode.id)
                .putLong(KEY_FLUID_DURATION_MS, config.fluidCloudDurationMs)
                .putBoolean(KEY_ENABLE_ASSIST_FALLBACK, config.enableLocalAssistFallback)
                .putString(KEY_ASSIST_PREFIXES, config.assistTriggerPrefixes)
                .putBoolean(KEY_DEMO_MODE, config.demoMode)
                .putString(KEY_CUSTOM_ALIASES, config.customAliasesRaw)
                .putString(KEY_ALLOWED_DOMAINS, config.allowedDomainsRaw)
                .putString(KEY_EXCLUDED_KEYWORDS, config.excludedKeywordsRaw)
                .putString(KEY_EXCLUDED_ENTITY_IDS, config.excludedEntityIdsRaw)
                .putBoolean(KEY_DEBUG_LOG, config.debugLog)
                .apply()
        }
    }

    private fun getAppReadPrefs(context: Context): SharedPreferences =
        if (XiaobuHaBridge.prefsBackend.isRemoteChannelReady()) {
            XiaobuHaBridge.prefsBackend.getAppReadPrefs(context)
        } else {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

    fun loadConfigFromApp(context: Context): BridgeConfig =
        readConfig(getAppReadPrefs(context))

    fun saveEntities(context: Context, entities: List<HaEntity>) {
        val json = HaEntity.listToJson(entities)
        val now = System.currentTimeMillis()
        getAppPrefs(context).edit()
            .putString(KEY_ENTITIES_JSON, json)
            .putLong(KEY_LAST_SYNC_TIME, now)
            .apply()
        val mirror = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (mirror !== getAppPrefs(context)) {
            mirror.edit()
                .putString(KEY_ENTITIES_JSON, json)
                .putLong(KEY_LAST_SYNC_TIME, now)
                .apply()
        }
    }

    fun loadEntitiesFromApp(context: Context): List<HaEntity> =
        HaEntity.listFromJson(getAppReadPrefs(context).getString(KEY_ENTITIES_JSON, null))

    fun loadLastSyncTimeFromApp(context: Context): Long =
        getAppReadPrefs(context).getLong(KEY_LAST_SYNC_TIME, 0L)

    fun loadConfigInHook(): BridgeConfig {
        val prefs = XiaobuHaBridge.prefsBackend.getHookPrefs() ?: return BridgeConfig()
        return readConfig(prefs)
    }

    fun loadEffectiveEntitiesInHook(config: BridgeConfig): List<HaEntity> {
        val prefs = XiaobuHaBridge.prefsBackend.getHookPrefs()
        val synced = HaEntity.listFromJson(prefs?.getString(KEY_ENTITIES_JSON, null))
        val raw = when {
            synced.isNotEmpty() -> synced
            config.demoMode -> DemoEntities.all
            else -> emptyList()
        }
        return config.filterEntities(raw)
    }

    private fun readBoolean(prefs: SharedPreferences, key: String, default: Boolean): Boolean =
        if (prefs.contains(key)) prefs.getBoolean(key, default) else default

    private fun readInt(prefs: SharedPreferences, key: String, default: Int): Int =
        if (prefs.contains(key)) prefs.getInt(key, default) else default

    private fun readLong(prefs: SharedPreferences, key: String, default: Long): Long =
        if (prefs.contains(key)) prefs.getLong(key, default) else default

    private fun readString(prefs: SharedPreferences, key: String, default: String): String =
        if (prefs.contains(key)) (prefs.getString(key, default) ?: default) else default

    private fun readConfig(prefs: SharedPreferences): BridgeConfig = BridgeConfig(
        enabled = readBoolean(prefs, KEY_ENABLED, true),
        haLanUrl = readString(prefs, KEY_HA_LAN_URL, ""),
        haExternalUrl = readString(prefs, KEY_HA_EXT_URL, ""),
        haToken = readString(prefs, KEY_HA_TOKEN, ""),
        defaultRoom = readString(prefs, KEY_DEFAULT_ROOM, ""),
        feedbackMode = FeedbackMode.fromId(
            readInt(prefs, KEY_FEEDBACK_MODE, FeedbackMode.FLUID_AND_LOCAL_TTS.id)
        ),
        enableFluidCloud = readBoolean(prefs, KEY_ENABLE_FLUID, true),
        fluidCloudDisplayMode = FluidCloudDisplayMode.fromId(
            readInt(prefs, KEY_FLUID_DISPLAY_MODE, FluidCloudDisplayMode.EXPANDED_CARD.id)
        ),
        fluidCloudDurationMs = readLong(prefs, KEY_FLUID_DURATION_MS, 4500L),
        enableLocalAssistFallback = readBoolean(prefs, KEY_ENABLE_ASSIST_FALLBACK, false),
        assistTriggerPrefixes = readString(prefs, KEY_ASSIST_PREFIXES, "家里,全屋,查一下家里"),
        demoMode = readBoolean(prefs, KEY_DEMO_MODE, true),
        customAliasesRaw = readString(prefs, KEY_CUSTOM_ALIASES, ""),
        allowedDomainsRaw = readString(prefs, KEY_ALLOWED_DOMAINS, ""),
        excludedKeywordsRaw = readString(prefs, KEY_EXCLUDED_KEYWORDS, ""),
        excludedEntityIdsRaw = readString(prefs, KEY_EXCLUDED_ENTITY_IDS, ""),
        debugLog = readBoolean(prefs, KEY_DEBUG_LOG, true)
    )
}

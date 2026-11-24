package cc.csxcl.xiaobuhabridge.fluid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import cc.csxcl.xiaobuhabridge.XiaobuHaBridge
import cc.csxcl.xiaobuhabridge.config.ConfigStore
import cc.csxcl.xiaobuhabridge.config.FluidCloudDisplayMode
import cc.csxcl.xiaobuhabridge.ha.HaClient
import cc.csxcl.xiaobuhabridge.model.HaServiceCall
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

class BridgeActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pkg = context.packageName
        when (action) {
            actionShowFluidCloud(pkg), ACTION_SHOW_FLUID_CLOUD -> {
                val payload = FluidCloudPayload(
                    shortCapsuleText = intent.getStringExtra(EXTRA_SHORT_TEXT) ?: "HA 已执行",
                    title = intent.getStringExtra(EXTRA_TITLE) ?: "Home Assistant",
                    subtitle = intent.getStringExtra(EXTRA_SUBTITLE) ?: "操作完成",
                    domain = intent.getStringExtra(EXTRA_DOMAIN) ?: "light",
                    isTurnOnStyle = intent.getBooleanExtra(EXTRA_IS_ON_STYLE, true),
                    isError = intent.getBooleanExtra(EXTRA_IS_ERROR, false),
                    durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 4500L),
                    displayMode = FluidCloudDisplayMode.fromId(
                        intent.getIntExtra(EXTRA_DISPLAY_MODE, FluidCloudDisplayMode.EXPANDED_CARD.id)
                    ),
                    capsuleLeftText = intent.getStringExtra(EXTRA_CAPSULE_LEFT) ?: "",
                    capsuleRightText = intent.getStringExtra(EXTRA_CAPSULE_RIGHT) ?: "",
                    undoDomain = intent.getStringExtra(EXTRA_UNDO_DOMAIN),
                    undoService = intent.getStringExtra(EXTRA_UNDO_SERVICE),
                    undoPayloadJson = intent.getStringExtra(EXTRA_UNDO_PAYLOAD),
                    undoTargetLabel = intent.getStringExtra(EXTRA_UNDO_LABEL),
                    icon = intent.getStringExtra(EXTRA_ICON) ?: "",
                    iconColor = intent.getStringExtra(EXTRA_ICON_COLOR) ?: ""
                )
                FluidCloudNotifier.show(context, payload)
            }

            actionDismissFluidCloud(pkg), ACTION_DISMISS_FLUID_CLOUD -> {
                FluidCloudNotifier.dismiss(context)
            }

            actionUndoCommand(pkg), ACTION_UNDO_COMMAND -> {
                val domain = intent.getStringExtra(EXTRA_UNDO_DOMAIN) ?: return
                val service = intent.getStringExtra(EXTRA_UNDO_SERVICE) ?: return
                val payloadJson = intent.getStringExtra(EXTRA_UNDO_PAYLOAD) ?: return
                val label = intent.getStringExtra(EXTRA_UNDO_LABEL) ?: "设备"
                val icon = intent.getStringExtra(EXTRA_ICON) ?: ""
                val iconColor = intent.getStringExtra(EXTRA_ICON_COLOR) ?: ""
                val pendingResult = goAsync()
                executor.execute {
                    try {
                        val config = ConfigStore.loadConfigFromApp(context)
                        val obj = JSONObject(payloadJson)
                        val ids = mutableListOf<String>()
                        val rawEid = obj.opt("entity_id")
                        when (rawEid) {
                            is String -> ids.add(rawEid)
                            is JSONArray -> {
                                for (i in 0 until rawEid.length()) {
                                    ids.add(rawEid.optString(i))
                                }
                            }
                        }
                        val extras = mutableMapOf<String, Any>()
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            if (k != "entity_id") extras[k] = obj.get(k)
                        }
                        val call = HaServiceCall(domain, service, ids, extras)
                        val res = HaClient.executeCalls(config, listOf(call), "已撤销")
                        FluidCloudNotifier.show(
                            context,
                            FluidCloudPayload(
                                shortCapsuleText = if (res.success) "已撤销" else "撤销失败",
                                title = if (res.success) "已撤销：$label" else "撤销失败",
                                subtitle = if (res.success) "已恢复之前状态" else res.message,
                                domain = domain,
                                isTurnOnStyle = service.contains("on") || service.contains("open"),
                                isError = !res.success,
                                durationMs = 2500L,
                                displayMode = config.fluidCloudDisplayMode,
                                capsuleLeftText = label.take(4),
                                capsuleRightText = if (res.success) "已撤销" else "失败",
                                icon = icon,
                                iconColor = iconColor
                            )
                        )
                    } catch (t: Throwable) {
                        Log.e(TAG, "Undo failed", t)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "BreenoHaReceiver"
        private val executor = Executors.newSingleThreadExecutor()

        const val ACTION_SHOW_FLUID_CLOUD = "cc.csxcl.xiaobuhabridge.ACTION_SHOW_FLUID_CLOUD"
        const val ACTION_UNDO_COMMAND = "cc.csxcl.xiaobuhabridge.ACTION_UNDO_COMMAND"
        const val ACTION_DISMISS_FLUID_CLOUD = "cc.csxcl.xiaobuhabridge.ACTION_DISMISS_FLUID_CLOUD"

        fun actionShowFluidCloud(hostPackageName: String): String =
            "$hostPackageName.breenoha.ACTION_SHOW_FLUID_CLOUD"

        fun actionUndoCommand(hostPackageName: String): String =
            "$hostPackageName.breenoha.ACTION_UNDO_COMMAND"

        fun actionDismissFluidCloud(hostPackageName: String): String =
            "$hostPackageName.breenoha.ACTION_DISMISS_FLUID_CLOUD"

        const val EXTRA_SHORT_TEXT = "short_text"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SUBTITLE = "subtitle"
        const val EXTRA_DOMAIN = "domain"
        const val EXTRA_IS_ON_STYLE = "is_on_style"
        const val EXTRA_IS_ERROR = "is_error"
        const val EXTRA_DURATION_MS = "duration_ms"
        const val EXTRA_DISPLAY_MODE = "display_mode"
        const val EXTRA_CAPSULE_LEFT = "capsule_left"
        const val EXTRA_CAPSULE_RIGHT = "capsule_right"
        const val EXTRA_UNDO_DOMAIN = "undo_domain"
        const val EXTRA_UNDO_SERVICE = "undo_service"
        const val EXTRA_UNDO_PAYLOAD = "undo_payload"
        const val EXTRA_UNDO_LABEL = "undo_label"
        const val EXTRA_ICON = "icon"
        const val EXTRA_ICON_COLOR = "icon_color"

        fun sendFluidCloudBroadcast(
            context: Context,
            payload: FluidCloudPayload,
            hostPackageName: String = XiaobuHaBridge.hostPackageName
        ) {
            if (hostPackageName.isBlank()) {
                Log.w(TAG, "hostPackageName is blank; cannot dispatch fluid cloud")
                return
            }
            // 优先使用同步 ContentProvider.call 跨进程唤醒，规避 ColorOS Hans 墓碑吞后台广播
            if (BridgeFluidProvider.triggerFromRemote(context, payload, hostPackageName)) {
                return
            }
            val intent = Intent(actionShowFluidCloud(hostPackageName)).apply {
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES or Intent.FLAG_RECEIVER_FOREGROUND)
                setClassName(hostPackageName, BridgeActionReceiver::class.java.name)
                putExtra(EXTRA_SHORT_TEXT, payload.shortCapsuleText)
                putExtra(EXTRA_TITLE, payload.title)
                putExtra(EXTRA_SUBTITLE, payload.subtitle)
                putExtra(EXTRA_DOMAIN, payload.domain)
                putExtra(EXTRA_IS_ON_STYLE, payload.isTurnOnStyle)
                putExtra(EXTRA_IS_ERROR, payload.isError)
                putExtra(EXTRA_DURATION_MS, payload.durationMs)
                putExtra(EXTRA_DISPLAY_MODE, payload.displayMode.id)
                putExtra(EXTRA_CAPSULE_LEFT, payload.capsuleLeftText)
                putExtra(EXTRA_CAPSULE_RIGHT, payload.capsuleRightText)
                putExtra(EXTRA_UNDO_DOMAIN, payload.undoDomain)
                putExtra(EXTRA_UNDO_SERVICE, payload.undoService)
                putExtra(EXTRA_UNDO_PAYLOAD, payload.undoPayloadJson)
                putExtra(EXTRA_UNDO_LABEL, payload.undoTargetLabel)
                putExtra(EXTRA_ICON, payload.icon)
                putExtra(EXTRA_ICON_COLOR, payload.iconColor)
            }
            context.sendBroadcast(intent)
        }
    }
}

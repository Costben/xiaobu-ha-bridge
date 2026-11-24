package cc.csxcl.xiaobuhabridge.fluid

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.util.Log
import cc.csxcl.xiaobuhabridge.XiaobuHaBridge
import cc.csxcl.xiaobuhabridge.config.FluidCloudDisplayMode

/**
 * 供小布助手（com.heytap.speechassist）跨进程同步唤醒本模块并弹出流体云的 ContentProvider。
 *
 * 相比普通 Broadcast，在 ColorOS Hans 墓碑机制冻结后台进程时，
 * 同步 ContentProvider.call 可以可靠唤醒进程并立即触发顶部挖孔区流体云胶囊。
 */
class BridgeFluidProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context ?: return Bundle().apply { putBoolean("ok", false) }
        val callingUid = Binder.getCallingUid()
        val callerPkg = callingPackage
        val allowed = callingUid == Process.ROOT_UID ||
            callingUid == Process.SHELL_UID ||
            callingUid == Process.myUid() ||
            callerPkg == XiaobuHaBridge.TARGET_SPEECH_PACKAGE ||
            callerPkg == ctx.packageName

        if (!allowed) {
            Log.w(TAG, "Rejected fluid provider call from uid=$callingUid pkg=$callerPkg")
            return Bundle().apply {
                putBoolean("ok", false)
                putString("error", "unauthorized_caller")
            }
        }

        return when (method) {
            METHOD_SHOW_FLUID_CLOUD -> {
                val b = extras ?: Bundle.EMPTY
                val payload = FluidCloudPayload(
                    shortCapsuleText = b.getString(BridgeActionReceiver.EXTRA_SHORT_TEXT) ?: "HA 已执行",
                    title = b.getString(BridgeActionReceiver.EXTRA_TITLE) ?: "Home Assistant",
                    subtitle = b.getString(BridgeActionReceiver.EXTRA_SUBTITLE) ?: "操作完成",
                    domain = b.getString(BridgeActionReceiver.EXTRA_DOMAIN) ?: "light",
                    isTurnOnStyle = b.getBoolean(BridgeActionReceiver.EXTRA_IS_ON_STYLE, true),
                    isError = b.getBoolean(BridgeActionReceiver.EXTRA_IS_ERROR, false),
                    durationMs = b.getLong(BridgeActionReceiver.EXTRA_DURATION_MS, 4500L),
                    displayMode = FluidCloudDisplayMode.fromId(
                        b.getInt(BridgeActionReceiver.EXTRA_DISPLAY_MODE, FluidCloudDisplayMode.EXPANDED_CARD.id)
                    ),
                    capsuleLeftText = b.getString(BridgeActionReceiver.EXTRA_CAPSULE_LEFT) ?: "",
                    capsuleRightText = b.getString(BridgeActionReceiver.EXTRA_CAPSULE_RIGHT) ?: "",
                    undoDomain = b.getString(BridgeActionReceiver.EXTRA_UNDO_DOMAIN),
                    undoService = b.getString(BridgeActionReceiver.EXTRA_UNDO_SERVICE),
                    undoPayloadJson = b.getString(BridgeActionReceiver.EXTRA_UNDO_PAYLOAD),
                    undoTargetLabel = b.getString(BridgeActionReceiver.EXTRA_UNDO_LABEL),
                    icon = b.getString(BridgeActionReceiver.EXTRA_ICON) ?: "",
                    iconColor = b.getString(BridgeActionReceiver.EXTRA_ICON_COLOR) ?: ""
                )
                FluidCloudNotifier.show(ctx, payload)
                Bundle().apply { putBoolean("ok", true) }
            }

            METHOD_DISMISS_FLUID_CLOUD -> {
                FluidCloudNotifier.dismiss(ctx)
                Bundle().apply { putBoolean("ok", true) }
            }

            else -> Bundle().apply {
                putBoolean("ok", false)
                putString("error", "unknown_method:$method")
            }
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private const val TAG = "BreenoFluidProvider"
        const val METHOD_SHOW_FLUID_CLOUD = "show_fluid_cloud"
        const val METHOD_DISMISS_FLUID_CLOUD = "dismiss_fluid_cloud"

        fun authorityFor(hostPackageName: String): String = "$hostPackageName.breenoha.fluid"

        fun triggerFromRemote(
            context: Context,
            payload: FluidCloudPayload,
            hostPackageName: String = XiaobuHaBridge.hostPackageName
        ): Boolean {
            if (hostPackageName.isBlank()) {
                Log.w(TAG, "hostPackageName is blank; skipping ContentProvider call")
                return false
            }
            val extras = Bundle().apply {
                putString(BridgeActionReceiver.EXTRA_SHORT_TEXT, payload.shortCapsuleText)
                putString(BridgeActionReceiver.EXTRA_TITLE, payload.title)
                putString(BridgeActionReceiver.EXTRA_SUBTITLE, payload.subtitle)
                putString(BridgeActionReceiver.EXTRA_DOMAIN, payload.domain)
                putBoolean(BridgeActionReceiver.EXTRA_IS_ON_STYLE, payload.isTurnOnStyle)
                putBoolean(BridgeActionReceiver.EXTRA_IS_ERROR, payload.isError)
                putLong(BridgeActionReceiver.EXTRA_DURATION_MS, payload.durationMs)
                putInt(BridgeActionReceiver.EXTRA_DISPLAY_MODE, payload.displayMode.id)
                putString(BridgeActionReceiver.EXTRA_CAPSULE_LEFT, payload.capsuleLeftText)
                putString(BridgeActionReceiver.EXTRA_CAPSULE_RIGHT, payload.capsuleRightText)
                putString(BridgeActionReceiver.EXTRA_UNDO_DOMAIN, payload.undoDomain)
                putString(BridgeActionReceiver.EXTRA_UNDO_SERVICE, payload.undoService)
                putString(BridgeActionReceiver.EXTRA_UNDO_PAYLOAD, payload.undoPayloadJson)
                putString(BridgeActionReceiver.EXTRA_UNDO_LABEL, payload.undoTargetLabel)
                putString(BridgeActionReceiver.EXTRA_ICON, payload.icon)
                putString(BridgeActionReceiver.EXTRA_ICON_COLOR, payload.iconColor)
            }
            return try {
                val res = context.contentResolver.call(
                    Uri.parse("content://${authorityFor(hostPackageName)}"),
                    METHOD_SHOW_FLUID_CLOUD,
                    null,
                    extras
                )
                res?.getBoolean("ok", false) == true
            } catch (t: Throwable) {
                Log.w(TAG, "ContentProvider call failed, falling back to broadcast: ${t.message}")
                false
            }
        }
    }
}

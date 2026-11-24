package cc.csxcl.xiaobuhabridge.fluid

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.core.content.ContextCompat
import cc.csxcl.xiaobuhabridge.R
import cc.csxcl.xiaobuhabridge.XiaobuHaBridge
import cc.csxcl.xiaobuhabridge.config.FluidCloudDisplayMode
import cc.csxcl.xiaobuhabridge.model.HaIconSpec
import cc.csxcl.xiaobuhabridge.ui.XiaobuHaBridgeActivity
import org.json.JSONArray
import org.json.JSONObject

data class FluidCloudPayload(
    val shortCapsuleText: String,
    val title: String,
    val subtitle: String,
    val domain: String = "light",
    val isTurnOnStyle: Boolean = true,
    val isError: Boolean = false,
    val durationMs: Long = 4500L,
    val displayMode: FluidCloudDisplayMode = FluidCloudDisplayMode.EXPANDED_CARD,
    val capsuleLeftText: String = "",
    val capsuleRightText: String = "",
    val undoDomain: String? = null,
    val undoService: String? = null,
    val undoPayloadJson: String? = null,
    val undoTargetLabel: String? = null,
    val icon: String = "",
    val iconColor: String = ""
)

/**
 * ColorOS 17 流体云（Android 16/17 Promoted Ongoing + ColorOS CustomUI 模板）发送器：
 * - 胶囊态使用 `t_sym` 对称布局：挖孔左侧「图标 + 短设备名」，挖孔右侧「短状态词」，避免单侧过长失衡
 * - 展开态使用 `oplus.livealert.card`（`t_standard` + `t_actions`）与 `remindType` 强提醒控制
 * - 支持在应用设置里切换：直接展开卡片 / 对称紧凑胶囊 / 持续展开卡片
 */
object FluidCloudNotifier {
    const val CHANNEL_ID = "breeno_ha_fluid_cloud"
    private const val NOTIFICATION_ID = 42001

    private const val EXTRA_OPEN_PI = "ha_open_pi"
    private const val EXTRA_UNDO_PI = "ha_undo_pi"
    private const val EXTRA_DISMISS_PI = "ha_dismiss_pi"
    internal const val EXTRA_BADGE_ICON = "ha_badge_icon"

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var pendingCancelRunnable: Runnable? = null

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "智能家居流体云反馈",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "小布控制 Home Assistant 设备时在顶部流体云显示展开卡片或对称胶囊"
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    fun canPostPromoted(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= 36) {
            val apiAllowed = runCatching { nm.canPostPromotedNotifications() }.getOrDefault(false)
            if (apiAllowed) return true
        }
        if (!nm.areNotificationsEnabled()) return false
        val appOpsAllowed = runCatching {
            val aom = context.getSystemService(AppOpsManager::class.java) ?: return@runCatching false
            val mode = aom.unsafeCheckOpNoThrow(
                "android:post_promoted_notifications",
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
        val permAllowed = ContextCompat.checkSelfPermission(
            context,
            "android.permission.POST_PROMOTED_NOTIFICATIONS"
        ) == PackageManager.PERMISSION_GRANTED
        return appOpsAllowed || permAllowed
    }

    fun show(context: Context, payload: FluidCloudPayload) {
        val appCtx = context.applicationContext
        ensureChannel(appCtx)
        val nm = appCtx.getSystemService(NotificationManager::class.java) ?: return

        val launchIntent = XiaobuHaBridge.launchActivityClassName
            ?.takeIf { it.isNotBlank() }
            ?.let { cls -> Intent().setClassName(appCtx, cls) }
            ?: appCtx.packageManager.getLaunchIntentForPackage(appCtx.packageName)
            ?: Intent(appCtx, XiaobuHaBridgeActivity::class.java)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val openAppIntent = PendingIntent.getActivity(
            appCtx,
            100,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val smallIconRes = if (payload.domain == "light") {
            R.drawable.ic_bulb_capsule
        } else {
            R.drawable.ic_ha_capsule
        }

        val (badgeRes, badgeDrawableName) = when {
            payload.isError -> R.drawable.ic_fluid_home_error to "ic_fluid_home_error"
            payload.domain == "light" && payload.isTurnOnStyle -> R.drawable.ic_fluid_bulb_on to "ic_fluid_bulb_on"
            payload.domain == "light" -> R.drawable.ic_fluid_bulb_off to "ic_fluid_bulb_off"
            else -> R.drawable.ic_fluid_home_on to "ic_fluid_home_on"
        }

        val accentColor = resolveAccentColorArgb(payload)
        val accentHex = HaIconSpec.argbToHex(accentColor)
        val rightTextHex = resolveRightTextHex(payload, accentColor)

        val dynamicBadgeBitmap = if (!payload.isError) {
            runCatching {
                HaIconSpec.renderFluidBadgeBitmap(payload.icon, payload.domain, accentColor)
            }.getOrNull()
        } else {
            null
        }
        val badgeIcon = dynamicBadgeBitmap?.let { Icon.createWithBitmap(it) }
        val iconUri = if (badgeIcon != null) {
            "icon/$EXTRA_BADGE_ICON"
        } else {
            "drawable/$badgeDrawableName"
        }

        val (leftText, rightText) = resolveCapsuleSides(payload)
        val duration = payload.durationMs.coerceIn(2000L, 15000L)

        // 构建「撤销」和「收起」PendingIntent（同时给标准通知 Action 与 ColorOS 流体云自定义按钮共用）
        val undoPi: PendingIntent? = if (
            !payload.undoDomain.isNullOrBlank() &&
            !payload.undoService.isNullOrBlank() &&
            !payload.undoPayloadJson.isNullOrBlank()
        ) {
            val undoIntent = Intent(appCtx, BridgeActionReceiver::class.java).apply {
                action = BridgeActionReceiver.actionUndoCommand(appCtx.packageName)
                putExtra(BridgeActionReceiver.EXTRA_UNDO_DOMAIN, payload.undoDomain)
                putExtra(BridgeActionReceiver.EXTRA_UNDO_SERVICE, payload.undoService)
                putExtra(BridgeActionReceiver.EXTRA_UNDO_PAYLOAD, payload.undoPayloadJson)
                putExtra(BridgeActionReceiver.EXTRA_UNDO_LABEL, payload.undoTargetLabel ?: payload.title)
                putExtra(BridgeActionReceiver.EXTRA_ICON, payload.icon)
                putExtra(BridgeActionReceiver.EXTRA_ICON_COLOR, payload.iconColor)
            }
            PendingIntent.getBroadcast(
                appCtx,
                101,
                undoIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            null
        }

        val dismissIntent = Intent(appCtx, BridgeActionReceiver::class.java).apply {
            action = BridgeActionReceiver.actionDismissFluidCloud(appCtx.packageName)
        }
        val dismissPi = PendingIntent.getBroadcast(
            appCtx,
            102,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val capsuleJson = buildSymmetricCapsuleJson(
            iconUri = iconUri,
            leftText = leftText,
            rightText = rightText,
            rightTextHex = rightTextHex,
            primaryHex = accentHex
        )
        val cardJson = buildExpandedCardJson(
            iconUri = iconUri,
            title = payload.title,
            subtitle = payload.subtitle,
            primaryHex = accentHex,
            hasUndo = undoPi != null
        )
        val optionsJson = buildLiveAlertOptionsJson(
            pkgName = appCtx.packageName,
            remindType = payload.displayMode.remindType
        )

        val extras = Bundle().apply {
            putBoolean("android.requestPromotedOngoing", true)
            putString("oplus.livealert.capsule", capsuleJson)
            putString("oplus.livealert.card", cardJson)
            putString("oplusLiveAlertOptions", optionsJson)
            putString("oplusLiveAlertAppConfig", optionsJson)
            if (badgeIcon != null) {
                putParcelable(EXTRA_BADGE_ICON, badgeIcon)
            }
            putParcelable(EXTRA_OPEN_PI, openAppIntent)
            if (undoPi != null) {
                putParcelable(EXTRA_UNDO_PI, undoPi)
            }
            putParcelable(EXTRA_DISMISS_PI, dismissPi)
        }

        val builder = Notification.Builder(appCtx, CHANNEL_ID)
            .setSmallIcon(smallIconRes)
            .setLargeIcon(badgeIcon ?: Icon.createWithResource(appCtx, badgeRes))
            .setContentTitle(payload.title)
            .setContentText(payload.subtitle)
            .setOngoing(true)
            .setOnlyAlertOnce(false)
            .setColor(accentColor)
            .setExtras(extras)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setTimeoutAfter(duration)

        if (Build.VERSION.SDK_INT >= 36) {
            try {
                // 作为回退文案也保持极致紧凑，避免拉长胶囊
                val fallbackShort = "$leftText$rightText".take(6)
                builder.setShortCriticalText(fallbackShort)
            } catch (_: Throwable) {
            }
        }

        if (undoPi != null) {
            builder.addAction(Notification.Action.Builder(null, "撤销", undoPi).build())
        }
        builder.addAction(Notification.Action.Builder(null, "收起", dismissPi).build())

        nm.notify(NOTIFICATION_ID, builder.build())

        // 刷新自动收回计时器（配合 setTimeoutAfter，即使进程被 Hans 冻结系统也会按时收回）
        pendingCancelRunnable?.let { mainHandler.removeCallbacks(it) }
        val cancelTask = Runnable {
            nm.cancel(NOTIFICATION_ID)
        }
        pendingCancelRunnable = cancelTask
        mainHandler.postDelayed(cancelTask, duration)
    }

    fun dismiss(context: Context) {
        pendingCancelRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingCancelRunnable = null
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    /**
     * 将文案拆分为挖孔左侧（设备短名，<=4字）与挖孔右侧（状态短词，<=4字），保证摄像头两侧视觉对称平衡。
     */
    internal fun resolveCapsuleSides(payload: FluidCloudPayload): Pair<String, String> {
        val explicitLeft = payload.capsuleLeftText.trim()
        val explicitRight = payload.capsuleRightText.trim()
        if (explicitLeft.isNotEmpty() && explicitRight.isNotEmpty()) {
            return explicitLeft.take(4) to explicitRight.take(4)
        }

        val raw = payload.shortCapsuleText.trim()
        val sep = listOf(" · ", "·", " ", "：", ":").firstOrNull { raw.contains(it) }
        if (sep != null) {
            val parts = raw.split(sep, limit = 2).map { it.trim() }
            if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                return compactDeviceName(parts[0]) to parts[1].take(4)
            }
        }

        val defaultRight = when {
            payload.isError -> "失败"
            raw.endsWith("已关") || payload.title.contains("关闭") -> "已关"
            raw.endsWith("已开") || payload.title.contains("打开") || payload.title.contains("开启") -> "已开"
            raw.endsWith("已切换") || payload.title.contains("切换") -> "已切换"
            payload.isTurnOnStyle -> "已执行"
            else -> "已关闭"
        }
        val stripped = raw
            .removeSuffix("已关")
            .removeSuffix("已开")
            .removeSuffix("已关闭")
            .removeSuffix("已打开")
            .removeSuffix("已切换")
            .trim()
        val left = compactDeviceName(stripped.ifEmpty { payload.undoTargetLabel ?: "家居" })
        return left to defaultRight.take(4)
    }

    private fun compactDeviceName(name: String): String {
        val cleaned = name
            .replace("吸顶灯", "灯")
            .replace("智能开关", "开关")
            .replace("电动窗帘", "窗帘")
            .replace("空气循环扇", "风扇")
            .replace("扫地机器人", "扫地机")
            .trim()
        return cleaned.take(4).ifEmpty { "家居" }
    }

    internal fun resolveAccentColorArgb(payload: FluidCloudPayload): Int {
        if (payload.isError) return 0xFFE53935.toInt()
        if (!payload.isTurnOnStyle) return HaIconSpec.INACTIVE_COLOR_ARGB
        return HaIconSpec.resolveEntityColorArgb(
            iconColor = payload.iconColor,
            domain = payload.domain,
            state = "on"
        )
    }

    internal fun resolveRightTextHex(payload: FluidCloudPayload, accentArgb: Int): String =
        if (payload.isError) "#FF7B7B" else HaIconSpec.brightCapsuleTextHex(accentArgb)

    internal fun buildSymmetricCapsuleJson(
        iconUri: String,
        leftText: String,
        rightText: String,
        rightTextHex: String,
        primaryHex: String
    ): String {
        val iconSpec = JSONObject().apply {
            put("icon", iconUri)
            put("shape", "CIRCLE")
        }
        val capsuleData = JSONObject().apply {
            put("type", "t_sym")
            put("firstIcon", JSONObject().put("icon", iconSpec))
            put("firstTitle", JSONObject().put("text", leftText))
            put(
                "lastTitle",
                JSONObject().apply {
                    put("text", rightText)
                    put("textColor", "colorInt/$rightTextHex")
                }
            )
        }
        // 胶囊态不绑定直跳 Intent，点按胶囊即可顺滑展开卡片查看详情与撤销按钮；卡片本体仍保留打开应用 Intent
        val attr = JSONObject().apply {
            put("primaryColor", "colorInt/$primaryHex")
        }
        return JSONObject()
            .put("capsuleData", capsuleData)
            .put("attr", attr)
            .toString()
    }

    internal fun buildExpandedCardJson(
        iconUri: String,
        title: String,
        subtitle: String,
        primaryHex: String,
        hasUndo: Boolean
    ): String {
        val iconSpec = JSONObject().apply {
            put("icon", iconUri)
            put("shape", "CIRCLE")
        }
        val primaryData = JSONObject().apply {
            put("type", "t_standard")
            put("icon", JSONObject().put("icon", iconSpec))
            put("title", JSONObject().put("text", title))
            put(
                "labels",
                JSONArray().put(
                    JSONArray().put(JSONObject().put("text", subtitle))
                )
            )
        }

        val btns = JSONArray().apply {
            if (hasUndo) {
                put(
                    JSONObject().apply {
                        put("text", "撤销")
                        put("style", "BUTTON")
                        put("type", "ICON_AND_TEXT")
                        put("shape", "RECTANGLE")
                        put("size", "LARGE")
                        put("textSize", "15")
                        put("bgColor", "colorInt/#33FFFFFF")
                        put("intent", "pi/$EXTRA_UNDO_PI")
                    }
                )
            }
            put(
                JSONObject().apply {
                    put("text", "收起")
                    put("style", "BUTTON")
                    put("type", "ICON_AND_TEXT")
                    put("shape", "RECTANGLE")
                    put("size", "LARGE")
                    put("textSize", "15")
                    put("bgColor", "colorInt/#21FFFFFF")
                    put("intent", "pi/$EXTRA_DISMISS_PI")
                }
            )
        }

        val extensionData = JSONObject().apply {
            put("type", "t_actions")
            put("arr", "eqd")
            put("btns", btns)
        }

        val attr = JSONObject().apply {
            put("primaryColor", "colorInt/$primaryHex")
            put("intent", "pi/$EXTRA_OPEN_PI")
        }

        return JSONObject()
            .put("primaryData", primaryData)
            .put("extensionData", extensionData)
            .put("attr", attr)
            .toString()
    }

    internal fun buildLiveAlertOptionsJson(pkgName: String, remindType: Int): String {
        return JSONObject().apply {
            put("dataSourcePkgName", "$pkgName.ha")
            put("isMilestone", true)
            put("remindType", remindType)
            put("showHostMap", JSONArray(listOf(8, 16, 4)))
            put("lockScreenShowHostMap", JSONArray(listOf(16)))
            put(
                "extensibleActionMap",
                JSONObject().apply {
                    // ColorOS 17 会强制用宿主包名覆盖第三方通知的 dataSourcePkgName，导致前台应用内
                    // remindType=0 的对称胶囊被 AppFrontInterceptor 拦截；标记 service_from=3 可绕开前台抑制
                    put("service_from", 3)
                    put("isDismissible", true)
                    put("isAutoCancel", false)
                    put("liveAlertService", "")
                    put("immersiveCardType", -1)
                    put("requestLockScreenImmersive", -1)
                    put("ignoreEntry", 0)
                }
            )
        }.toString()
    }
}

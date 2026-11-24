package cc.csxcl.xiaobuhabridge.hook

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import cc.csxcl.xiaobuhabridge.XiaobuHaBridge
import cc.csxcl.xiaobuhabridge.config.BridgeConfig
import cc.csxcl.xiaobuhabridge.config.FeedbackMode
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * 负责将 Home Assistant 执行结果原生地回灌给 ColorOS 17 小布：
 * - 通知云端 CancelRecord 停止大模型继续下发
 * - 结束超级小布 GENERATING 转圈状态
 * - 上屏原生回复气泡（兼容 ColorOS 17 超级小布 AIChat 与经典浮窗）
 * - 调用小布原厂 TTSEngineSpeakHelper 播报（支持本地零延迟离线音色 / 云端默认音色）
 * - 播完或静默模式下自动调用 ConversationExtraTool (d.c(ctx, 6)) 平滑收起小布浮窗
 */
object BreenoReplyBridge {
    private const val TAG = "BreenoHaBridge"
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun log(msg: String) {
        XiaobuHaBridge.hookRuntime?.log("$TAG: $msg")
    }

    fun getSpeechContext(cl: ClassLoader): Context? {
        runCatching {
            val engineCls = ReflectUtils.findClass("com.heytap.speech.engine.HeytapSpeechEngine", cl)
            val holder = runCatching { ReflectUtils.getStaticObjectField(engineCls, "Companion") }.getOrNull()
                ?: runCatching { ReflectUtils.getStaticObjectField(engineCls, "INSTANCE") }.getOrNull()
            if (holder != null) {
                val engine = ReflectUtils.callMethod(holder, "getInstance")
                val ctx = engine?.let { ReflectUtils.callMethod(it, "getContext") as? Context }
                if (ctx != null) return ctx
            }
        }
        runCatching {
            val atCls = Class.forName("android.app.ActivityThread")
            val app = atCls.getMethod("currentApplication").invoke(null) as? Context
            if (app != null) return app
        }
        for (clsName in listOf("com.heytap.speechassist.utils.z2", "com.heytap.speechassist.utils.y2")) {
            val ctx = runCatching {
                val cls = ReflectUtils.findClass(clsName, cl)
                ReflectUtils.callStaticMethod(cls, "b") as? Context
            }.getOrNull()
            if (ctx != null) return ctx
        }
        return null
    }

    fun cancelCloudGeneration(cl: ClassLoader, recordId: String?) {
        if (recordId.isNullOrBlank()) return
        try {
            val helperCls = ReflectUtils.findClass(
                "com.heytap.speechassist.aichat.floatwindow.AIChatEngineHelper",
                cl
            )
            val instance = ReflectUtils.getStaticObjectField(helperCls, "INSTANCE")
            val cancelMethod = helperCls.declaredMethods.firstOrNull { m ->
                m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == Boolean::class.javaPrimitiveType &&
                    m.parameterTypes[1] == String::class.java &&
                    (m.name == "q" || m.name == "p")
            } ?: helperCls.declaredMethods.firstOrNull { m ->
                m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == Boolean::class.javaPrimitiveType &&
                    m.parameterTypes[1] == String::class.java
            }
            if (cancelMethod != null) {
                cancelMethod.isAccessible = true
                cancelMethod.invoke(instance, false, recordId)
            } else {
                log("cancelCloudGeneration method not found on AIChatEngineHelper")
            }
        } catch (t: Throwable) {
            log("cancelCloudGeneration skipped: ${t.message}")
        }
    }

    fun deliverFeedback(
        cl: ClassLoader,
        dismissMethod: Method?,
        config: BridgeConfig,
        replyText: String,
        recordId: String,
        roomId: String,
        uniqueId: String,
        alsoInsertUserQuery: String? = null
    ) {
        mainHandler.post {
            val ctx = getSpeechContext(cl)
            try {
                // 如是合包截杀或文本直截，把用户问题气泡也补上
                if (!alsoInsertUserQuery.isNullOrBlank() && config.feedbackMode != FeedbackMode.FLUID_SILENT_DISMISS) {
                    insertUserBubbleInNewAiMode(cl, alsoInsertUserQuery, roomId, recordId, uniqueId)
                }

                // 先结束“思考中/生成中”转圈状态
                resetAiChatStateToIdle(cl, roomId)

                when (config.feedbackMode) {
                    FeedbackMode.FLUID_SILENT_DISMISS -> {
                        dismissWindow(ctx, dismissMethod)
                    }

                    FeedbackMode.FLUID_AND_BUBBLE_ONLY -> {
                        insertReplyBubble(cl, ctx, replyText, recordId, roomId, uniqueId)
                    }

                    FeedbackMode.FLUID_AND_LOCAL_TTS -> {
                        insertReplyBubble(cl, ctx, replyText, recordId, roomId, uniqueId)
                        speakWithBreenoTts(cl, ctx, replyText, forceLocal = true) {
                            mainHandler.postDelayed({ dismissWindow(ctx, dismissMethod) }, 350L)
                        }
                    }

                    FeedbackMode.FLUID_AND_CLOUD_TTS -> {
                        insertReplyBubble(cl, ctx, replyText, recordId, roomId, uniqueId)
                        speakWithBreenoTts(cl, ctx, replyText, forceLocal = false) {
                            mainHandler.postDelayed({ dismissWindow(ctx, dismissMethod) }, 350L)
                        }
                    }
                }
            } catch (t: Throwable) {
                log("deliverFeedback error: ${t.message}")
            }
        }
    }

    private fun isNewAiMode(cl: ClassLoader): Boolean {
        return try {
            val x2Cls = ReflectUtils.findClass("com.heytap.speechassist.core.x2", cl)
            val wc = ReflectUtils.callStaticMethod(x2Cls, "a") ?: return true
            ReflectUtils.callMethod(wc, "G0") as? Boolean ?: true
        } catch (_: Throwable) {
            true
        }
    }

    private fun resolveRoomId(cl: ClassLoader, roomId: String): String {
        if (roomId.isNotBlank()) return roomId
        return try {
            val rmCls = ReflectUtils.findClass("com.heytap.speechassist.aichat.AIChatRoomIdManager", cl)
            val inst = ReflectUtils.getStaticObjectField(rmCls, "INSTANCE") ?: return ""
            val hRoom = runCatching { ReflectUtils.callMethod(inst, "H") as? String }.getOrNull().orEmpty()
            if (hRoom.isNotBlank()) hRoom
            else (runCatching { ReflectUtils.callMethod(inst, "F") as? String }.getOrNull()).orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun insertUserBubbleInNewAiMode(
        cl: ClassLoader,
        queryText: String,
        roomId: String,
        recordId: String,
        uniqueId: String
    ) {
        if (!isNewAiMode(cl)) return
        try {
            val providerCls = ReflectUtils.findClass("com.heytap.speechassist.aichat.AIChatViewBeanProvider", cl)
            val provider = ReflectUtils.getStaticObjectField(providerCls, "INSTANCE") ?: return
            val effectiveRoom = resolveRoomId(cl, roomId)
            val uid = uniqueId.ifBlank { System.currentTimeMillis().toString() }
            val rid = recordId.ifBlank { uid }
            ReflectUtils.callMethod(provider, "a", queryText, effectiveRoom, uid, rid, 0)
        } catch (t: Throwable) {
            log("insertUserBubble skipped: ${t.message}")
        }
    }

    private fun insertReplyBubble(
        cl: ClassLoader,
        ctx: Context?,
        replyText: String,
        recordId: String,
        roomId: String,
        uniqueId: String
    ) {
        if (isNewAiMode(cl)) {
            try {
                val providerCls = ReflectUtils.findClass("com.heytap.speechassist.aichat.AIChatViewBeanProvider", cl)
                val provider = ReflectUtils.getStaticObjectField(providerCls, "INSTANCE") ?: return
                val dataCenterCls = ReflectUtils.findClass("com.heytap.speechassist.aichat.AIChatDataCenter", cl)
                val dataCenter = ReflectUtils.getStaticObjectField(dataCenterCls, "INSTANCE") ?: return
                val effectiveRoom = resolveRoomId(cl, roomId)
                val rid = recordId.ifBlank { System.currentTimeMillis().toString() }
                val uid = uniqueId.ifBlank { rid }
                val bean = ReflectUtils.callMethod(provider, "g", uid, rid, rid, effectiveRoom, replyText)
                ReflectUtils.callMethod(dataCenter, "s", bean)
                return
            } catch (t: Throwable) {
                log("NewAIMode bubble failed, trying legacy: ${t.message}")
            }
        }
        // 经典浮窗兜底
        try {
            val x2Cls = ReflectUtils.findClass("com.heytap.speechassist.core.x2", cl)
            val wc = ReflectUtils.callStaticMethod(x2Cls, "a") ?: return
            val viewHandler = ReflectUtils.callMethod(wc, "c")
            if (viewHandler != null) {
                ReflectUtils.callMethod(viewHandler, "addReplyText", replyText)
            }
        } catch (t: Throwable) {
            log("legacy bubble failed: ${t.message}")
        }
    }

    private fun resetAiChatStateToIdle(cl: ClassLoader, roomId: String) {
        try {
            val mgrCls = ReflectUtils.findClass("com.heytap.speechassist.aichat.state.AiChatStateManager", cl)
            val stateCls = ReflectUtils.findClass("com.heytap.speechassist.aichat.state.AiChatState", cl)
            val idleEnum = ReflectUtils.getStaticObjectField(stateCls, "IDLE")
            val companion = runCatching { ReflectUtils.getStaticObjectField(mgrCls, "g") }.getOrNull()
                ?: runCatching { ReflectUtils.getStaticObjectField(mgrCls, "f24828g") }.getOrNull()
                ?: mgrCls.declaredFields.firstOrNull {
                    java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                        it.type.name.startsWith("com.heytap.speechassist.aichat.state.AiChatStateManager\$")
                }?.apply { isAccessible = true }?.get(null)
                ?: return
            val mgr = ReflectUtils.callMethod(companion, "a")
            val effectiveRoom = resolveRoomId(cl, roomId)
            ReflectUtils.callStaticMethod(mgrCls, "i", mgr, idleEnum, effectiveRoom, false, 4, null)
        } catch (t: Throwable) {
            log("resetAiChatStateToIdle skipped: ${t.message}")
        }
    }

    private fun speakWithBreenoTts(
        cl: ClassLoader,
        ctx: Context?,
        speakText: String,
        forceLocal: Boolean,
        onFinished: () -> Unit
    ) {
        if (ctx == null || speakText.isBlank()) {
            onFinished()
            return
        }
        try {
            val helperCls = ReflectUtils.findClass(
                "com.heytap.speechassist.pluginAdapter.platformAdapterDefine.tts.TTSEngineSpeakHelper",
                cl
            )
            val listenerIface = ReflectUtils.findClass(
                "com.heytap.speechassist.pluginAdapter.platformAdapterDefine.tts.TtsListener",
                cl
            )
            var called = false
            val proxy = Proxy.newProxyInstance(cl, arrayOf(listenerIface)) { _, method, _ ->
                if (!called && (method.name == "onSpeakCompleted" || method.name == "onTtsError" || method.name == "onSpeakInterrupted")) {
                    called = true
                    onFinished()
                }
                null
            }
            val bundle = Bundle().apply {
                if (forceLocal) {
                    putString("tts_model", "local")
                }
            }
            ReflectUtils.callStaticMethod(helperCls, "speak", ctx, speakText, proxy, bundle)
            // 安全兜底：防止 TTS 回调丢失导致浮窗永不收起
            mainHandler.postDelayed({
                if (!called) {
                    called = true
                    onFinished()
                }
            }, 6000L)
        } catch (t: Throwable) {
            log("speakWithBreenoTts failed: ${t.message}")
            onFinished()
        }
    }

    private fun dismissWindow(ctx: Context?, dismissMethod: Method?) {
        if (ctx == null || dismissMethod == null) return
        try {
            dismissMethod.invoke(null, ctx, 6)
        } catch (t: Throwable) {
            log("dismissWindow failed: ${t.message}")
        }
    }
}

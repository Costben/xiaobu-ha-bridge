package cc.csxcl.xiaobuhabridge.hook

import android.os.Bundle
import cc.csxcl.xiaobuhabridge.XiaobuHaBridge
import cc.csxcl.xiaobuhabridge.config.BridgeConfig
import cc.csxcl.xiaobuhabridge.config.ConfigStore
import cc.csxcl.xiaobuhabridge.fluid.BridgeActionReceiver
import cc.csxcl.xiaobuhabridge.fluid.FluidCloudPayload
import cc.csxcl.xiaobuhabridge.ha.HaClient
import cc.csxcl.xiaobuhabridge.model.NluResult
import cc.csxcl.xiaobuhabridge.nlu.LocalNluEngine
import org.json.JSONObject
import java.lang.reflect.Method
import java.util.concurrent.Executors

object BridgeHookEntry {
    private const val TARGET_PACKAGE = XiaobuHaBridge.TARGET_SPEECH_PACKAGE
    private const val TAG = "BreenoHaBridge"
    private val workerExecutor = Executors.newCachedThreadPool()

    private fun log(msg: String) {
        XiaobuHaBridge.hookRuntime?.log("$TAG: $msg")
    }

    fun install(classLoader: ClassLoader, apkPath: String) {
        val runtime = XiaobuHaBridge.hookRuntime
        if (runtime == null) {
            android.util.Log.e(TAG, "BridgeHookRuntime not configured before install()")
            return
        }
        log("loaded into $TARGET_PACKAGE")

        val targets = BreenoDexFinder.resolve(classLoader, apkPath)
        hookDirectiveFilter(classLoader, targets)
        hookSendText(classLoader, targets)
    }

    private fun hookDirectiveFilter(cl: ClassLoader, targets: ResolvedBreenoTargets) {
        val method = targets.directiveFilterMethod
        if (method == null) {
            log("ERROR directiveFilterMethod not found")
            return
        }
        val runtime = XiaobuHaBridge.hookRuntime ?: return
        runtime.hookBefore(method) { args, setResult ->
            val config = ConfigStore.loadConfigInHook()
            if (!config.enabled) return@hookBefore

            val argRecordId = args.getOrNull(1) as? String ?: ""
            val rawJson = args.getOrNull(2) as? String ?: return@hookBefore
            if (rawJson.isBlank()) return@hookBefore

            try {
                val root = JSONObject(rawJson)
                val recordId = root.optString("recordId", argRecordId).ifBlank { argRecordId }
                val origRecordId = root.optString("originalRecordId", "")
                val roomId = root.optString("roomId", "")
                val uniqueId = root.optString("uniqueId", "")
                val directives = root.optJSONArray("directives") ?: return@hookBefore

                // 检查本包里是否包含 ASR 识别指令与下游业务回复指令
                var hasAsrDirective = false
                var hasBusinessDirective = false
                var finalAsrText: String? = null

                for (i in 0 until directives.length()) {
                    val d = directives.optJSONObject(i) ?: continue
                    val header = d.optJSONObject("header") ?: continue
                    val ns = header.optString("namespace", "")
                    val name = header.optString("name", "")
                    val full = "$ns.$name"
                    if (ns == "SpeechRecognizer") {
                        hasAsrDirective = true
                        val payload = d.optJSONObject("payload")
                        if (payload != null) {
                            val type = payload.optString("type", "")
                            if (full == "SpeechRecognizer.StreamRecognizeResult") {
                                val merged = StreamAsrTracker.updateStreamChunk(
                                    origRecordId.ifBlank { recordId },
                                    payload
                                )
                                if (type == "FINAL") {
                                    finalAsrText = merged
                                }
                            } else if (full == "SpeechRecognizer.RecognizeResult" && type == "FINAL") {
                                finalAsrText = payload.optString("content", "")
                            }
                        }
                    } else if (ns != "Command" && ns != "Tracking" && ns != "CustomerData") {
                        hasBusinessDirective = true
                    }
                }

                // 情况 1：下游云端回复包，属于之前已经由 HA 接管的 recordId -> 一刀截杀！
                if (hasBusinessDirective && StreamAsrTracker.isIntercepted(recordId, origRecordId)) {
                    if (config.debugLog) {
                        log("[INTERCEPTED CLOUD REPLY] recordId=$recordId orig=$origRecordId")
                    }
                    setResult(true)
                    return@hookBefore
                }

                // 情况 2：收到 FINAL ASR 终态文本 -> 运行手机端纯本地 NLU (<2ms)
                val query = finalAsrText?.trim().orEmpty()
                if (query.isNotEmpty()) {
                    val match = evaluateQuery(config, query)
                    if (match != null) {
                        StreamAsrTracker.markIntercepted(recordId, origRecordId)
                        if (StreamAsrTracker.markDispatchedOnce(recordId, origRecordId)) {
                            if (config.debugLog) {
                                log("[ASR MATCH] '$query' -> ${match.summary}")
                            }
                            // 若云端把 ASR FINAL 与业务回复合在同一个包里下发，则连这包一起截杀并自己补用户气泡；
                            // 若只是纯 ASR 包，则放行让小布原生显示用户气泡 & 结束录音动画
                            val blockCurrentPacket = hasBusinessDirective
                            if (blockCurrentPacket) {
                                setResult(true)
                            }
                            dispatchMatchedCommand(
                                cl = cl,
                                dismissMethod = targets.dismissWindowMethod,
                                config = config,
                                match = match,
                                recordId = recordId,
                                roomId = roomId,
                                uniqueId = uniqueId,
                                insertUserQuery = if (blockCurrentPacket) query else null,
                                sendCancelRecord = !blockCurrentPacket
                            )
                        } else if (hasBusinessDirective) {
                            setResult(true)
                        }
                    } else if (config.debugLog && hasAsrDirective) {
                        log("[PASSTHROUGH] '$query'")
                    }
                }
            } catch (t: Throwable) {
                log("directiveFilter hook error: ${t.message}")
            }
        }
        log("hooked directiveFilter -> ${method.declaringClass.name}.${method.name}")
    }

    private fun hookSendText(cl: ClassLoader, targets: ResolvedBreenoTargets) {
        val method = targets.sendTextMethod
        if (method == null) {
            log("sendTextMethod not found (voice hook still active)")
            return
        }
        val runtime = XiaobuHaBridge.hookRuntime ?: return
        runtime.hookBefore(method) { args, setResult ->
            val config = ConfigStore.loadConfigInHook()
            if (!config.enabled) return@hookBefore

            val text = (args.getOrNull(0) as? String)?.trim().orEmpty()
            if (text.isEmpty()) return@hookBefore
            val bundle = args.getOrNull(1) as? Bundle

            val match = evaluateQuery(config, text) ?: return@hookBefore
            if (config.debugLog) {
                log("[TEXT MATCH] '$text' -> ${match.summary}")
            }
            val recordId = bundle?.getString("customized_record_id")
                ?: "ha_${System.currentTimeMillis()}"
            val roomId = bundle?.getString("roomId").orEmpty()
            StreamAsrTracker.markIntercepted(recordId)
            // 直接阻断发往欢太云端
            setResult(null)

            dispatchMatchedCommand(
                cl = cl,
                dismissMethod = targets.dismissWindowMethod,
                config = config,
                match = match,
                recordId = recordId,
                roomId = roomId,
                uniqueId = recordId,
                insertUserQuery = null, // SpeechEngineHandler.y 已将用户文本上屏
                sendCancelRecord = false
            )
        }
        log("hooked sendText -> ${method.declaringClass.name}.${method.name}")
    }

    private sealed class MatchDecision(val summary: String) {
        class LocalDirect(val nlu: NluResult) : MatchDecision("LOCAL:${nlu.action}:${nlu.matchedEntities.map { it.entityId }}")
        class HaAssistFallback(val query: String) : MatchDecision("ASSIST:$query")
    }

    private fun evaluateQuery(config: BridgeConfig, text: String): MatchDecision? {
        val entities = ConfigStore.loadEffectiveEntitiesInHook(config)
        val engine = LocalNluEngine(
            entities = entities,
            defaultRoom = config.defaultRoom,
            customAliases = config.parsedCustomAliases()
        )
        engine.parse(text)?.let { return MatchDecision.LocalDirect(it) }

        if (config.enableLocalAssistFallback && config.hasRealHaConfigured()) {
            val prefixes = config.parsedAssistPrefixes()
            if (prefixes.any { text.startsWith(it) }) {
                return MatchDecision.HaAssistFallback(text)
            }
        }
        return null
    }

    private fun dispatchMatchedCommand(
        cl: ClassLoader,
        dismissMethod: Method?,
        config: BridgeConfig,
        match: MatchDecision,
        recordId: String,
        roomId: String,
        uniqueId: String,
        insertUserQuery: String?,
        sendCancelRecord: Boolean
    ) {
        workerExecutor.execute {
            if (sendCancelRecord) {
                BreenoReplyBridge.cancelCloudGeneration(cl, recordId)
            }

            val speechCtx = BreenoReplyBridge.getSpeechContext(cl)

            when (match) {
                is MatchDecision.LocalDirect -> {
                    val nlu = match.nlu
                    val res = HaClient.executeCalls(config, nlu.serviceCalls, nlu.replyText)
                    val reply = res.message
                    if (config.debugLog) {
                        log("HA exec success=${res.success} (${res.latencyMs}ms): $reply")
                    }
                    if (config.enableFluidCloud) {
                        if (speechCtx == null) {
                            log("skip fluid cloud because speechCtx is null")
                        } else {
                            val undo = nlu.undoCall
                            val firstEntity = nlu.matchedEntities.firstOrNull()
                            val domain = firstEntity?.domain ?: "light"
                            val subtitle = if (res.success) {
                                "${nlu.cardSubtitle} · ${res.latencyMs}ms" + if (res.isSimulated) " (模拟)" else ""
                            } else {
                                res.message
                            }
                            BridgeActionReceiver.sendFluidCloudBroadcast(
                                speechCtx,
                                FluidCloudPayload(
                                    shortCapsuleText = if (res.success) nlu.capsuleShortText else "HA 控制失败",
                                    title = if (res.success) nlu.cardTitle else "控制失败",
                                    subtitle = subtitle,
                                    domain = domain,
                                    isTurnOnStyle = nlu.isTurnOnStyle,
                                    isError = !res.success,
                                    durationMs = config.fluidCloudDurationMs,
                                    displayMode = config.fluidCloudDisplayMode,
                                    undoDomain = if (res.success) undo?.domain else null,
                                    undoService = if (res.success) undo?.service else null,
                                    undoPayloadJson = if (res.success) undo?.toPayloadJson()?.toString() else null,
                                    undoTargetLabel = firstEntity?.displayName,
                                    icon = firstEntity?.icon.orEmpty(),
                                    iconColor = firstEntity?.iconColor.orEmpty()
                                )
                            )
                        }
                    }
                    BreenoReplyBridge.deliverFeedback(
                        cl = cl,
                        dismissMethod = dismissMethod,
                        config = config,
                        replyText = reply,
                        recordId = recordId,
                        roomId = roomId,
                        uniqueId = uniqueId,
                        alsoInsertUserQuery = insertUserQuery
                    )
                }

                is MatchDecision.HaAssistFallback -> {
                    val res = HaClient.callLocalAssist(config, match.query)
                    if (config.enableFluidCloud && speechCtx != null) {
                        BridgeActionReceiver.sendFluidCloudBroadcast(
                            speechCtx,
                            FluidCloudPayload(
                                shortCapsuleText = if (res.success) "HA 已完成" else "HA 失败",
                                title = if (res.success) res.message.take(24) else "Home Assistant 未执行",
                                subtitle = "${match.query} · ${res.latencyMs}ms",
                                domain = "home",
                                isTurnOnStyle = res.success,
                                isError = !res.success,
                                durationMs = config.fluidCloudDurationMs,
                                displayMode = config.fluidCloudDisplayMode,
                                capsuleLeftText = "智能家居",
                                capsuleRightText = if (res.success) "已完成" else "失败"
                            )
                        )
                    }
                    BreenoReplyBridge.deliverFeedback(
                        cl = cl,
                        dismissMethod = dismissMethod,
                        config = config,
                        replyText = res.message,
                        recordId = recordId,
                        roomId = roomId,
                        uniqueId = uniqueId,
                        alsoInsertUserQuery = insertUserQuery
                    )
                }
            }
        }
    }
}

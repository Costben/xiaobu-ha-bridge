package cc.csxcl.xiaobuhabridge.ha

import cc.csxcl.xiaobuhabridge.config.BridgeConfig
import cc.csxcl.xiaobuhabridge.model.HaEntity
import cc.csxcl.xiaobuhabridge.model.HaIconSpec
import cc.csxcl.xiaobuhabridge.model.HaServiceCall
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class HaExecResult(
    val success: Boolean,
    val message: String,
    val latencyMs: Long,
    val isSimulated: Boolean = false
)

object HaClient {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val execStrictClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(4500, TimeUnit.MILLISECONDS)
            .writeTimeout(3000, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val syncStrictClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5000, TimeUnit.MILLISECONDS)
            .readTimeout(12000, TimeUnit.MILLISECONDS)
            .writeTimeout(5000, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 仅对用户配置的私网局域网 IP / .local / .lan 地址放宽 HTTPS 证书与主机名校验
     * （例如 HA 内网启用了公网域名证书，但通过 https://192.168.x.x:8123 访问的场景）。
     * 公网域名始终使用严格系统证书校验。
     */
    private val execLanTlsClient: OkHttpClient by lazy { buildLanRelaxedClient(2500, 4500, 3000) }
    private val syncLanTlsClient: OkHttpClient by lazy { buildLanRelaxedClient(5000, 12000, 5000) }

    private fun buildLanRelaxedClient(connectMs: Long, readMs: Long, writeMs: Long): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(connectMs, TimeUnit.MILLISECONDS)
            .readTimeout(readMs, TimeUnit.MILLISECONDS)
            .writeTimeout(writeMs, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
        try {
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
            builder.hostnameVerifier { hostname, _ -> isPrivateLanHost(hostname) }
        } catch (_: Throwable) {
        }
        return builder.build()
    }

    private fun clientFor(baseUrl: String, forSync: Boolean): OkHttpClient {
        val parsed = baseUrl.toHttpUrlOrNull()
        val useLanRelaxed = parsed != null && parsed.isHttps && isPrivateLanHost(parsed.host)
        return when {
            forSync && useLanRelaxed -> syncLanTlsClient
            forSync -> syncStrictClient
            useLanRelaxed -> execLanTlsClient
            else -> execStrictClient
        }
    }

    internal fun isPrivateLanHost(host: String): Boolean {
        val h = host.trim().lowercase()
        if (h == "localhost" || h == "127.0.0.1" || h.endsWith(".local") || h.endsWith(".lan")) {
            return true
        }
        val parts = h.split('.')
        if (parts.size != 4) return false
        val a = parts[0].toIntOrNull() ?: return false
        val b = parts[1].toIntOrNull() ?: return false
        val c = parts[2].toIntOrNull() ?: return false
        val d = parts[3].toIntOrNull() ?: return false
        if (a !in 0..255 || b !in 0..255 || c !in 0..255 || d !in 0..255) return false
        return a == 10 ||
            (a == 192 && b == 168) ||
            (a == 172 && b in 16..31) ||
            (a == 100 && b in 64..127)
    }

    private val controllableDomains = setOf(
        "light", "switch", "climate", "cover", "fan",
        "media_player", "vacuum", "humidifier", "scene",
        "script", "input_boolean", "remote", "water_heater", "lock"
    )

    fun syncEntities(config: BridgeConfig): Result<List<HaEntity>> {
        val urls = config.normalizedUrls()
        if (urls.isEmpty() || config.haToken.isBlank()) {
            return Result.failure(IllegalStateException("请先填写 Home Assistant 地址和长期访问令牌"))
        }

        val errors = mutableListOf<String>()
        for (baseUrl in urls) {
            try {
                val statesBody = get(baseUrl, "/api/states", config.haToken, forSync = true)
                val areaMap = fetchAreaMapSilently(baseUrl, config.haToken)
                val visualMeta = fetchVisualMetaSilently(baseUrl, config.haToken)
                val arr = JSONArray(statesBody)
                val list = mutableListOf<HaEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val entityId = obj.optString("entity_id").trim()
                    val domain = entityId.substringBefore('.', "")
                    if (domain !in controllableDomains) continue
                    val attrs = obj.optJSONObject("attributes")
                    val friendlyName = attrs?.optString("friendly_name")?.trim().orEmpty()
                        .ifBlank { entityId.substringAfter('.') }
                    val area = areaMap[entityId].orEmpty()
                    val stateIcon = optNonNullString(attrs, "icon")
                    val stateColor = extractColorFromAttributes(attrs)
                    val wsMeta = visualMeta[entityId]
                    val resolvedIcon = wsMeta?.lovelaceIcon.orEmpty()
                        .ifBlank { stateIcon }
                        .ifBlank { wsMeta?.registryIcon.orEmpty() }
                    val resolvedColor = wsMeta?.lovelaceColor.orEmpty()
                        .ifBlank { stateColor }
                    list.add(
                        HaEntity(
                            entityId = entityId,
                            domain = domain,
                            name = friendlyName,
                            areaName = area,
                            aliases = emptyList(),
                            state = obj.optString("state", ""),
                            icon = resolvedIcon,
                            iconColor = resolvedColor
                        )
                    )
                }
                return Result.success(list.sortedWith(compareBy({ it.areaName }, { it.domain }, { it.name })))
            } catch (t: Throwable) {
                errors.add("$baseUrl -> ${t.message ?: t.javaClass.simpleName}")
            }
        }
        return Result.failure(IllegalStateException(errors.joinToString(" | ").ifBlank { "连接 Home Assistant 失败" }))
    }

    fun executeCalls(config: BridgeConfig, calls: List<HaServiceCall>, defaultSuccessReply: String): HaExecResult {
        val start = System.currentTimeMillis()
        if (!config.hasRealHaConfigured()) {
            if (config.demoMode) {
                return HaExecResult(
                    success = true,
                    message = "$defaultSuccessReply（模拟模式）",
                    latencyMs = System.currentTimeMillis() - start,
                    isSimulated = true
                )
            }
            return HaExecResult(
                success = false,
                message = "未配置 Home Assistant 地址或令牌",
                latencyMs = System.currentTimeMillis() - start
            )
        }

        val urls = config.normalizedUrls()
        var lastError: Throwable? = null
        for (baseUrl in urls) {
            try {
                for (call in calls) {
                    val path = "/api/services/${call.domain}/${call.service}"
                    postJson(baseUrl, path, config.haToken, call.toPayloadJson().toString(), forSync = false)
                }
                return HaExecResult(
                    success = true,
                    message = defaultSuccessReply,
                    latencyMs = System.currentTimeMillis() - start
                )
            } catch (t: Throwable) {
                lastError = t
            }
        }
        return HaExecResult(
            success = false,
            message = "连接 Home Assistant 失败：${lastError?.message ?: "网络超时"}",
            latencyMs = System.currentTimeMillis() - start
        )
    }

    /**
     * 可选纯本地兜底：调用 Home Assistant 自带 /api/conversation/process（内置 hassil 本地规则引擎）
     */
    fun callLocalAssist(config: BridgeConfig, text: String): HaExecResult {
        val start = System.currentTimeMillis()
        if (!config.hasRealHaConfigured()) {
            return HaExecResult(false, "未配置 Home Assistant", System.currentTimeMillis() - start)
        }
        val payload = JSONObject().apply {
            put("text", text)
            put("language", "zh-cn")
        }.toString()

        var lastError: Throwable? = null
        for (baseUrl in config.normalizedUrls()) {
            try {
                val resp = postJson(baseUrl, "/api/conversation/process", config.haToken, payload, forSync = false)
                val root = JSONObject(resp)
                val responseObj = root.optJSONObject("response")
                val responseType = responseObj?.optString("response_type", "") ?: ""
                val speech = responseObj?.optJSONObject("speech")
                    ?.optJSONObject("plain")
                    ?.optString("speech", "")
                    .orEmpty()
                val ok = responseType != "error" && speech.isNotBlank()
                return HaExecResult(
                    success = ok,
                    message = speech.ifBlank { if (ok) "已执行" else "Home Assistant 未理解该指令" },
                    latencyMs = System.currentTimeMillis() - start
                )
            } catch (t: Throwable) {
                lastError = t
            }
        }
        return HaExecResult(
            success = false,
            message = "连接 Home Assistant 失败：${lastError?.message ?: "网络超时"}",
            latencyMs = System.currentTimeMillis() - start
        )
    }

    /**
     * 利用 HA 原生 /api/template 拉取 entity_id -> area_name 映射，无需任何第三方插件
     */
    private fun fetchAreaMapSilently(baseUrl: String, token: String): Map<String, String> {
        return try {
            val tpl = """
                {%- set ns = namespace(items=[]) -%}
                {%- for s in states -%}
                  {%- set a = area_name(s.entity_id) -%}
                  {%- if a -%}
                    {%- set ns.items = ns.items + [s.entity_id ~ '|||' ~ a] -%}
                  {%- endif -%}
                {%- endfor -%}
                {{ ns.items | join('\n') }}
            """.trimIndent()
            val body = JSONObject().put("template", tpl).toString()
            val resp = postJson(baseUrl, "/api/template", token, body, forSync = true)
            val map = HashMap<String, String>()
            resp.lineSequence().forEach { line ->
                val idx = line.indexOf("|||")
                if (idx > 0) {
                    val eid = line.substring(0, idx).trim()
                    val area = line.substring(idx + 3).trim()
                    if (eid.isNotEmpty() && area.isNotEmpty()) {
                        map[eid] = area
                    }
                }
            }
            map
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    internal data class EntityVisualMeta(
        var lovelaceIcon: String = "",
        var lovelaceColor: String = "",
        var registryIcon: String = ""
    )

    internal fun extractColorFromAttributes(attrs: JSONObject?): String {
        if (attrs == null) return ""
        val named = parseColorField(attrs.opt("icon_color")).ifBlank { parseColorField(attrs.opt("color")) }
        if (named.isNotBlank()) return named

        val rgb = attrs.optJSONArray("rgb_color")
        if (rgb != null && rgb.length() >= 3) {
            val r = rgb.optInt(0, -1)
            val g = rgb.optInt(1, -1)
            val b = rgb.optInt(2, -1)
            if (r in 0..255 && g in 0..255 && b in 0..255) {
                return String.format("#%02X%02X%02X", r, g, b)
            }
        }

        val hs = attrs.optJSONArray("hs_color")
        if (hs != null && hs.length() >= 2) {
            val h = hs.optDouble(0, Double.NaN)
            val s = hs.optDouble(1, Double.NaN)
            if (!h.isNaN() && !s.isNaN()) {
                return hsvToHex(h.toFloat(), (s.toFloat() / 100f).coerceIn(0f, 1f))
            }
        }
        return ""
    }

    private fun optNonNullString(obj: JSONObject?, key: String): String {
        if (obj == null || obj.isNull(key)) return ""
        return obj.optString(key, "").trim().takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()
    }

    private fun parseColorField(raw: Any?): String = when (raw) {
        is String -> raw.trim().takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()
        is JSONArray -> if (raw.length() >= 3) {
            val r = raw.optInt(0, -1)
            val g = raw.optInt(1, -1)
            val b = raw.optInt(2, -1)
            if (r in 0..255 && g in 0..255 && b in 0..255) String.format("#%02X%02X%02X", r, g, b) else ""
        } else ""
        else -> ""
    }

    private fun hsvToHex(hueDeg: Float, sat: Float): String {
        val h = ((hueDeg % 360f) + 360f) % 360f
        val c = sat
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = 1f - c
        val (rf, gf, bf) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val r = ((rf + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((gf + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((bf + m) * 255f).roundToInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", r, g, b)
    }

    internal fun collectLovelaceVisualMeta(node: Any?, out: MutableMap<String, EntityVisualMeta>) {
        when (node) {
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectLovelaceVisualMeta(node.opt(i), out)
                }
            }

            is JSONObject -> {
                val eid = optNonNullString(node, "entity")
                    .ifBlank { optNonNullString(node, "entity_id") }
                if (eid.contains('.')) {
                    val icon = optNonNullString(node, "icon")
                    val color = parseColorField(node.opt("icon_color"))
                        .ifBlank { parseColorField(node.opt("color")) }
                    if (icon.isNotBlank() || color.isNotBlank()) {
                        val meta = out.getOrPut(eid) { EntityVisualMeta() }
                        if (icon.isNotBlank() && meta.lovelaceIcon.isBlank()) {
                            meta.lovelaceIcon = icon
                        }
                        if (color.isNotBlank() && meta.lovelaceColor.isBlank()) {
                            meta.lovelaceColor = color
                        }
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val child = node.opt(k)
                    if (child is JSONObject || child is JSONArray) {
                        collectLovelaceVisualMeta(child, out)
                    }
                }
            }
        }
    }

    /**
     * 通过 HA 原生 WebSocket 拉取实体注册表与 Lovelace 仪表盘卡片里的自定义图标和配色，
     * 超时或反代不支持 WebSocket 时静默降级，仅依赖 /api/states。
     */
    private fun fetchVisualMetaSilently(baseUrl: String, token: String): Map<String, EntityVisualMeta> {
        val httpUrl = baseUrl.toHttpUrlOrNull() ?: return emptyMap()
        val wsUrl = httpUrl.newBuilder()
            .scheme(if (httpUrl.isHttps) "https" else "http")
            .encodedPath("/api/websocket")
            .build()
            .toString()
            .let { url ->
                when {
                    url.startsWith("https://") -> "wss://" + url.removePrefix("https://")
                    url.startsWith("http://") -> "ws://" + url.removePrefix("http://")
                    else -> url
                }
            }

        val resultMap = ConcurrentHashMap<String, EntityVisualMeta>()
        val doneLatch = CountDownLatch(1)
        val inflight = AtomicInteger(0)
        val nextId = AtomicInteger(1)
        val lovelaceConfigIds = ConcurrentHashMap.newKeySet<Int>()
        var registryReqId = -1
        var dashboardsReqId = -1
        var wsRef: WebSocket? = null

        return try {
            val req = Request.Builder().url(wsUrl).build()
            wsRef = clientFor(baseUrl, forSync = true).newWebSocket(req, object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
                    when (msg.optString("type")) {
                        "auth_required" -> {
                            webSocket.send(
                                JSONObject()
                                    .put("type", "auth")
                                    .put("access_token", token)
                                    .toString()
                            )
                        }

                        "auth_ok" -> {
                            registryReqId = nextId.getAndIncrement()
                            dashboardsReqId = nextId.getAndIncrement()
                            val defaultCfgId = nextId.getAndIncrement()
                            lovelaceConfigIds.add(defaultCfgId)
                            inflight.addAndGet(3)

                            webSocket.send(JSONObject().put("id", registryReqId).put("type", "config/entity_registry/list").toString())
                            webSocket.send(JSONObject().put("id", dashboardsReqId).put("type", "lovelace/dashboards/list").toString())
                            webSocket.send(JSONObject().put("id", defaultCfgId).put("type", "lovelace/config").toString())
                        }

                        "auth_invalid" -> doneLatch.countDown()

                        "result" -> {
                            val id = msg.optInt("id", -1)
                            val ok = msg.optBoolean("success", false)
                            if (ok) {
                                when {
                                    id == registryReqId -> {
                                        val arr = msg.optJSONArray("result")
                                        if (arr != null) {
                                            for (i in 0 until arr.length()) {
                                                val item = arr.optJSONObject(i) ?: continue
                                                val eid = optNonNullString(item, "entity_id")
                                                val icon = optNonNullString(item, "icon")
                                                if (eid.contains('.') && icon.isNotBlank()) {
                                                    resultMap.getOrPut(eid) { EntityVisualMeta() }.registryIcon = icon
                                                }
                                            }
                                        }
                                    }

                                    id == dashboardsReqId -> {
                                        val arr = msg.optJSONArray("result")
                                        if (arr != null) {
                                            val limit = arr.length().coerceAtMost(10)
                                            for (i in 0 until limit) {
                                                val dash = arr.optJSONObject(i) ?: continue
                                                val urlPath = optNonNullString(dash, "url_path")
                                                if (urlPath.isNotEmpty()) {
                                                    val cfgId = nextId.getAndIncrement()
                                                    lovelaceConfigIds.add(cfgId)
                                                    inflight.incrementAndGet()
                                                    webSocket.send(
                                                        JSONObject()
                                                            .put("id", cfgId)
                                                            .put("type", "lovelace/config")
                                                            .put("url_path", urlPath)
                                                            .toString()
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    id in lovelaceConfigIds -> {
                                        collectLovelaceVisualMeta(msg.opt("result"), resultMap)
                                    }
                                }
                            }
                            if (inflight.decrementAndGet() <= 0) {
                                doneLatch.countDown()
                            }
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    doneLatch.countDown()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    doneLatch.countDown()
                }
            })

            doneLatch.await(3500, TimeUnit.MILLISECONDS)
            resultMap
        } catch (_: Throwable) {
            emptyMap()
        } finally {
            runCatching { wsRef?.close(1000, "sync_done") }
        }
    }

    private fun get(baseUrl: String, path: String, token: String, forSync: Boolean): String {
        val req = Request.Builder()
            .url(baseUrl + path)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .get()
            .build()
        clientFor(baseUrl, forSync).newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}")
            }
            return body
        }
    }

    private fun postJson(baseUrl: String, path: String, token: String, json: String, forSync: Boolean): String {
        val req = Request.Builder()
            .url(baseUrl + path)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(json.toRequestBody(jsonMediaType))
            .build()
        clientFor(baseUrl, forSync).newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}")
            }
            return body
        }
    }
}

package cc.csxcl.xiaobuhabridge.hook

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 追踪流式 ASR 拼接（对齐原厂 StreamAsrRecorder 语义）+ 被拦截的 recordId 集合（带 TTL 自动清理）
 */
object StreamAsrTracker {
    private const val TTL_MS = 20_000L

    private data class StreamSentence(var seqNo: Int, var text: String)
    private data class StreamState(
        val updatedAt: Long,
        val sentences: MutableList<StreamSentence> = mutableListOf(),
        var fullText: String = ""
    )

    private val streamMap = ConcurrentHashMap<String, StreamState>()
    private val interceptedIds = ConcurrentHashMap<String, Long>()
    private val dispatchedIds = ConcurrentHashMap<String, Long>()

    fun updateStreamChunk(recordId: String, payload: JSONObject): String {
        cleanupExpired()
        val key = recordId.ifBlank { "default" }
        val content = payload.optString("content", "")
        val mode = payload.optString("mode", "")
        val seqNo = payload.optInt("seqNo", 0)
        val replaceIndex = payload.optInt("replaceIndex", 0)

        val now = System.currentTimeMillis()
        val state = streamMap.compute(key) { _, old ->
            if (old == null || now - old.updatedAt > TTL_MS) {
                StreamState(
                    updatedAt = now,
                    sentences = mutableListOf(StreamSentence(seqNo, content)),
                    fullText = content
                )
            } else {
                when (mode) {
                    "append" -> {
                        val existing = old.sentences.firstOrNull { it.seqNo == seqNo }
                        if (existing != null) {
                            existing.text += content
                        } else {
                            old.sentences.add(StreamSentence(seqNo, content))
                        }
                        old.fullText = old.sentences.joinToString("") { it.text }
                    }
                    "replace" -> {
                        old.sentences.filter { it.seqNo == replaceIndex }.forEach { it.text = content }
                        old.fullText = old.sentences.joinToString("") { it.text }
                    }
                    else -> {
                        old.sentences.clear()
                        old.sentences.add(StreamSentence(seqNo, content))
                        old.fullText = content
                    }
                }
                StreamState(now, old.sentences, old.fullText)
            }
        }
        return state?.fullText.orEmpty()
    }

    fun markIntercepted(vararg ids: String?) {
        cleanupExpired()
        val now = System.currentTimeMillis()
        ids.forEach { id ->
            if (!id.isNullOrBlank()) {
                interceptedIds[id] = now
            }
        }
    }

    fun isIntercepted(vararg ids: String?): Boolean {
        val now = System.currentTimeMillis()
        return ids.any { id ->
            if (id.isNullOrBlank()) return@any false
            val ts = interceptedIds[id] ?: return@any false
            now - ts <= TTL_MS
        }
    }

    /** 返回 true 表示是首次分发，避免同一条 recordId 重复执行两次 HA 调用 */
    fun markDispatchedOnce(vararg ids: String?): Boolean {
        cleanupExpired()
        val now = System.currentTimeMillis()
        val nonBlank = ids.filterNotNull().filter { it.isNotBlank() }
        if (nonBlank.isEmpty()) return true
        if (nonBlank.any { id -> dispatchedIds[id]?.let { now - it <= TTL_MS } == true }) {
            return false
        }
        nonBlank.forEach { dispatchedIds[it] = now }
        return true
    }

    private fun cleanupExpired() {
        val now = System.currentTimeMillis()
        interceptedIds.entries.removeIf { now - it.value > TTL_MS }
        dispatchedIds.entries.removeIf { now - it.value > TTL_MS }
        streamMap.entries.removeIf { now - it.value.updatedAt > TTL_MS }
    }
}

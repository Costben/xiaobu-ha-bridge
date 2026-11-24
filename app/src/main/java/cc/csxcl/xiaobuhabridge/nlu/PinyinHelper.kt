package cc.csxcl.xiaobuhabridge.nlu

import java.util.concurrent.ConcurrentHashMap

/**
 * 零外部依赖的中文拼音归一化器：
 * - 真机 Android 10+ 优先调用框架内置 android.icu.text.Transliterator("Han-Latin; NFD; [:Nonspacing Mark:] Remove; NFC")
 * - 覆盖全部 Unicode 汉字，并做内存缓存（<0.05ms 命中）
 * - 在 JVM 单测环境下自动退化到高频智能家居同音字典，保证单测可独立运行
 */
object PinyinHelper {
    private val cache = ConcurrentHashMap<String, String>()

    @Volatile
    private var icuReady: Boolean? = null

    @Volatile
    private var icuTransliterator: Any? = null

    @Volatile
    private var icuTransliterateMethod: java.lang.reflect.Method? = null

    private val fallbackCharMap: Map<Char, String> = mapOf(
        '吸' to "xi", '西' to "xi", '息' to "xi", '熄' to "xi", '洗' to "xi",
        '顶' to "ding", '丁' to "ding", '定' to "ding",
        '灯' to "deng", '登' to "deng", '等' to "deng",
        '卧' to "wo", '我' to "wo", '室' to "shi", '是' to "shi", '事' to "shi",
        '主' to "zhu", '住' to "zhu", '次' to "ci",
        '客' to "ke", '厅' to "ting", '听' to "ting", '停' to "ting",
        '书' to "shu", '舒' to "shu", '房' to "fang", '放' to "fang",
        '厨' to "chu", '除' to "chu", '卫' to "wei", '生' to "sheng", '间' to "jian",
        '阳' to "yang", '台' to "tai", '太' to "tai",
        '玄' to "xuan", '悬' to "xuan", '关' to "guan", '观' to "guan", '管' to "guan",
        '走' to "zou", '廊' to "lang", '餐' to "can", '浴' to "yu", '霸' to "ba",
        '空' to "kong", '控' to "kong", '调' to "tiao", '条' to "tiao",
        '窗' to "chuang", '床' to "chuang", '帘' to "lian", '连' to "lian", '头' to "tou",
        '风' to "feng", '封' to "feng", '扇' to "shan", '闪' to "shan",
        '插' to "cha", '查' to "cha", '座' to "zuo", '做' to "zuo", '坐' to "zuo",
        '排' to "pai", '开' to "kai", '电' to "dian", '视' to "shi",
        '投' to "tou", '影' to "ying", '仪' to "yi",
        '加' to "jia", '湿' to "shi", '器' to "qi", '气' to "qi", '净' to "jing", '化' to "hua",
        '扫' to "sao", '地' to "di", '机' to "ji", '新' to "xin",
        '带' to "dai", '代' to "dai", '筒' to "tong", '同' to "tong", '射' to "she", '社' to "she",
        '夜' to "ye", '也' to "ye", '氛' to "fen", '围' to "wei", '吊' to "diao", '壁' to "bi",
        '晚' to "wan", '安' to "an", '离' to "li", '家' to "jia", '回' to "hui", '睡' to "shui", '觉' to "jiao"
    )

    fun toPinyin(input: String): String {
        if (input.isEmpty()) return ""
        cache[input]?.let { return it }
        val normalized = computePinyin(input)
        if (cache.size < 2048) {
            cache[input] = normalized
        }
        return normalized
    }

    private fun computePinyin(input: String): String {
        val icuResult = tryIcu(input)
        if (icuResult != null) {
            return cleanLatin(icuResult)
        }
        val sb = StringBuilder(input.length * 3)
        for (ch in input.lowercase()) {
            when {
                ch in 'a'..'z' || ch in '0'..'9' -> sb.append(ch)
                fallbackCharMap.containsKey(ch) -> sb.append(fallbackCharMap[ch])
                ch.code >= 0x4E00 -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun tryIcu(input: String): String? {
        if (icuReady == false) return null
        return try {
            if (icuReady == null) {
                val cls = Class.forName("android.icu.text.Transliterator")
                val getInstance = cls.getMethod("getInstance", String::class.java)
                val instance = getInstance.invoke(null, "Han-Latin; NFD; [:Nonspacing Mark:] Remove; NFC")
                val method = cls.getMethod("transliterate", String::class.java)
                icuTransliterator = instance
                icuTransliterateMethod = method
                icuReady = true
            }
            icuTransliterateMethod?.invoke(icuTransliterator, input) as? String
        } catch (_: Throwable) {
            icuReady = false
            null
        }
    }

    private fun cleanLatin(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw.lowercase()) {
            if (ch in 'a'..'z' || ch in '0'..'9') {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * 判断两个短词在拼音层面是否等价或高度近似（容忍 1 字符模糊，用于纠偏 ASR 同音误识别，如“西顶灯”≈“吸顶灯”）
     */
    fun isPinyinSimilar(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        if (a.equals(b, ignoreCase = true)) return true
        val pa = toPinyin(a)
        val pb = toPinyin(b)
        if (pa.isEmpty() || pb.isEmpty()) return false
        if (pa == pb) return true
        // 至少 2 个汉字以上才允许编辑距离 <= 1，避免单字过度误匹配
        if (a.length >= 2 && b.length >= 2 && kotlin.math.abs(pa.length - pb.length) <= 1) {
            return levenshtein(pa, pb) <= 1
        }
        return false
    }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in a.indices) {
            curr[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                curr[j + 1] = minOf(curr[j] + 1, prev[j + 1] + 1, prev[j] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }
}

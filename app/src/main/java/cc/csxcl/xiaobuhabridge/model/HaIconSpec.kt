package cc.csxcl.xiaobuhabridge.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.core.graphics.PathParser
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 解析并映射 Home Assistant / Lovelace 的自定义 `mdi:*` 图标与颜色值。
 * 纯颜色解析与路径查表不依赖 Android 运行时图形类，可直接跑 JVM 单元测试；
 * 位图绘制仅在 [renderFluidBadgeBitmap] 被 Android UI/通知层调用时使用。
 */
object HaIconSpec {

    private val NAMED_COLORS_ARGB: Map<String, Int> = mapOf(
        "red" to 0xFFF44336.toInt(),
        "pink" to 0xFFE91E63.toInt(),
        "purple" to 0xFF9C27B0.toInt(),
        "deep-purple" to 0xFF673AB7.toInt(),
        "indigo" to 0xFF3F51B5.toInt(),
        "blue" to 0xFF2196F3.toInt(),
        "light-blue" to 0xFF03A9F4.toInt(),
        "cyan" to 0xFF00BCD4.toInt(),
        "teal" to 0xFF009688.toInt(),
        "green" to 0xFF4CAF50.toInt(),
        "light-green" to 0xFF8BC34A.toInt(),
        "lime" to 0xFFCDDC39.toInt(),
        "yellow" to 0xFFFFEB3B.toInt(),
        "amber" to 0xFFFFC107.toInt(),
        "orange" to 0xFFFF9800.toInt(),
        "deep-orange" to 0xFFFF5722.toInt(),
        "brown" to 0xFF795548.toInt(),
        "grey" to 0xFF9E9E9E.toInt(),
        "gray" to 0xFF9E9E9E.toInt(),
        "blue-grey" to 0xFF607D8B.toInt(),
        "blue-gray" to 0xFF607D8B.toInt(),
        "black" to 0xFF212121.toInt(),
        "white" to 0xFFFFFFFF.toInt(),
        "primary" to 0xFF03A9F4.toInt(),
        "accent" to 0xFFFF9800.toInt(),
        "disabled" to 0xFF9E9E9E.toInt()
    )

    // Material Design Icons (24x24 viewBox) SVG path strings
    private const val PATH_LIGHTBULB =
        "M12,2A7,7 0 0,0 5,9C5,11.38 6.19,13.47 8,14.74V17A1,1 0 0,0 9,18H15A1,1 0 0,0 16,17V14.74C17.81,13.47 19,11.38 19,9A7,7 0 0,0 12,2M9,21A1,1 0 0,0 10,22H14A1,1 0 0,0 15,21V20H9V21Z"
    private const val PATH_LIGHTBULB_ON =
        "M12,6A6,6 0 0,0 6,12C6,14.22 7.21,16.16 9,17.2V19A1,1 0 0,0 10,20H14A1,1 0 0,0 15,19V17.2C16.79,16.16 18,14.22 18,12A6,6 0 0,0 12,6M14,21V22A1,1 0 0,1 13,23H11A1,1 0 0,1 10,22V21H14M20,11H23V13H20V11M1,11H4V13H1V11M13,1V4H11V1H13M4.92,3.5L7.05,5.64L5.63,7.05L3.5,4.93L4.92,3.5M16.95,5.63L19.07,3.5L20.5,4.93L18.37,7.05L16.95,5.63Z"
    private const val PATH_DESK_LAMP =
        "M8,2H13L15,7H10L8,2M11,9H14L17,17H15L12.5,10.5L10,17H8L11,9M7,19H17V21H7V19Z"
    private const val PATH_LAMP =
        "M8,2H16L20,14H4L8,2M11,15H13V20H18V22H6V20H11V15Z"
    private const val PATH_CEILING_LIGHT =
        "M8,9H11V4H13V9H16L20,17H4L8,9M14,18A2,2 0 0,1 12,20A2,2 0 0,1 10,18H14Z"
    private const val PATH_LED_STRIP =
        "M2,7H22V10H2V7M5,11H7V14H5V11M11,11H13V14H11V11M17,11H19V14H17V11M2,15H22V17H2V15Z"
    private const val PATH_AIR_CONDITIONER =
        "M3,4H21A2,2 0 0,1 23,6V12A2,2 0 0,1 21,14H3A2,2 0 0,1 1,12V6A2,2 0 0,1 3,4M5,8V10H19V8H5M6,16H8V20H6V16M11,16H13V21H11V16M16,16H18V20H16V16Z"
    private const val PATH_FAN =
        "M12,11A1,1 0 0,0 11,12A1,1 0 0,0 12,13A1,1 0 0,0 13,12A1,1 0 0,0 12,11M12.5,2C17,2 17.11,5.57 14.75,6.75C13.76,7.24 13.32,8.29 13.13,9.22C13.61,9.42 14.03,9.73 14.35,10.13C18.05,8.13 22.03,8.92 22.03,12.5C22.03,17 18.46,17.1 17.28,14.73C16.78,13.74 15.72,13.3 14.79,13.11C14.59,13.59 14.28,14 13.88,14.34C15.87,18.03 15.08,22 11.5,22C7,22 6.91,18.42 9.27,17.24C10.25,16.75 10.69,15.71 10.89,14.79C10.4,14.59 9.97,14.27 9.65,13.87C5.96,15.85 2,15.07 2,11.5C2,7 5.56,6.89 6.74,9.26C7.24,10.25 8.29,10.68 9.22,10.87C9.41,10.39 9.73,9.97 10.14,9.65C8.15,5.96 8.94,2 12.5,2Z"
    private const val PATH_ARROW_OSCILLATING =
        "M7,4L3,8L7,12V9H17V12L21,8L17,4V7H7V4M2,14H22V16H2V14M7,18H17V20H7V18Z"
    private const val PATH_TOGGLE_SWITCH =
        "M17,7H7A5,5 0 0,0 2,12A5,5 0 0,0 7,17H17A5,5 0 0,0 22,12A5,5 0 0,0 17,7M17,15A3,3 0 0,1 14,12A3,3 0 0,1 17,9A3,3 0 0,1 20,12A3,3 0 0,1 17,15Z"
    private const val PATH_LIGHT_SWITCH =
        "M7,2H17A2,2 0 0,1 19,4V20A2,2 0 0,1 17,22H7A2,2 0 0,1 5,20V4A2,2 0 0,1 7,2M7,4V20H17V4H7M9,7H15V17H9V7M11,9V12H13V9H11Z"
    private const val PATH_POWER_SOCKET =
        "M4,2H20A2,2 0 0,1 22,4V20A2,2 0 0,1 20,22H4A2,2 0 0,1 2,20V4A2,2 0 0,1 4,2M4,4V20H20V4H4M12,6A6,6 0 0,1 18,12A6,6 0 0,1 12,18A6,6 0 0,1 6,12A6,6 0 0,1 12,6M9,10V13H10.5V10H9M13.5,10V13H15V10H13.5M11.25,14V16H12.75V14H11.25Z"
    private const val PATH_POWER_PLUG =
        "M16,7V3H14V7H10V3H8V7H8C7,7 6,8 6,9V14.5L9.5,18V21H14.5V18L18,14.5V9C18,8 17,7 16,7Z"
    private const val PATH_POWER =
        "M16.56,5.44L15.11,6.89C16.84,7.94 18,9.83 18,12A6,6 0 0,1 12,18A6,6 0 0,1 6,12C6,9.83 7.16,7.94 8.88,6.88L7.44,5.44C5.36,6.88 4,9.28 4,12A8,8 0 0,0 12,20A8,8 0 0,0 20,12C20,9.28 18.64,6.88 16.56,5.44M13,3H11V13H13"
    private const val PATH_FLASH =
        "M7,2V13H10V22L17,10H13L17,2H7Z"
    private const val PATH_TELEVISION =
        "M21,17H3V5H21M21,3H3A2,2 0 0,0 1,5V17A2,2 0 0,0 3,19H8V21H16V19H21A2,2 0 0,0 23,17V5A2,2 0 0,0 21,3Z"
    private const val PATH_DESKTOP_TOWER_MONITOR =
        "M1.5,4C1.1,4 0.8,4.3 0.8,4.8V16.2C0.8,16.7 1.1,17 1.5,17H5.5V19H3.5V20H9.5V19H7.5V17H11.5C11.9,17 12.2,16.7 12.2,16.2V4.8C12.2,4.3 11.9,4 11.5,4H1.5M2.3,5.5H10.7V15.2H2.3V5.5M14,4C13.4,4 13,4.4 13,5V19C13,19.6 13.4,20 14,20H22C22.6,20 23,19.6 23,19V5C23,4.4 22.6,4 22,4H14M15,6H21V8H15V6M15,9H21V11H15V9M18,15.5A1.5,1.5 0 0,1 19.5,17A1.5,1.5 0 0,1 18,18.5A1.5,1.5 0 0,1 16.5,17A1.5,1.5 0 0,1 18,15.5Z"
    private const val PATH_DESK =
        "M2,6H22V8H20V18H18V12H14V18H12V8H4V18H2V6M14,8V10H18V8H14Z"
    private const val PATH_SPEAKER =
        "M12,12A3,3 0 0,0 9,15A3,3 0 0,0 12,18A3,3 0 0,0 15,15A3,3 0 0,0 12,12M12,20A5,5 0 0,1 7,15A5,5 0 0,1 12,10A5,5 0 0,1 17,15A5,5 0 0,1 12,20M12,4A2,2 0 0,1 14,6A2,2 0 0,1 12,8C10.89,8 10,7.1 10,6C10,4.89 10.89,4 12,4M17,2H7C5.89,2 5,2.89 5,4V20A2,2 0 0,0 7,22H17A2,2 0 0,0 19,20V4C19,2.89 18.1,2 17,2Z"
    private const val PATH_CURTAINS =
        "M23,3H1V1H23V3M2,4V22H10C10,16 6,14 6,14C6,14 10,11 10,4H2M22,4H14C14,11 18,14 18,14C18,14 14,16 14,22H22V4Z"
    private const val PATH_BLINDS =
        "M20,19V3H4V19H2V21H22V19H20M18,5V9H6V5H18M6,19V11H11V14.27C10.4,14.62 10,15.26 10,16A2,2 0 0,0 12,18A2,2 0 0,0 14,16C14,15.26 13.6,14.62 13,14.27V11H18V19H6Z"
    private const val PATH_LOCK =
        "M12,17A2,2 0 0,0 14,15C14,13.89 13.1,13 12,13A2,2 0 0,0 10,15A2,2 0 0,0 12,17M18,8A2,2 0 0,1 20,10V20A2,2 0 0,1 18,22H6A2,2 0 0,1 4,20V10C4,8.89 4.9,8 6,8H7V6A5,5 0 0,1 12,1A5,5 0 0,1 17,6V8H18M12,3A3,3 0 0,0 9,6V8H15V6A3,3 0 0,0 12,3Z"
    private const val PATH_ROBOT_VACUUM =
        "M12,2A10,10 0 0,0 2,12A10,10 0 0,0 12,22A10,10 0 0,0 22,12A10,10 0 0,0 12,2M12,4A8,8 0 0,1 19.3,8.8L17.4,9.6A6,6 0 0,0 6.6,9.6L4.7,8.8A8,8 0 0,1 12,4M12,8.5A3.5,3.5 0 0,1 15.5,12A3.5,3.5 0 0,1 12,15.5A3.5,3.5 0 0,1 8.5,12A3.5,3.5 0 0,1 12,8.5M12,10.5A1.5,1.5 0 0,0 10.5,12A1.5,1.5 0 0,0 12,13.5A1.5,1.5 0 0,0 13.5,12A1.5,1.5 0 0,0 12,10.5Z"
    private const val PATH_THERMOMETER =
        "M15,13V5A3,3 0 0,0 9,5V13A5,5 0 1,0 15,13M12,4A1,1 0 0,1 13,5V8H11V5A1,1 0 0,1 12,4Z"
    private const val PATH_WATER_PERCENT =
        "M12,3.25C12,3.25 6,10 6,14C6,17.32 8.69,20 12,20A6,6 0 0,0 18,14C18,10 12,3.25 12,3.25M14.47,9.97L15.53,11.03L9.53,17.03L8.47,15.97M9.75,10A1.25,1.25 0 0,1 11,11.25A1.25,1.25 0 0,1 9.75,12.5A1.25,1.25 0 0,1 8.5,11.25A1.25,1.25 0 0,1 9.75,10M14.25,14.5A1.25,1.25 0 0,1 15.5,15.75A1.25,1.25 0 0,1 14.25,17A1.25,1.25 0 0,1 13,15.75A1.25,1.25 0 0,1 14.25,14.5Z"
    private const val PATH_PALETTE =
        "M17.5,12A1.5,1.5 0 0,1 16,10.5A1.5,1.5 0 0,1 17.5,9A1.5,1.5 0 0,1 19,10.5A1.5,1.5 0 0,1 17.5,12M14.5,8A1.5,1.5 0 0,1 13,6.5A1.5,1.5 0 0,1 14.5,5A1.5,1.5 0 0,1 16,6.5A1.5,1.5 0 0,1 14.5,8M9.5,8A1.5,1.5 0 0,1 8,6.5A1.5,1.5 0 0,1 9.5,5A1.5,1.5 0 0,1 11,6.5A1.5,1.5 0 0,1 9.5,8M6.5,12A1.5,1.5 0 0,1 5,10.5A1.5,1.5 0 0,1 6.5,9A1.5,1.5 0 0,1 8,10.5A1.5,1.5 0 0,1 6.5,12M12,3A9,9 0 0,0 3,12A9,9 0 0,0 12,21A1.5,1.5 0 0,0 13.5,19.5C13.5,19.11 13.35,18.76 13.11,18.5C12.88,18.23 12.73,17.88 12.73,17.5A1.5,1.5 0 0,1 14.23,16H16A5,5 0 0,0 21,11C21,6.58 16.97,3 12,3Z"
    private const val PATH_SCRIPT_TEXT =
        "M17,8H20V6H17V8M17,12H20V10H17V12M17,16H20V14H17V16M14,4H4A2,2 0 0,0 2,6V18A2,2 0 0,0 4,20H14A2,2 0 0,0 16,18V6A2,2 0 0,0 14,4M12,16H6V14H12V16M12,12H6V10H12V12M12,8H6V6H12V8Z"
    private const val PATH_PLAY =
        "M8,5.14V19.14L19,12.14L8,5.14Z"
    private const val PATH_HOME_AUTOMATION =
        "M12,3L2,12H5V20H19V12H22L12,3M12,8.5A3.5,3.5 0 0,1 15.5,12A3.5,3.5 0 0,1 12,15.5A3.5,3.5 0 0,1 8.5,12A3.5,3.5 0 0,1 12,8.5M12,10.5A1.5,1.5 0 0,0 10.5,12A1.5,1.5 0 0,0 12,13.5A1.5,1.5 0 0,0 13.5,12A1.5,1.5 0 0,0 12,10.5Z"
    private const val PATH_EYE =
        "M12,9A3,3 0 0,0 9,12A3,3 0 0,0 12,15A3,3 0 0,0 15,12A3,3 0 0,0 12,9M12,17A5,5 0 0,1 7,12A5,5 0 0,1 12,7A5,5 0 0,1 17,12A5,5 0 0,1 12,17M12,4.5C7,4.5 2.73,7.61 1,12C2.73,16.39 7,19.5 12,19.5C17,19.5 21.27,16.39 23,12C21.27,7.61 17,4.5 12,4.5Z"
    private const val PATH_SNOWFLAKE =
        "M20.79,13.95L18.46,14.57L16.46,13.44V10.56L18.46,9.43L20.79,10.05L21.31,8.12L19.54,7.65L20,5.88L18.07,5.36L17.45,7.69L15.45,8.82L13,7.38V5.12L14.71,3.41L13.29,2L12,3.29L10.71,2L9.29,3.41L11,5.12V7.38L8.5,8.82L6.5,7.69L5.92,5.36L4,5.88L4.47,7.65L2.7,8.12L3.22,10.05L5.55,9.43L7.55,10.56V13.45L5.55,14.58L3.22,13.96L2.7,15.89L4.47,16.36L4,18.12L5.93,18.64L6.55,16.31L8.55,15.18L11,16.62V18.88L9.29,20.59L10.71,22L12,20.71L13.29,22L14.7,20.59L13,18.88V16.62L15.5,15.17L17.5,16.3L18.12,18.63L20,18.12L19.53,16.35L21.3,15.88L20.79,13.95M9.5,10.56L12,9.11L14.5,10.56V13.44L12,14.89L9.5,13.44V10.56Z"
    private const val PATH_FIRE =
        "M17.66,11.2C17.43,10.9 17.15,10.64 16.89,10.38C16.22,9.78 15.46,9.35 14.82,8.72C13.33,7.26 13,4.85 13.95,3C13,3.23 12.17,3.75 11.46,4.32C8.87,6.4 7.85,10.07 9.07,13.22C9.11,13.32 9.15,13.42 9.15,13.55C9.15,13.77 9,13.97 8.8,14.05C8.57,14.15 8.33,14.09 8.14,13.93C8.08,13.88 8.04,13.83 8,13.76C6.87,12.33 6.69,10.28 7.45,8.64C5.78,10 4.87,12.3 5,14.47C5.06,14.97 5.12,15.47 5.29,15.97C5.43,16.57 5.7,17.17 6,17.7C7.08,19.43 8.95,20.67 10.96,20.92C13.1,21.19 15.39,20.8 17.03,19.32C18.86,17.66 19.5,15 18.56,12.72L18.43,12.46C18.22,12 17.66,11.2 17.66,11.2Z"
    private const val PATH_CAMERA =
        "M4,4H7L9,2H15L17,4H20A2,2 0 0,1 22,6V18A2,2 0 0,1 20,20H4A2,2 0 0,1 2,18V6A2,2 0 0,1 4,4M12,7A5,5 0 0,0 7,12A5,5 0 0,0 12,17A5,5 0 0,0 17,12A5,5 0 0,0 12,7M12,9A3,3 0 0,1 15,12A3,3 0 0,1 12,15A3,3 0 0,1 9,12A3,3 0 0,1 12,9Z"

    private val MDI_PATHS: Map<String, String> = mapOf(
        "lightbulb" to PATH_LIGHTBULB,
        "lightbulb-outline" to PATH_LIGHTBULB,
        "lightbulb-on" to PATH_LIGHTBULB_ON,
        "lightbulb-on-outline" to PATH_LIGHTBULB_ON,
        "desk-lamp" to PATH_DESK_LAMP,
        "floor-lamp" to PATH_LAMP,
        "lamp" to PATH_LAMP,
        "ceiling-light" to PATH_CEILING_LIGHT,
        "ceiling-light-outline" to PATH_CEILING_LIGHT,
        "chandelier" to PATH_CEILING_LIGHT,
        "wall-sconce" to PATH_LAMP,
        "led-strip" to PATH_LED_STRIP,
        "led-strip-variant" to PATH_LED_STRIP,
        "air-conditioner" to PATH_AIR_CONDITIONER,
        "fan" to PATH_FAN,
        "fan-off" to PATH_FAN,
        "arrow-oscillating" to PATH_ARROW_OSCILLATING,
        "toggle-switch" to PATH_TOGGLE_SWITCH,
        "toggle-switch-off" to PATH_TOGGLE_SWITCH,
        "toggle-switch-variant" to PATH_TOGGLE_SWITCH,
        "light-switch" to PATH_LIGHT_SWITCH,
        "power-socket" to PATH_POWER_SOCKET,
        "power-socket-au" to PATH_POWER_SOCKET,
        "power-socket-us" to PATH_POWER_SOCKET,
        "power-socket-eu" to PATH_POWER_SOCKET,
        "power-socket-uk" to PATH_POWER_SOCKET,
        "power-plug" to PATH_POWER_PLUG,
        "power" to PATH_POWER,
        "flash" to PATH_FLASH,
        "lightning-bolt" to PATH_FLASH,
        "television" to PATH_TELEVISION,
        "television-classic" to PATH_TELEVISION,
        "monitor" to PATH_TELEVISION,
        "desktop-tower-monitor" to PATH_DESKTOP_TOWER_MONITOR,
        "desk" to PATH_DESK,
        "speaker" to PATH_SPEAKER,
        "cast" to PATH_TELEVISION,
        "curtains" to PATH_CURTAINS,
        "curtains-closed" to PATH_CURTAINS,
        "blinds" to PATH_BLINDS,
        "window-shutter" to PATH_BLINDS,
        "lock" to PATH_LOCK,
        "lock-open" to PATH_LOCK,
        "robot-vacuum" to PATH_ROBOT_VACUUM,
        "thermometer" to PATH_THERMOMETER,
        "water-percent" to PATH_WATER_PERCENT,
        "palette" to PATH_PALETTE,
        "script-text" to PATH_SCRIPT_TEXT,
        "play" to PATH_PLAY,
        "home" to PATH_HOME_AUTOMATION,
        "home-automation" to PATH_HOME_AUTOMATION,
        "eye" to PATH_EYE,
        "motion-sensor" to PATH_EYE,
        "snowflake" to PATH_SNOWFLAKE,
        "fire" to PATH_FIRE,
        "camera" to PATH_CAMERA,
        "cctv" to PATH_CAMERA
    )

    /**
     * 解析 HA 颜色（颜色名 / #RRGGBB / rgb(r,g,b)），返回 ARGB 整数；失败返回 null。
     */
    fun parseHaColorArgb(raw: String?): Int? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val lower = text.lowercase(Locale.ROOT)
        NAMED_COLORS_ARGB[lower.replace('_', '-').replace(' ', '-')]?.let { return it }

        if (lower.startsWith("#")) {
            val hex = lower.substring(1)
            val parsed = when (hex.length) {
                3 -> {
                    val r = hex[0].digitToIntOrNull(16) ?: return null
                    val g = hex[1].digitToIntOrNull(16) ?: return null
                    val b = hex[2].digitToIntOrNull(16) ?: return null
                    (0xFF shl 24) or ((r * 17) shl 16) or ((g * 17) shl 8) or (b * 17)
                }
                6 -> {
                    val rgb = hex.toLongOrNull(16) ?: return null
                    (0xFF000000L or rgb).toInt()
                }
                8 -> hex.toLongOrNull(16)?.toInt() ?: return null
                else -> null
            }
            return parsed
        }

        val rgbContent = lower
            .removePrefix("rgba(")
            .removePrefix("rgb(")
            .removeSuffix(")")
        val parts = rgbContent.split(',').mapNotNull { it.trim().toFloatOrNull()?.roundToInt() }
        if (parts.size >= 3) {
            val r = parts[0].coerceIn(0, 255)
            val g = parts[1].coerceIn(0, 255)
            val b = parts[2].coerceIn(0, 255)
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return null
    }

    fun argbToHex(argb: Int): String = String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)

    /**
     * 将色值提亮并柔和化，用于在深色流体云胶囊左侧/右侧保持高可读性。
     */
    fun brightCapsuleTextHex(argb: Int): String {
        val r = ((argb shr 16) and 0xFF)
        val g = ((argb shr 8) and 0xFF)
        val b = (argb and 0xFF)
        val nr = (r + ((255 - r) * 0.42f)).roundToInt().coerceIn(0, 255)
        val ng = (g + ((255 - g) * 0.42f)).roundToInt().coerceIn(0, 255)
        val nb = (b + ((255 - b) * 0.42f)).roundToInt().coerceIn(0, 255)
        return String.format(Locale.ROOT, "#%02X%02X%02X", nr, ng, nb)
    }

    const val INACTIVE_COLOR_ARGB: Int = 0xFF757575.toInt()

    private val ACTIVE_STATES: Set<String> = setOf(
        "on", "open", "opening", "playing", "unlocked", "cleaning",
        "heat", "cool", "auto", "dry", "fan_only", "heat_cool",
        "home", "armed_home", "armed_away", "armed_night", "armed_vacation", "armed_custom_bypass"
    )

    private val ALWAYS_COLORED_DOMAINS: Set<String> = setOf("scene", "script", "sensor", "button", "home")

    /**
     * 判断实体当前状态是否处于「开启/活跃」态（与 Home Assistant 保持一致：关闭态显示灰色，开启态才着色）。
     */
    fun isEntityStateActive(domain: String, state: String): Boolean {
        val d = domain.lowercase(Locale.ROOT)
        if (d in ALWAYS_COLORED_DOMAINS) return true
        val s = state.trim().lowercase(Locale.ROOT)
        if (s.isEmpty()) return true
        return s in ACTIVE_STATES
    }

    /**
     * 按 Home Assistant 规则解析实体最终图标色：关闭/非活跃态统一灰色，活跃态优先用自定义色，否则回退领域默认色。
     */
    fun resolveEntityColorArgb(iconColor: String?, domain: String, state: String): Int {
        if (!isEntityStateActive(domain, state)) return INACTIVE_COLOR_ARGB
        return parseHaColorArgb(iconColor) ?: defaultDomainColorArgb(domain, state)
    }

    /**
     * 为不同实体状态与 HA 配置选择默认颜色或解析用户配置颜色。
     */
    fun defaultDomainColorArgb(domain: String, state: String): Int {
        val active = isEntityStateActive(domain, state)
        return when (domain.lowercase(Locale.ROOT)) {
            "light" -> if (active) 0xFFFFA000.toInt() else INACTIVE_COLOR_ARGB
            "climate" -> if (active) 0xFF00ACC1.toInt() else INACTIVE_COLOR_ARGB
            "fan" -> if (active) 0xFF009688.toInt() else INACTIVE_COLOR_ARGB
            "switch" -> if (active) 0xFF2196F3.toInt() else INACTIVE_COLOR_ARGB
            "cover" -> if (active) 0xFF3F51B5.toInt() else INACTIVE_COLOR_ARGB
            "media_player" -> if (active) 0xFF7E57C2.toInt() else INACTIVE_COLOR_ARGB
            "vacuum" -> if (active) 0xFF26A69A.toInt() else INACTIVE_COLOR_ARGB
            "lock" -> if (active) 0xFF5C6BC0.toInt() else INACTIVE_COLOR_ARGB
            "scene" -> 0xFFE91E63.toInt()
            "script" -> 0xFF8E24AA.toInt()
            "sensor" -> 0xFF29B6F6.toInt()
            "binary_sensor" -> if (active) 0xFFFF9800.toInt() else INACTIVE_COLOR_ARGB
            else -> if (active) 0xFF18BCF2.toInt() else INACTIVE_COLOR_ARGB
        }
    }

    /**
     * 解析 `mdi:*` 图标名为 24x24 SVG path；未收录的图标按关键词与实体 domain 回退。
     */
    fun resolveMdiPathData(iconName: String?, domain: String): String {
        val raw = iconName?.trim().orEmpty().lowercase(Locale.ROOT)
        val key = raw.removePrefix("mdi:").trim()
        if (key.isNotEmpty()) {
            MDI_PATHS[key]?.let { return it }
            when {
                "lamp" in key -> return PATH_DESK_LAMP
                "ceiling" in key || "chandelier" in key -> return PATH_CEILING_LIGHT
                "led" in key || "strip" in key -> return PATH_LED_STRIP
                "light" in key || "bulb" in key -> return PATH_LIGHTBULB
                "air" in key || "ac" in key || "hvac" in key -> return PATH_AIR_CONDITIONER
                "fan" in key -> return PATH_FAN
                "oscillat" in key -> return PATH_ARROW_OSCILLATING
                "socket" in key || "outlet" in key -> return PATH_POWER_SOCKET
                "plug" in key -> return PATH_POWER_PLUG
                "switch" in key -> return PATH_TOGGLE_SWITCH
                "power" in key -> return PATH_POWER
                "flash" in key || "bolt" in key -> return PATH_FLASH
                "monitor" in key || "desktop" in key -> return PATH_DESKTOP_TOWER_MONITOR
                "tv" in key || "television" in key || "screen" in key -> return PATH_TELEVISION
                "desk" in key || "table" in key -> return PATH_DESK
                "speaker" in key || "audio" in key || "music" in key -> return PATH_SPEAKER
                "curtain" in key -> return PATH_CURTAINS
                "blind" in key || "shutter" in key -> return PATH_BLINDS
                "lock" in key -> return PATH_LOCK
                "vacuum" in key -> return PATH_ROBOT_VACUUM
                "therm" in key || "temp" in key -> return PATH_THERMOMETER
                "humid" in key || "water" in key -> return PATH_WATER_PERCENT
                "palette" in key || "scene" in key -> return PATH_PALETTE
                "script" in key -> return PATH_SCRIPT_TEXT
                "camera" in key -> return PATH_CAMERA
            }
        }
        return when (domain.lowercase(Locale.ROOT)) {
            "light" -> PATH_LIGHTBULB
            "switch" -> PATH_TOGGLE_SWITCH
            "fan" -> PATH_FAN
            "climate" -> PATH_AIR_CONDITIONER
            "cover" -> PATH_CURTAINS
            "media_player" -> PATH_SPEAKER
            "vacuum" -> PATH_ROBOT_VACUUM
            "lock" -> PATH_LOCK
            "scene" -> PATH_PALETTE
            "script" -> PATH_SCRIPT_TEXT
            "sensor" -> PATH_THERMOMETER
            "binary_sensor" -> PATH_EYE
            else -> PATH_HOME_AUTOMATION
        }
    }

    /**
     * 为系统通知栏 / LiveAlert `setLargeIcon` 绘制带 HA 配色圆底的实体图标。
     */
    fun renderFluidBadgeBitmap(
        iconName: String?,
        domain: String,
        accentArgb: Int,
        sizePx: Int = 96
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = sizePx / 2f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentArgb
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center, bgPaint)

        val pathData = resolveMdiPathData(iconName, domain)
        val path = PathParser.createPathFromPathData(pathData)
        val scale = (sizePx * 0.54f) / 24f
        val offset = (sizePx - 24f * scale) / 2f
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(offset, offset)
        }
        path.transform(matrix)

        val r = (accentArgb shr 16) and 0xFF
        val g = (accentArgb shr 8) and 0xFF
        val b = accentArgb and 0xFF
        val luma = 0.299f * r + 0.587f * g + 0.114f * b
        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (luma > 205f) 0xFF263238.toInt() else 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, fgPaint)
        return bitmap
    }
}

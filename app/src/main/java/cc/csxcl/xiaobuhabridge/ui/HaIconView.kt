package cc.csxcl.xiaobuhabridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.csxcl.xiaobuhabridge.model.HaEntity
import cc.csxcl.xiaobuhabridge.model.HaIconSpec
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 按 Home Assistant 配置的图标（`mdi:*`）与颜色渲染实体圆形徽章。
 */
@Composable
fun HaEntityIcon(
    entity: HaEntity,
    dimmed: Boolean = false,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val pathData = remember(entity.icon, entity.domain) {
        HaIconSpec.resolveMdiPathData(entity.icon, entity.domain)
    }
    val vector = remember(pathData) {
        val nodes = PathParser().parsePathString(pathData).toNodes()
        ImageVector.Builder(
            name = "HaEntityMdi",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).addPath(
            pathData = nodes,
            fill = SolidColor(Color.White)
        ).build()
    }

    val rawArgb = remember(entity.iconColor, entity.domain, entity.state) {
        HaIconSpec.resolveEntityColorArgb(entity.iconColor, entity.domain, entity.state)
    }
    val surfaceLum = COUITheme.colorScheme.surface.luminance()
    val rawColor = Color(rawArgb)
    val baseTint = when {
        dimmed -> COUITheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.55f)
        // 亮色模式下遇到 HA 设为 white / 极浅黄时保证前景色可读
        surfaceLum > 0.5f && rawColor.luminance() > 0.82f -> Color(0xFF607D8B)
        surfaceLum <= 0.5f && rawColor.luminance() < 0.08f -> Color(0xFFB0BEC5)
        else -> rawColor
    }
    val bgAlpha = when {
        dimmed -> 0.08f
        entity.iconColor.equals("white", ignoreCase = true) && surfaceLum <= 0.5f -> 0.20f
        else -> 0.14f
    }
    val borderAlpha = if (!dimmed && rawColor.luminance() > 0.85f && surfaceLum > 0.5f) 0.28f else 0f

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(baseTint.copy(alpha = bgAlpha))
            .then(
                if (borderAlpha > 0f) {
                    Modifier.border(0.75.dp, baseTint.copy(alpha = borderAlpha), CircleShape)
                } else {
                    Modifier
                }
            )
    ) {
        Icon(
            imageVector = vector,
            contentDescription = null,
            tint = baseTint,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

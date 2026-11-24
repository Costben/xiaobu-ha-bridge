package cc.csxcl.xiaobuhabridge.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.csxcl.xiaobuhabridge.XiaobuHaBridge
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.blur.isBlurEnabled
import io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.layout.AlertDialogButton
import io.github.suqi8.coui.kmp.overlay.OverlayAlertDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.util.concurrent.TimeUnit

@Composable
internal fun PaddingValues.bridgeListPadding(
    top: Dp,
    bottom: Dp,
    start: Dp = 0.dp,
    end: Dp = 0.dp,
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection) + start,
        top = calculateTopPadding() + top,
        end = calculateEndPadding(layoutDirection) + end,
        bottom = calculateBottomPadding() + bottom,
    )
}

@Composable
internal fun BridgeTopAppBar(
    title: String,
    scrollBehavior: ScrollBehavior,
    onBack: (() -> Unit)? = null,
    largeTitle: String = title,
    subtitle: String = "",
    gradientGlassExtraHeight: Dp = 0.dp,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val extraHeight = if (gradientGlassExtraHeight > 0.dp) {
        gradientGlassExtraHeight
    } else {
        16.dp
    }
    val isDark = COUITheme.colorScheme.background.luminance() < 0.5f
    val glassColor = if (isDark && isBlurEnabled()) {
        Color.Black
    } else {
        AppBarBlurOverlayDefaults.containerColor()
    }
    if (onBack != null) {
        SmallTopAppBar(
            title = title,
            subtitle = subtitle,
            color = glassColor,
            scrollBehavior = scrollBehavior,
            gradientGlass = true,
            gradientGlassExtraHeight = extraHeight,
            showDivider = false,
            navigationIcon = {
                BridgeBarIconButton(COUIIcons.Back, "返回", onBack)
            },
            actions = actions,
        )
    } else {
        TopAppBar(
            title = title,
            largeTitle = largeTitle,
            subtitle = subtitle,
            color = glassColor,
            scrollBehavior = scrollBehavior,
            gradientGlass = true,
            gradientGlassExtraHeight = extraHeight,
            showDivider = false,
            actions = actions,
        )
    }
}

@Composable
internal fun BridgeBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    HighlightedIconButton(
        onClick = onClick,
        modifier = Modifier.couiTopAppBarActionItem(),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

@Composable
internal fun BridgeGroupTitle(
    text: String,
    modifier: Modifier = Modifier,
    cardInset: Dp = 16.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = cardInset),
    ) {
        SmallTitle(
            text = text,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalCOUIApi::class)
@Composable
internal fun DefaultRestartSpeechDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    OverlayAlertDialog(
        show = show,
        title = "重启小布助手",
        summary = "将通过 Root 结束 ${XiaobuHaBridge.TARGET_SPEECH_PACKAGE} 进程，使最新配置与 Hook 生效。",
        confirmText = "立即重启",
        cancelText = "取消",
        recommendedButton = AlertDialogButton.Positive,
        onConfirm = {
            onDismiss()
            restartTargetPackage(mainHandler, context, XiaobuHaBridge.TARGET_SPEECH_PACKAGE)
        },
        onCancel = onDismiss
    )
}

private fun restartTargetPackage(handler: Handler, context: Context, pkg: String) {
    Toast.makeText(context, "正在重启作用域…", Toast.LENGTH_SHORT).show()
    Thread {
        val msg = try {
            val script = """
                total=0
                for pid in ${'$'}(pgrep -f "^${pkg}(:.*)?${'$'}"); do
                  kill -9 "${'$'}pid" 2>/dev/null && total=${'$'}((total + 1))
                done
                echo "killed=${'$'}total"
            """.trimIndent()
            val process = ProcessBuilder("su", "-c", script)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(20, TimeUnit.SECONDS)
            val output = runCatching { process.inputStream.bufferedReader().readText() }.getOrDefault("").trim()
            when {
                !finished -> {
                    process.destroyForcibly()
                    "执行超时，请确认 Root 授权"
                }
                process.exitValue() != 0 -> "需要 Root 权限（退出码 ${process.exitValue()}）"
                else -> {
                    val killed = Regex("killed=(\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    if (killed == 0) "目标进程未运行，下次启动即生效" else "已重启 $killed 个进程"
                }
            }
        } catch (t: Throwable) {
            "需要 Root 权限（${t.message}）"
        }
        handler.post {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }.start()
}

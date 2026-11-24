package cc.csxcl.xiaobuhabridge.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cc.csxcl.xiaobuhabridge.StandaloneBridgePrefsBackend
import cc.csxcl.xiaobuhabridge.XiaobuHaBridge
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.LocalPlatformAppBarMaterialEnabled
import io.github.suqi8.coui.kmp.nav.core.NavController
import io.github.suqi8.coui.kmp.nav.core.NavCornerClipMode
import io.github.suqi8.coui.kmp.nav.core.NavDisplay
import io.github.suqi8.coui.kmp.nav.core.NavDisplayEffects
import io.github.suqi8.coui.kmp.nav.core.NavKey
import io.github.suqi8.coui.kmp.nav.core.navBackStackOf
import io.github.suqi8.coui.kmp.nav.core.rememberNavSystemCornerRadius
import io.github.suqi8.coui.kmp.nav.transition.NavTransitions
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme

private sealed interface StandaloneBridgeRoute : NavKey {
    data object Home : StandaloneBridgeRoute
    data object Entities : StandaloneBridgeRoute
    data object Lab : StandaloneBridgeRoute
}

private val STANDALONE_CORNER_FALLBACK = 45.dp

/**
 * 独立 Xposed 模块主界面：启动即进入 Xiaobu Ha bridge 控制中心，支持家居实体管理与指令调试。
 */
class XiaobuHaBridgeActivity : ComponentActivity() {

    private var resumeCounter by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        XiaobuHaBridge.configureApp(
            context = this,
            launchActivityClassName = XiaobuHaBridgeActivity::class.java.name
        )
        if (Build.VERSION.SDK_INT >= 37 &&
            checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf("android.permission.ACCESS_LOCAL_NETWORK"), 18001)
        }
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            val prefsBackend = XiaobuHaBridge.prefsBackend as? StandaloneBridgePrefsBackend
            val prefsTick = prefsBackend?.prefsVersion ?: 0
            COUITheme(colors = colors) {
                CompositionLocalProvider(LocalPlatformAppBarMaterialEnabled provides false) {
                    StandaloneBridgeNavRoot(
                        context = this,
                        resumeTick = resumeCounter + prefsTick,
                        onPrefsChanged = { prefsBackend?.notifyWritten() },
                        onRequestPermission = { requestBridgePermissions(this) },
                        onFinishActivity = { finish() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeCounter++
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        window.decorView.post {
            if (!isDestroyed && window.decorView.isAttachedToWindow) {
                windowManager.updateViewLayout(window.decorView, window.attributes)
            }
        }
    }

    companion object {
        fun requestBridgePermissions(context: Context) {
            val activity = context as? Activity
            if (Build.VERSION.SDK_INT >= 37 && activity != null &&
                ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED
            ) {
                activity.requestPermissions(arrayOf("android.permission.ACCESS_LOCAL_NETWORK"), 18001)
                return
            }
            if (Build.VERSION.SDK_INT >= 33 && activity != null &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 18002)
                return
            }
            runCatching {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
    }
}

@Composable
private fun StandaloneBridgeNavRoot(
    context: Context,
    resumeTick: Int,
    onPrefsChanged: () -> Unit,
    onRequestPermission: () -> Unit,
    onFinishActivity: () -> Unit
) {
    val backStack = remember { navBackStackOf(StandaloneBridgeRoute.Home) }
    val navController = remember(backStack) { NavController(backStack) }
    val systemCorner = rememberNavSystemCornerRadius()
    val pageColor = AppBarBlurOverlayDefaults.containerColor()
    val effects = remember(systemCorner, pageColor) {
        NavDisplayEffects(
            cornerClipRadius = if (systemCorner > 0.dp) systemCorner else STANDALONE_CORNER_FALLBACK,
            cornerClipMode = NavCornerClipMode.All,
            dimAmount = 0.35f,
            blockInputDuringTransition = true,
            backdropColor = pageColor
        )
    }

    fun push(route: StandaloneBridgeRoute) {
        if (navController.backStack.lastOrNull() == route || route in navController.backStack) return
        navController.push(route)
    }

    fun pop() {
        if (navController.backStack.size <= 1) {
            onFinishActivity()
        } else {
            navController.pop()
        }
    }

    NavDisplay(
        navController = navController,
        modifier = Modifier.fillMaxSize(),
        onBack = ::pop,
        transition = NavTransitions.COUIDefault,
        effects = effects
    ) {
        entry<StandaloneBridgeRoute.Home> {
            BridgeAppScreen(
                context = context,
                resumeTick = resumeTick,
                onRequestNotificationPermission = onRequestPermission,
                onBack = null,
                page = BridgePage.HOME,
                onOpenPage = { page ->
                    push(
                        when (page) {
                            BridgePage.HOME -> StandaloneBridgeRoute.Home
                            BridgePage.ENTITIES -> StandaloneBridgeRoute.Entities
                            BridgePage.LAB -> StandaloneBridgeRoute.Lab
                        }
                    )
                },
                onPrefsChanged = onPrefsChanged
            )
        }
        entry<StandaloneBridgeRoute.Entities> {
            BridgeAppScreen(
                context = context,
                resumeTick = resumeTick,
                onRequestNotificationPermission = onRequestPermission,
                onBack = ::pop,
                page = BridgePage.ENTITIES,
                onPrefsChanged = onPrefsChanged
            )
        }
        entry<StandaloneBridgeRoute.Lab> {
            BridgeAppScreen(
                context = context,
                resumeTick = resumeTick,
                onRequestNotificationPermission = onRequestPermission,
                onBack = ::pop,
                page = BridgePage.LAB,
                onPrefsChanged = onPrefsChanged
            )
        }
    }
}

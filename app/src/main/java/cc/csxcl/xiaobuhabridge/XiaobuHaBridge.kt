package cc.csxcl.xiaobuhabridge

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import cc.csxcl.xiaobuhabridge.config.ConfigStore
import cc.csxcl.xiaobuhabridge.hook.BridgeHookEntry
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.lang.reflect.Method

/**
 * 跨进程配置通道抽象。
 */
interface BridgePrefsBackend {
    fun isRemoteChannelReady(): Boolean
    fun getAppWritePrefs(context: Context): SharedPreferences
    fun getAppReadPrefs(context: Context): SharedPreferences
    fun getHookPrefs(): SharedPreferences?
}

/**
 * 面向 Xposed / LibXposed 钩子的最小运行时抽象。
 */
interface BridgeHookRuntime {
    fun log(message: String)
    fun hookBefore(method: Method, callback: (args: Array<Any?>, setResult: (Any?) -> Unit) -> Unit)
    fun getModuleNativeLibraryDir(): String? = null
    fun getModuleSourceDir(): String? = null
}

/**
 * 独立 Xposed 模块默认配置后端：通过 LibXposed API 102 `getRemotePreferences` 同步配置，
 * 并在通道尚未就绪时自动回退到本地私有 `SharedPreferences`。
 */
class StandaloneBridgePrefsBackend(
    private val prefsName: String = ConfigStore.PREFS_NAME
) : BridgePrefsBackend {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var hookRemotePrefs: SharedPreferences? = null

    @Volatile
    private var appRemotePrefs: SharedPreferences? = null

    @Volatile
    private var serviceRegistered = false

    var prefsVersion by mutableIntStateOf(0)
        private set

    fun notifyWritten() {
        prefsVersion++
    }

    fun initHost(module: XposedModule) {
        if (hookRemotePrefs != null) return
        runCatching {
            hookRemotePrefs = module.getRemotePreferences(prefsName)
        }.onFailure { t ->
            Log.w(TAG, "getRemotePreferences in hook failed: ${t.message}")
        }
    }

    fun attachAppService(service: XposedService?) {
        appRemotePrefs = runCatching { service?.getRemotePreferences(prefsName) }.getOrNull()
        mainHandler.post { prefsVersion++ }
    }

    fun ensureAppServiceListener() {
        if (serviceRegistered) return
        serviceRegistered = true
        runCatching {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    attachAppService(service)
                }

                override fun onServiceDied(service: XposedService) {
                    attachAppService(null)
                }
            })
        }
    }

    override fun isRemoteChannelReady(): Boolean = appRemotePrefs != null

    override fun getAppWritePrefs(context: Context): SharedPreferences =
        appRemotePrefs ?: context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun getAppReadPrefs(context: Context): SharedPreferences =
        appRemotePrefs ?: context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun getHookPrefs(): SharedPreferences? = hookRemotePrefs

    private companion object {
        private const val TAG = "XiaobuHaBridgePrefs"
    }
}

/**
 * 直接基于官方 LibXposed API 102 [XposedModule] 的钩子运行时实现。
 */
class LibXposedBridgeHookRuntime(
    private val module: XposedModule
) : BridgeHookRuntime {

    override fun log(message: String) {
        module.log(Log.INFO, TAG, message)
    }

    override fun hookBefore(
        method: Method,
        callback: (args: Array<Any?>, setResult: (Any?) -> Unit) -> Unit
    ) {
        module.hook(method).intercept { chain: XposedInterface.Chain ->
            val args = chain.args.toTypedArray()
            var intercepted = false
            var customResult: Any? = null
            callback(args) { result ->
                intercepted = true
                customResult = result
            }
            if (intercepted) {
                customResult
            } else {
                chain.proceed(args)
            }
        }
    }

    override fun getModuleNativeLibraryDir(): String? =
        runCatching { module.moduleApplicationInfo.nativeLibraryDir }.getOrNull()

    override fun getModuleSourceDir(): String? =
        runCatching { module.moduleApplicationInfo.sourceDir }.getOrNull()

    private companion object {
        private const val TAG = "XiaobuHaBridge"
    }
}

/**
 * Xiaobu Ha bridge 独立模块公开入口。
 */
object XiaobuHaBridge {
    const val TARGET_SPEECH_PACKAGE = "com.heytap.speechassist"

    @Volatile
    var hostPackageName: String = BuildConfig.APPLICATION_ID
        private set

    @Volatile
    var launchActivityClassName: String? = null
        private set

    @Volatile
    var prefsBackend: BridgePrefsBackend = StandaloneBridgePrefsBackend()
        private set

    @Volatile
    var hookRuntime: BridgeHookRuntime? = null
        private set

    fun configureApp(
        context: Context,
        prefsBackend: BridgePrefsBackend? = null,
        launchActivityClassName: String? = null
    ) {
        hostPackageName = context.packageName
        if (prefsBackend != null) {
            this.prefsBackend = prefsBackend
        } else {
            (this.prefsBackend as? StandaloneBridgePrefsBackend)?.ensureAppServiceListener()
        }
        if (launchActivityClassName != null) {
            this.launchActivityClassName = launchActivityClassName
        }
    }

    fun installHook(
        runtime: BridgeHookRuntime,
        prefsBackend: BridgePrefsBackend,
        hostPackageName: String,
        classLoader: ClassLoader,
        apkPath: String
    ) {
        this.hookRuntime = runtime
        this.prefsBackend = prefsBackend
        this.hostPackageName = hostPackageName
        BridgeHookEntry.install(classLoader, apkPath)
    }

    fun installHook(
        module: XposedModule,
        classLoader: ClassLoader,
        apkPath: String
    ) {
        val backend = (prefsBackend as? StandaloneBridgePrefsBackend)
            ?: StandaloneBridgePrefsBackend().also { prefsBackend = it }
        backend.initHost(module)
        installHook(
            runtime = LibXposedBridgeHookRuntime(module),
            prefsBackend = backend,
            hostPackageName = BuildConfig.APPLICATION_ID,
            classLoader = classLoader,
            apkPath = apkPath
        )
    }
}

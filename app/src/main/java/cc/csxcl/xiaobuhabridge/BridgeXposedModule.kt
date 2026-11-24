package cc.csxcl.xiaobuhabridge

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * 独立 Xposed 模块入口（LibXposed API 102）。
 */
class BridgeXposedModule : XposedModule() {

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        (XiaobuHaBridge.prefsBackend as? StandaloneBridgePrefsBackend)?.initHost(this)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName == XiaobuHaBridge.TARGET_SPEECH_PACKAGE) {
            log(Log.INFO, TAG, "Initializing Xiaobu Ha bridge for ${param.packageName}")
            XiaobuHaBridge.installHook(
                module = this,
                classLoader = param.classLoader,
                apkPath = param.applicationInfo.sourceDir
            )
        }
    }

    private companion object {
        private const val TAG = "XiaobuHaBridge"
    }
}

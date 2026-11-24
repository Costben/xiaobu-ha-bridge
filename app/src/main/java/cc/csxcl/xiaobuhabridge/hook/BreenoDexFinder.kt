package cc.csxcl.xiaobuhabridge.hook

import cc.csxcl.xiaobuhabridge.XiaobuHaBridge
import org.luckypray.dexkit.DexKitBridge
import java.io.File
import java.lang.reflect.Method

data class ResolvedBreenoTargets(
    val directiveFilterMethod: Method?,
    val sendTextMethod: Method?,
    val dismissWindowMethod: Method?
)

/**
 * 优先直连已验证的 ColorOS 17 v13.2.0 精确类/方法，
 * 若小布升级导致混淆类/方法变名，则通过 DexKit 按日志字符串锚点自动找回。
 */
object BreenoDexFinder {
    private const val TAG = "BreenoHaBridge"

    private fun log(msg: String) {
        XiaobuHaBridge.hookRuntime?.log("$TAG: $msg")
    }

    fun resolve(classLoader: ClassLoader, apkPath: String): ResolvedBreenoTargets {
        var directiveFilterMethod = findDirectDirectiveFilter(classLoader)
        var sendTextMethod = findDirectSendText(classLoader)
        var dismissWindowMethod = findDirectDismissWindow(classLoader)

        if (directiveFilterMethod != null && sendTextMethod != null && dismissWindowMethod != null) {
            log("all 3 targets resolved directly without DexKit scan")
            return ResolvedBreenoTargets(directiveFilterMethod, sendTextMethod, dismissWindowMethod)
        }

        // DexKit 兜底动态搜索（跨版本抗混淆）
        try {
            loadDexKitLibrary()
            DexKitBridge.create(apkPath).use { bridge ->
                if (directiveFilterMethod == null) {
                    val m = bridge.findMethod {
                        matcher {
                            usingStrings("onDirectiveFilter , intercept ? ")
                            paramCount = 3
                            returnType = "boolean"
                        }
                    }.firstOrNull()
                    if (m != null) {
                        directiveFilterMethod = m.getMethodInstance(classLoader)
                        log("DexKit resolved directiveFilter -> ${m.className}.${m.methodName}")
                    }
                }
                if (sendTextMethod == null) {
                    val m = bridge.findMethod {
                        matcher {
                            usingStrings("sendText in MyAI roomid ")
                            paramCount = 3
                        }
                    }.firstOrNull()
                    if (m != null) {
                        sendTextMethod = m.getMethodInstance(classLoader)
                        log("DexKit resolved sendText -> ${m.className}.${m.methodName}")
                    }
                }
                if (dismissWindowMethod == null) {
                    val c = bridge.findClass {
                        matcher {
                            usingStrings("ConversationExtraTool", "finishMain quitType : ")
                        }
                    }.firstOrNull()
                    if (c != null) {
                        val cls = c.getInstance(classLoader)
                        dismissWindowMethod = cls.declaredMethods.firstOrNull { method ->
                            java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                                method.parameterTypes.size == 2 &&
                                method.parameterTypes[0].name == "android.content.Context" &&
                                method.parameterTypes[1] == Int::class.javaPrimitiveType
                        }
                        if (dismissWindowMethod != null) {
                            log("DexKit resolved dismissWindow -> ${cls.name}.${dismissWindowMethod!!.name}")
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            log("DexKit fallback error: ${t.message}")
        }

        return ResolvedBreenoTargets(directiveFilterMethod, sendTextMethod, dismissWindowMethod)
    }

    private fun loadDexKitLibrary() {
        try {
            System.loadLibrary("dexkit")
            return
        } catch (_: UnsatisfiedLinkError) {
        }
        val runtime = XiaobuHaBridge.hookRuntime
        val nativeDir = runtime?.getModuleNativeLibraryDir()
        if (!nativeDir.isNullOrBlank()) {
            val soFile = File(nativeDir, "libdexkit.so")
            if (soFile.exists()) {
                System.load(soFile.absolutePath)
                return
            }
        }
        val sourceDir = runtime?.getModuleSourceDir()
        if (!sourceDir.isNullOrBlank()) {
            for (abi in android.os.Build.SUPPORTED_ABIS) {
                try {
                    System.load("$sourceDir!/lib/$abi/libdexkit.so")
                    return
                } catch (_: UnsatisfiedLinkError) {
                }
            }
        }
        System.loadLibrary("dexkit")
    }

    private fun findDirectDirectiveFilter(cl: ClassLoader): Method? {
        return try {
            val cls = ReflectUtils.findClass("com.heytap.speech.engine.HeytapSpeechEngine", cl)
            cls.getDeclaredMethod(
                "onDirectiveFilter\$speechEngine_release",
                String::class.java,
                String::class.java,
                String::class.java
            ).apply { isAccessible = true }
        } catch (_: Throwable) {
            null
        }
    }

    private fun findDirectSendText(cl: ClassLoader): Method? {
        return try {
            val cls = ReflectUtils.findClass("com.heytap.speechassist.core.engine.a0", cl)
            cls.declaredMethods.firstOrNull { m ->
                m.name == "p" &&
                    m.parameterTypes.size == 3 &&
                    m.parameterTypes[0] == String::class.java &&
                    m.parameterTypes[1].name == "android.os.Bundle"
            }?.apply { isAccessible = true }
        } catch (_: Throwable) {
            null
        }
    }

    private fun findDirectDismissWindow(cl: ClassLoader): Method? {
        return try {
            val cls = ReflectUtils.findClass("com.heytap.speechassist.core.d", cl)
            cls.getDeclaredMethod("c", android.content.Context::class.java, Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
        } catch (_: Throwable) {
            null
        }
    }
}

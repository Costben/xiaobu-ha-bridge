package cc.csxcl.xiaobuhabridge.hook

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 轻量反射工具，供钩子侧读取/调用小布宿主类，避免依赖外部 Xposed 兼容层。
 */
internal object ReflectUtils {

    fun findClass(className: String, classLoader: ClassLoader): Class<*> =
        Class.forName(className, false, classLoader)

    fun getStaticObjectField(clazz: Class<*>, fieldName: String): Any? {
        val field = findField(clazz, fieldName)
        return field.get(null)
    }

    fun callMethod(obj: Any, methodName: String, vararg args: Any?): Any? {
        val method = findBestMethod(obj.javaClass, methodName, args)
        return method.invoke(obj, *args)
    }

    fun callStaticMethod(clazz: Class<*>, methodName: String, vararg args: Any?): Any? {
        val method = findBestMethod(clazz, methodName, args)
        return method.invoke(null, *args)
    }

    private fun findField(clazz: Class<*>, fieldName: String): Field {
        var current: Class<*>? = clazz
        while (current != null) {
            runCatching {
                val field = current.getDeclaredField(fieldName)
                field.isAccessible = true
                return field
            }
            current = current.superclass
        }
        throw NoSuchFieldException("${clazz.name}#$fieldName")
    }

    private fun findBestMethod(clazz: Class<*>, methodName: String, args: Array<out Any?>): Method {
        var current: Class<*>? = clazz
        while (current != null) {
            val candidates = current.declaredMethods.filter { m ->
                m.name == methodName && m.parameterTypes.size == args.size && isCompatible(m.parameterTypes, args)
            }
            if (candidates.isNotEmpty()) {
                val exact = candidates.firstOrNull { m ->
                    m.parameterTypes.indices.all { idx ->
                        val a = args[idx]
                        a == null || m.parameterTypes[idx] == a.javaClass
                    }
                } ?: candidates.first()
                exact.isAccessible = true
                return exact
            }
            current = current.superclass
        }
        throw NoSuchMethodException("${clazz.name}#$methodName(${args.size} args)")
    }

    private fun isCompatible(paramTypes: Array<Class<*>>, args: Array<out Any?>): Boolean {
        for (i in paramTypes.indices) {
            val param = paramTypes[i]
            val arg = args[i]
            if (arg == null) {
                if (param.isPrimitive) return false
                continue
            }
            val boxed = primitiveToWrapper(param)
            if (!boxed.isAssignableFrom(arg.javaClass)) {
                return false
            }
        }
        return true
    }

    private fun primitiveToWrapper(cls: Class<*>): Class<*> = when (cls) {
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        java.lang.Character.TYPE -> java.lang.Character::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        else -> cls
    }
}

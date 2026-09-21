package com.d4viddf.hyperbridge.xposed.hooks

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object IslandHookReflection {
    fun invokeNoArg(target: Any?, methodName: String): Any? {
        if (target == null) return null
        return runCatching {
            val method = findNoArgMethod(target.javaClass, methodName) ?: return null
            method.isAccessible = true
            method.invoke(target)
        }.getOrNull()
    }

    fun invokeIntSetter(target: Any, methodName: String, value: Int): Boolean {
        var current: Class<*>? = target.javaClass
        while (current != null) {
            val method = current.declaredMethods.firstOrNull {
                it.name == methodName &&
                    it.parameterCount == 1 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType
            }
            if (method != null) {
                return runCatching {
                    method.isAccessible = true
                    method.invoke(target, value)
                    true
                }.getOrDefault(false)
            }
            current = current.superclass
        }
        return false
    }

    fun invokeFloatSetter(target: Any, methodName: String, value: Float): Boolean {
        var current: Class<*>? = target.javaClass
        while (current != null) {
            val method = current.declaredMethods.firstOrNull {
                it.name == methodName &&
                    it.parameterCount == 1 &&
                    it.parameterTypes[0] == Float::class.javaPrimitiveType
            }
            if (method != null) {
                return runCatching {
                    method.isAccessible = true
                    method.invoke(target, value)
                    true
                }.getOrDefault(false)
            }
            current = current.superclass
        }
        return false
    }

    fun findNoArgMethod(clazz: Class<*>, name: String): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods.firstOrNull { it.name == name && it.parameterCount == 0 }?.let { return it }
            current = current.superclass
        }
        return null
    }

    fun allMethods(clazz: Class<*>): List<Method> {
        val methods = mutableListOf<Method>()
        var current: Class<*>? = clazz
        while (current != null) {
            methods += current.declaredMethods
            current = current.superclass
        }
        return methods
    }

    fun readField(instance: Any, fieldName: String): Any? {
        var current: Class<*>? = instance.javaClass
        while (current != null) {
            val field = runCatching { current.getDeclaredField(fieldName) }.getOrNull()
            if (field != null) {
                field.isAccessible = true
                return runCatching { field.get(instance) }.getOrNull()
            }
            current = current.superclass
        }
        return null
    }

    fun findLightColorField(clazz: Class<*>, preferStatic: Boolean): Field? {
        runCatching {
            val exact = clazz.getDeclaredField("U_LIGHT_COLORS")
            exact.isAccessible = true
            return exact
        }
        val fields = clazz.declaredFields.filter {
            it.type == FloatArray::class.java && it.name.contains("LIGHT", ignoreCase = true)
        }
        val preferred = fields.firstOrNull { Modifier.isStatic(it.modifiers) == preferStatic }
        return (preferred ?: fields.firstOrNull())?.apply { isAccessible = true }
    }
}

package com.sykeptical.hyperpop.xposed.hooks

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Method and field lookup cached by class, name, and argument count.
 * A miss is cached too, so an animation frame does not scan again.
 */
internal class ReflectionLookup(
    private val findMethod: (Class<*>, String, Int) -> Method? = ::scanMethod,
    private val findField: (Class<*>, String) -> Field? = ::scanField,
) {
    private val methods = ConcurrentHashMap<MethodKey, MethodHolder>()
    private val fields = ConcurrentHashMap<FieldKey, FieldHolder>()

    var methodScans: Int = 0
        private set
    var fieldScans: Int = 0
        private set

    fun method(clazz: Class<*>, name: String, arity: Int): Method? {
        val key = MethodKey(clazz, name, arity)
        val cached = methods[key]
        if (cached != null) return cached.method
        methodScans += 1
        val found = findMethod(clazz, name, arity)?.also { it.isAccessible = true }
        methods.putIfAbsent(key, MethodHolder(found))
        return methods[key]?.method
    }

    fun field(clazz: Class<*>, name: String): Field? {
        val key = FieldKey(clazz, name)
        val cached = fields[key]
        if (cached != null) return cached.field
        fieldScans += 1
        val found = findField(clazz, name)?.also { it.isAccessible = true }
        fields.putIfAbsent(key, FieldHolder(found))
        return fields[key]?.field
    }

    private data class MethodKey(val clazz: Class<*>, val name: String, val arity: Int)
    private data class FieldKey(val clazz: Class<*>, val name: String)
    private class MethodHolder(val method: Method?)
    private class FieldHolder(val field: Field?)
}

private fun scanMethod(clazz: Class<*>, name: String, arity: Int): Method? {
    var type: Class<*>? = clazz
    while (type != null) {
        val found = type.declaredMethods.firstOrNull { it.name == name && it.parameterTypes.size == arity }
        if (found != null) return found
        type = type.superclass
    }
    return null
}

private fun scanField(clazz: Class<*>, name: String): Field? {
    var type: Class<*>? = clazz
    while (type != null) {
        val found = type.declaredFields.firstOrNull { it.name == name }
        if (found != null) return found
        type = type.superclass
    }
    return null
}

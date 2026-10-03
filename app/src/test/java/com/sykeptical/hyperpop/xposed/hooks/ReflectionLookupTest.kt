package com.sykeptical.hyperpop.xposed.hooks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReflectionLookupTest {
    @Test
    fun aSecondLookupDoesNotScanAgain() {
        var scans = 0
        val lookup = ReflectionLookup(
            findMethod = { clazz, name, arity ->
                scans += 1
                clazz.declaredMethods.firstOrNull { it.name == name && it.parameterTypes.size == arity }
            },
        )
        assertNotNull(lookup.method(Host::class.java, "ping", 0))
        assertNotNull(lookup.method(Host::class.java, "ping", 0))
        assertNull(lookup.method(Host::class.java, "missing", 0))
        assertNull(lookup.method(Host::class.java, "missing", 0))
        assertEquals(2, scans)
        assertEquals(2, lookup.methodScans)
    }

    class Host {
        fun ping(): String = "ok"
    }
}

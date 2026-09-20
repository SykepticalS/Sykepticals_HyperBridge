package com.d4viddf.hyperbridge.xposed.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ModuleServiceSnapshot(
    val available: Boolean = false,
    val apiVersion: Int = 0,
    val frameworkName: String = "",
    val frameworkVersion: String = "",
    val scopes: Set<String> = emptySet(),
)

object ModuleServiceState {
    private val mutable = MutableStateFlow(ModuleServiceSnapshot())
    val state = mutable.asStateFlow()
    fun update(value: ModuleServiceSnapshot) { mutable.value = value }
    fun clear() { mutable.value = ModuleServiceSnapshot() }
}

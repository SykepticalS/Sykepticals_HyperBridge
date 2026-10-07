package com.sykeptical.hyperpop.service.animation.fingerprint

/**
 * Maps framework acquired codes and the vendor codes observed on this ROM.
 * Vendor codes arrive either raw (22, 23, 44, 201) or as `1000 + code`.
 * Help codes are not failures: Xiaomi does not count them as attempts.
 */
enum class AcquiredKind {
    Down,
    Up,
    Good,
    Help,
    VendorDown,
    VendorUp,
    VendorFail,
    VendorPostFail,
    VendorOther,
    Ignore,
}

object AcquiredCodeClassifier {
    const val VENDOR_BASE = 1000
    const val ERROR_LOCKOUT = 7
    const val ERROR_LOCKOUT_PERMANENT = 9

    fun classify(code: Int): AcquiredKind {
        when (code) {
            100 -> return AcquiredKind.Down
            101 -> return AcquiredKind.Up
            0 -> return AcquiredKind.Good
        }
        if (code in 1..5) return AcquiredKind.Help
        val vendor = if (code >= VENDOR_BASE) code - VENDOR_BASE else code
        return when (vendor) {
            22 -> AcquiredKind.VendorDown
            23 -> AcquiredKind.VendorUp
            44 -> AcquiredKind.VendorFail
            201 -> AcquiredKind.VendorPostFail
            24 -> AcquiredKind.VendorOther
            else -> if (code >= VENDOR_BASE) AcquiredKind.VendorOther else AcquiredKind.Ignore
        }
    }

    fun isFingerDown(kind: AcquiredKind): Boolean =
        kind == AcquiredKind.Down || kind == AcquiredKind.VendorDown

    fun isFingerUp(kind: AcquiredKind): Boolean =
        kind == AcquiredKind.Up || kind == AcquiredKind.VendorUp

    fun isLockoutError(code: Int): Boolean =
        code == ERROR_LOCKOUT || code == ERROR_LOCKOUT_PERMANENT
}

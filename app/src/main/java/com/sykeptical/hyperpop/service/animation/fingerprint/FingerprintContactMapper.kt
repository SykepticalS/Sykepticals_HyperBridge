package com.sykeptical.hyperpop.service.animation.fingerprint

/**
 * Maps a sensor-rect touch into the 600-unit artwork canvas and keeps the
 * last [CAPACITY] centroids. Size and orientation are not measured on this
 * device, so [NOMINAL_RADIUS] is a fixed contact size in artwork units.
 */
class FingerprintContactMapper(
    private val sensorLeft: Float,
    private val sensorTop: Float,
    private val sensorRight: Float,
    private val sensorBottom: Float,
    private val canvas: Float = CANVAS,
    val nominalRadius: Float = NOMINAL_RADIUS,
) {
    private val xs = FloatArray(CAPACITY)
    private val ys = FloatArray(CAPACITY)
    var count: Int = 0
        private set

    fun push(rawX: Float, rawY: Float): Boolean {
        val width = sensorRight - sensorLeft
        val height = sensorBottom - sensorTop
        if (width <= 0f || height <= 0f) return false
        if (rawX < sensorLeft || rawX > sensorRight || rawY < sensorTop || rawY > sensorBottom) return false
        val x = ((rawX - sensorLeft) / width) * canvas
        val y = ((rawY - sensorTop) / height) * canvas
        if (count > 0) {
            val dx = x - xs[count - 1]
            val dy = y - ys[count - 1]
            if (dx * dx + dy * dy < MERGE_DISTANCE_SQ) {
                xs[count - 1] = x
                ys[count - 1] = y
                return true
            }
        }
        if (count == CAPACITY) {
            xs.copyInto(xs, destinationOffset = 0, startIndex = 1, endIndex = CAPACITY)
            ys.copyInto(ys, destinationOffset = 0, startIndex = 1, endIndex = CAPACITY)
            count = CAPACITY - 1
        }
        xs[count] = x
        ys[count] = y
        count += 1
        return true
    }

    fun clear() {
        count = 0
    }

    fun copyInto(destX: FloatArray, destY: FloatArray): Int {
        val n = count.coerceAtMost(minOf(destX.size, destY.size))
        xs.copyInto(destX, endIndex = n)
        ys.copyInto(destY, endIndex = n)
        return n
    }

    fun xAt(index: Int): Float = xs[index]
    fun yAt(index: Int): Float = ys[index]

    companion object {
        const val CAPACITY = 16
        const val CANVAS = 600f
        const val NOMINAL_RADIUS = 96f
        private const val MERGE_DISTANCE_SQ = 16f
    }
}

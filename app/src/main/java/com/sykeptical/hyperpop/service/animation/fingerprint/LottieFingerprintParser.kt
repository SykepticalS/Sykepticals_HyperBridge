package com.sykeptical.hyperpop.service.animation.fingerprint

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Pulls the static white ridge strokes, the blue enrollment trim, and the Union
 * checkmark out of Xiaomi's enrollment Lottie. Paths are baked into canvas space.
 * A schema miss returns null so the caller can disable the feature.
 *
 * The blue fill is the `路径` layers. Enrollment plays those trims from frame 0
 * through [FILL_END_FRAME] at [FILL_FRAME_RATE].
 */
object LottieFingerprintParser {
    const val FILL_FRAME_RATE = 60f
    const val FILL_END_FRAME = 147f

    private val json = Json { ignoreUnknownKeys = true }

    fun advanceFill(frame: Float, dtSeconds: Float): Float =
        (frame + dtSeconds * FILL_FRAME_RATE).coerceIn(0f, FILL_END_FRAME)

    /** Visible trim length at [frame], in percent of the path. 0 is empty and 100 is the whole stroke. */
    fun trimLength(ridge: FillRidge, frame: Float): Float {
        val start = ridge.start.at(frame)
        val end = ridge.end.at(frame)
        val span = if (end >= start) end - start else 100f - start + end
        return span.coerceIn(0f, 100f)
    }

    data class Contour(
        val x: FloatArray,
        val y: FloatArray,
        val inX: FloatArray,
        val inY: FloatArray,
        val outX: FloatArray,
        val outY: FloatArray,
        val closed: Boolean,
    )

    class TrimCurve(
        val frames: FloatArray,
        val values: FloatArray,
        val outX: FloatArray,
        val outY: FloatArray,
        val inX: FloatArray,
        val inY: FloatArray,
    ) {
        fun at(frame: Float): Float {
            if (frames.isEmpty()) return 0f
            if (frames.size == 1 || frame <= frames[0]) return values[0]
            val last = frames.size - 1
            if (frame >= frames[last]) return values[last]
            var index = 0
            while (index < last - 1 && frames[index + 1] <= frame) index++
            val span = frames[index + 1] - frames[index]
            if (span <= 0.0001f) return values[index + 1]
            val x = ((frame - frames[index]) / span).coerceIn(0f, 1f)
            val eased = ease(x, outX[index], outY[index], inX[index + 1], inY[index + 1])
            return values[index] + (values[index + 1] - values[index]) * eased
        }

        companion object {
            fun constant(value: Float) = TrimCurve(
                floatArrayOf(0f),
                floatArrayOf(value),
                floatArrayOf(Float.NaN),
                floatArrayOf(Float.NaN),
                floatArrayOf(Float.NaN),
                floatArrayOf(Float.NaN),
            )
        }
    }

    class FillRidge(
        val contour: Contour,
        val strokeWidth: Float,
        val start: TrimCurve,
        val end: TrimCurve,
        val offset: TrimCurve,
        val red: Float,
        val green: Float,
        val blue: Float,
    )

    data class Artwork(
        val canvasWidth: Float,
        val canvasHeight: Float,
        val ridgeStrokeWidth: Float,
        val ridges: List<Contour>,
        val fill: List<FillRidge>,
        val checkmark: Contour,
        val blueRed: Float,
        val blueGreen: Float,
        val blueBlue: Float,
    )

    fun parse(text: String): Artwork? = runCatching { parseOrThrow(text) }.getOrNull()

    private fun parseOrThrow(text: String): Artwork? {
        val root = json.parseToJsonElement(text) as? JsonObject ?: return null
        val canvasW = root.number("w") ?: return null
        val canvasH = root.number("h") ?: return null
        if (canvasW <= 0f || canvasH <= 0f) return null
        val layers = root.array("layers") ?: return null
        val assets = root.array("assets")
        val fill = findFill(layers) ?: return null
        val check = findCheckmark(layers) ?: return null
        val ridges = findRidges(layers, assets) ?: return null
        if (ridges.isEmpty() || check.x.size < 3) return null
        val stroke = ridges.first().second
        if (stroke <= 0f) return null
        val blue = fill.first()
        return Artwork(
            canvasWidth = canvasW,
            canvasHeight = canvasH,
            ridgeStrokeWidth = stroke,
            ridges = ridges.map { it.first },
            fill = fill,
            checkmark = check,
            blueRed = blue.red,
            blueGreen = blue.green,
            blueBlue = blue.blue,
        )
    }

    private fun findFill(layers: JsonArray): List<FillRidge>? {
        val out = ArrayList<FillRidge>()
        for (layerEl in layers) {
            val layer = layerEl as? JsonObject ?: continue
            if (layer.number("ty") != 4f) continue
            val name = layer.string("nm") ?: continue
            if (!name.startsWith("路径")) continue
            if (layer["parent"] != null) return null
            val xform = transformOf(layer) ?: return null
            val shapes = layer.array("shapes") ?: return null
            out += fillRidge(shapes, xform) ?: return null
        }
        return out.takeIf { it.isNotEmpty() }
    }

    private fun fillRidge(shapes: JsonArray, xform: Xform): FillRidge? {
        var contour: Contour? = null
        var trim: JsonObject? = null
        var stroke: JsonObject? = null
        for (shapeEl in shapes) {
            val shape = shapeEl as? JsonObject ?: return null
            when (shape.string("ty")) {
                "sh" -> {
                    if (contour != null) return null
                    contour = contourOf(shape, xform) ?: return null
                }
                "tm" -> {
                    if (trim != null) return null
                    trim = shape
                }
                "st" -> stroke = shape
            }
        }
        val path = contour ?: return null
        val trimShape = trim ?: return null
        val strokeShape = stroke ?: return null
        val color = colorOf(strokeShape.obj("c")) ?: return null
        if (isWhite(color)) return null
        val width = strokeShape.obj("w")?.number("k") ?: return null
        if (width <= 0f) return null
        val start = trimCurve(trimShape.obj("s")) ?: return null
        val end = trimCurve(trimShape.obj("e")) ?: return null
        val offset = trimCurve(trimShape.obj("o")) ?: TrimCurve.constant(0f)
        return FillRidge(
            contour = path,
            strokeWidth = width * kotlin.math.abs(xform.sx).coerceAtLeast(0.01f),
            start = start,
            end = end,
            offset = offset,
            red = color[0],
            green = color[1],
            blue = color[2],
        )
    }

    private fun trimCurve(prop: JsonObject?): TrimCurve? {
        if (prop == null) return null
        val animated = prop.number("a") ?: 0f
        if (animated == 0f) {
            val value = scalar(prop["k"]) ?: return null
            return TrimCurve.constant(value)
        }
        val keys = prop.array("k") ?: return null
        if (keys.isEmpty()) return null
        val count = keys.size
        val frames = FloatArray(count)
        val values = FloatArray(count)
        val outX = FloatArray(count)
        val outY = FloatArray(count)
        val inX = FloatArray(count)
        val inY = FloatArray(count)
        for (index in 0 until count) {
            val key = keys[index] as? JsonObject ?: return null
            val time = key.number("t") ?: return null
            if (index > 0 && time < frames[index - 1]) return null
            frames[index] = time
            values[index] = scalar(key["s"]) ?: return null
            val incoming = tangent(key, "i")
            val outgoing = tangent(key, "o")
            inX[index] = incoming.first
            inY[index] = incoming.second
            outX[index] = outgoing.first
            outY[index] = outgoing.second
        }
        return TrimCurve(frames, values, outX, outY, inX, inY)
    }

    private fun tangent(key: JsonObject, name: String): Pair<Float, Float> {
        val point = key.obj(name) ?: return Float.NaN to Float.NaN
        val x = scalar(point["x"]) ?: return Float.NaN to Float.NaN
        val y = scalar(point["y"]) ?: return Float.NaN to Float.NaN
        return x to y
    }

    private fun scalar(element: JsonElement?): Float? = when (element) {
        is JsonPrimitive -> element.floatOrNull()
        is JsonArray -> (element.firstOrNull() as? JsonPrimitive)?.floatOrNull()
        else -> null
    }

    private fun ease(x: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        if (x1.isNaN() || y1.isNaN() || x2.isNaN() || y2.isNaN()) return x
        var lo = 0f
        var hi = 1f
        var t = x
        repeat(12) {
            val estimate = cubic(t, x1, x2)
            if (estimate < x) lo = t else hi = t
            t = (lo + hi) * 0.5f
        }
        return cubic(t, y1, y2)
    }

    private fun cubic(t: Float, c1: Float, c2: Float): Float {
        val u = 1f - t
        return 3f * u * u * t * c1 + 3f * u * t * t * c2 + t * t * t
    }

    private fun findRidges(layers: JsonArray, assets: JsonArray?): List<Pair<Contour, Float>>? {
        val out = ArrayList<Pair<Contour, Float>>()
        for (layerEl in layers) {
            val layer = layerEl as? JsonObject ?: continue
            if (layer.number("ty") != 0f) continue
            val ref = layer.string("refId") ?: continue
            val asset = assets?.firstOrNull { (it as? JsonObject)?.string("id") == ref } as? JsonObject ?: continue
            val parent = transformOf(layer) ?: return null
            val children = asset.array("layers") ?: continue
            for (childEl in children) {
                val child = childEl as? JsonObject ?: continue
                val local = transformOf(child) ?: return null
                val baked = compose(parent, local)
                val shapes = child.array("shapes") ?: continue
                if (!collect(shapes, baked, out, wantStroke = true)) return null
            }
        }
        return out
    }

    private fun findCheckmark(layers: JsonArray): Contour? {
        for (layerEl in layers) {
            val layer = layerEl as? JsonObject ?: continue
            if (layer.string("nm") != "Union") continue
            val xform = transformOf(layer) ?: return null
            val shapes = layer.array("shapes") ?: return null
            val found = ArrayList<Pair<Contour, Float>>()
            if (!collect(shapes, xform, found, wantStroke = false)) return null
            return found.firstOrNull()?.first
        }
        return null
    }

    private fun collect(
        shapes: JsonArray,
        xform: Xform,
        out: MutableList<Pair<Contour, Float>>,
        wantStroke: Boolean,
    ): Boolean {
        for (shapeEl in shapes) {
            val shape = shapeEl as? JsonObject ?: continue
            when (shape.string("ty")) {
                "gr" -> {
                    val inner = shape.array("it") ?: continue
                    val group = transformOf(shape.obj("tr") ?: inner.transformItem()) ?: return false
                    if (!collect(inner, compose(xform, group), out, wantStroke)) return false
                }
                "sh" -> {
                    val contour = contourOf(shape, xform) ?: return false
                    val stroke = if (wantStroke) {
                        val width = strokeWidth(shapes) ?: return false
                        width * kotlin.math.abs(xform.sx).coerceAtLeast(0.01f)
                    } else {
                        0f
                    }
                    out += contour to stroke
                }
            }
        }
        return true
    }

    private fun contourOf(shape: JsonObject, xform: Xform): Contour? {
        val key = shape.obj("ks")?.get("k") as? JsonObject ?: return null
        val v = points(key.array("v")) ?: return null
        val i = points(key.array("i")) ?: return null
        val o = points(key.array("o")) ?: return null
        if (v.size < 2 || v.size != i.size || v.size != o.size) return null
        val n = v.size
        val x = FloatArray(n)
        val y = FloatArray(n)
        val inX = FloatArray(n)
        val inY = FloatArray(n)
        val outX = FloatArray(n)
        val outY = FloatArray(n)
        for (index in 0 until n) {
            val point = xform.apply(v[index].first, v[index].second)
            val incoming = xform.applyDelta(i[index].first, i[index].second)
            val outgoing = xform.applyDelta(o[index].first, o[index].second)
            x[index] = point.first
            y[index] = point.second
            inX[index] = incoming.first
            inY[index] = incoming.second
            outX[index] = outgoing.first
            outY[index] = outgoing.second
        }
        val closed = key.bool("c") == true
        return Contour(x, y, inX, inY, outX, outY, closed)
    }

    private fun strokeWidth(shapes: JsonArray): Float? {
        for (shapeEl in shapes) {
            val shape = shapeEl as? JsonObject ?: continue
            if (shape.string("ty") != "st") continue
            return shape.obj("w")?.number("k")
        }
        return null
    }

    private fun JsonArray.transformItem(): JsonObject? =
        firstOrNull { (it as? JsonObject)?.string("ty") == "tr" } as? JsonObject

    private data class Xform(
        val px: Float,
        val py: Float,
        val ax: Float,
        val ay: Float,
        val sx: Float,
        val sy: Float,
    ) {
        fun apply(x: Float, y: Float): Pair<Float, Float> =
            ((x - ax) * sx + px) to ((y - ay) * sy + py)

        fun applyDelta(x: Float, y: Float): Pair<Float, Float> = (x * sx) to (y * sy)

        companion object {
            val IDENTITY = Xform(0f, 0f, 0f, 0f, 1f, 1f)
        }
    }

    private fun compose(outer: Xform, inner: Xform): Xform {
        val origin = outer.apply(inner.px, inner.py)
        val anchor = inner.ax to inner.ay
        return Xform(
            px = origin.first,
            py = origin.second,
            ax = anchor.first,
            ay = anchor.second,
            sx = outer.sx * inner.sx,
            sy = outer.sy * inner.sy,
        )
    }

    private fun transformOf(holder: JsonObject?): Xform? {
        if (holder == null) return Xform.IDENTITY
        val ks = holder.obj("ks") ?: holder
        val p = staticPair(ks.obj("p")) ?: return null
        val a = staticPair(ks.obj("a")) ?: 0f to 0f
        val s = staticPair(ks.obj("s")) ?: 100f to 100f
        return Xform(p.first, p.second, a.first, a.second, s.first / 100f, s.second / 100f)
    }

    private fun staticPair(prop: JsonObject?): Pair<Float, Float>? {
        if (prop == null) return null
        val animated = prop.number("a") ?: 0f
        if (animated != 0f) return null
        val k = prop["k"] ?: return null
        return when (k) {
            is JsonArray -> {
                val x = (k.getOrNull(0) as? JsonPrimitive)?.floatOrNull() ?: return null
                val y = (k.getOrNull(1) as? JsonPrimitive)?.floatOrNull() ?: return null
                x to y
            }
            is JsonPrimitive -> {
                val value = k.floatOrNull() ?: return null
                value to value
            }
            else -> null
        }
    }

    private fun points(array: JsonArray?): List<Pair<Float, Float>>? {
        if (array == null) return null
        val out = ArrayList<Pair<Float, Float>>(array.size)
        for (item in array) {
            val pair = item as? JsonArray ?: return null
            val x = (pair.getOrNull(0) as? JsonPrimitive)?.floatOrNull() ?: return null
            val y = (pair.getOrNull(1) as? JsonPrimitive)?.floatOrNull() ?: return null
            out += x to y
        }
        return out
    }

    private fun colorOf(prop: JsonObject?): FloatArray? {
        val k = prop?.get("k") as? JsonArray ?: return null
        if (k.size < 3) return null
        val r = (k[0] as? JsonPrimitive)?.floatOrNull() ?: return null
        val g = (k[1] as? JsonPrimitive)?.floatOrNull() ?: return null
        val b = (k[2] as? JsonPrimitive)?.floatOrNull() ?: return null
        return floatArrayOf(r, g, b)
    }

    private fun isWhite(color: FloatArray): Boolean =
        color[0] > 0.95f && color[1] > 0.95f && color[2] > 0.95f

    private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray
    private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject
    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.number(name: String): Float? =
        (this[name] as? JsonPrimitive)?.floatOrNull()

    private fun JsonObject.bool(name: String): Boolean? {
        val primitive = this[name] as? JsonPrimitive ?: return null
        return when (primitive.contentOrNull) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

    private fun JsonPrimitive.floatOrNull(): Float? = contentOrNull?.toFloatOrNull()
}

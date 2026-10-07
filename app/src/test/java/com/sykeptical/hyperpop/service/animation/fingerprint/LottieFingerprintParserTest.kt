package com.sykeptical.hyperpop.service.animation.fingerprint

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class LottieFingerprintParserTest {
    @Test
    fun syntheticFixtureBakesRidgePositionAndCheckmarkScale() {
        val artwork = LottieFingerprintParser.parse(FIXTURE)
        checkNotNull(artwork)
        assertEquals(600f, artwork.canvasWidth, 0.01f)
        assertEquals(8f, artwork.ridgeStrokeWidth, 0.01f)
        assertEquals(1, artwork.ridges.size)
        assertEquals(10f, artwork.ridges[0].x[0], 0.01f)
        assertEquals(20f, artwork.ridges[0].y[0], 0.01f)
        assertEquals(3, artwork.checkmark.x.size)
        assertEquals(102f, artwork.checkmark.x[0], 0.01f)
        assertEquals(104f, artwork.checkmark.y[0], 0.01f)
        assertEquals(0.278431f, artwork.blueRed, 0.0001f)
        assertTrue(artwork.checkmark.closed)
        assertEquals(1, artwork.fill.size)
        val ridge = artwork.fill[0]
        assertEquals(100f, ridge.start.at(0f), 0.01f)
        assertEquals(0f, LottieFingerprintParser.trimLength(ridge, 0f), 0.01f)
        assertEquals(0f, ridge.start.at(23f), 0.01f)
        assertEquals(100f, LottieFingerprintParser.trimLength(ridge, 23f), 0.01f)
        val mid = ridge.start.at(11.2735f)
        assertTrue(mid in 40f..60f)
        assertEquals(60f, LottieFingerprintParser.advanceFill(0f, 1f), 0.01f)
        assertEquals(LottieFingerprintParser.FILL_END_FRAME, LottieFingerprintParser.advanceFill(140f, 1f), 0.01f)
    }

    @Test
    fun pulledEnrollmentAssetFillsTheEarlyRidge() {
        val apk = File("../.agent-local/apks/Settings.apk")
        assumeTrue(apk.isFile)
        val text = ZipFile(apk).use { zip ->
            val entry = zip.getEntry("res/raw/finger_enroll_dark.json")
            checkNotNull(entry)
            zip.getInputStream(entry).bufferedReader().readText()
        }
        val artwork = checkNotNull(LottieFingerprintParser.parse(text))
        assertEquals(22, artwork.fill.size)
        assertTrue(artwork.fill.any { ridge ->
            LottieFingerprintParser.trimLength(ridge, 0f) < 1f &&
                ridge.start.at(23f) < 1f &&
                LottieFingerprintParser.trimLength(ridge, 23f) > 99f
        })
    }

    @Test
    fun malformedSchemaDisablesTheArtwork() {
        assertNull(LottieFingerprintParser.parse(""))
        assertNull(LottieFingerprintParser.parse("{}"))
        assertNull(LottieFingerprintParser.parse(FIXTURE.replace("[[0, 0], [10, 0]]", "[[0, 0]]")))
        assertNull(LottieFingerprintParser.parse(FIXTURE.replace(""""nm": "Union"""", """"nm": "Other"""")))
        assertNull(LottieFingerprintParser.parse(FIXTURE.replace(""""ty": "tm"""", """"ty": "xx"""")))
        assertNull(LottieFingerprintParser.parse(FIXTURE.replace(""""nm": "路径1"""", """"nm": "blue"""")))
    }

    private companion object {
        val FIXTURE = """
            {
              "w": 600, "h": 600,
              "assets": [{
                "id": "comp_0",
                "layers": [{
                  "ty": 4, "nm": "ridge",
                  "ks": {"p": {"a": 0, "k": [10, 20]}, "a": {"a": 0, "k": [0, 0]}, "s": {"a": 0, "k": [100, 100]}},
                  "shapes": [
                    {"ty": "sh", "ks": {"k": {"v": [[0, 0], [10, 0]], "i": [[0, 0], [0, 0]], "o": [[0, 0], [0, 0]], "c": false}}},
                    {"ty": "st", "c": {"a": 0, "k": [1, 1, 1, 1]}, "w": {"a": 0, "k": 8}}
                  ]
                }]
              }],
              "layers": [
                {"ty": 0, "refId": "comp_0", "ks": {"p": {"a": 0, "k": [0, 0]}, "a": {"a": 0, "k": [0, 0]}, "s": {"a": 0, "k": [100, 100]}}},
                {"ty": 4, "nm": "Union", "ks": {"p": {"a": 0, "k": [100, 100]}, "a": {"a": 0, "k": [0, 0]}, "s": {"a": 0, "k": [100, 100]}},
                  "shapes": [{"ty": "gr", "it": [
                    {"ty": "sh", "ks": {"k": {"v": [[1, 2], [2, 2], [1, 3]], "i": [[0, 0], [0, 0], [0, 0]], "o": [[0, 0], [0, 0], [0, 0]], "c": true}}},
                    {"ty": "fl", "c": {"a": 0, "k": [1, 1, 1, 1]}},
                    {"ty": "tr", "p": {"a": 0, "k": [0, 0]}, "a": {"a": 0, "k": [0, 0]}, "s": {"a": 0, "k": [200, 200]}}
                  ]}]
                },
                {"ty": 4, "nm": "路径1",
                  "ks": {"p": {"a": 0, "k": [0, 0]}, "a": {"a": 0, "k": [0, 0]}, "s": {"a": 0, "k": [100, 100]}},
                  "shapes": [
                    {"ty": "sh", "ks": {"k": {"v": [[0, 0], [100, 0]], "i": [[0, 0], [0, 0]], "o": [[0, 0], [0, 0]], "c": false}}},
                    {"ty": "tm",
                      "s": {"a": 1, "k": [
                        {"i": {"x": [0.833], "y": [0.833]}, "o": {"x": [0.167], "y": [0.167]}, "t": 0, "s": [100]},
                        {"i": {"x": [0.833], "y": [0.833]}, "o": {"x": [0.167], "y": [0.167]}, "t": 22.547, "s": [0]},
                        {"t": 49.6, "s": [0]}
                      ]},
                      "e": {"a": 0, "k": 100},
                      "o": {"a": 0, "k": 0}
                    },
                    {"ty": "st", "c": {"a": 0, "k": [0.278431, 0.533333, 1, 1]}, "w": {"a": 0, "k": 8}}
                  ]
                }
              ]
            }
        """.trimIndent()
    }
}

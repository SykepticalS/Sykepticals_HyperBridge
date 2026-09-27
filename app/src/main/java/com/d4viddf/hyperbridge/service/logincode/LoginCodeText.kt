package com.d4viddf.hyperbridge.service.logincode

import java.text.Normalizer

/** Length-stable text primitives shared by the lexicon and the extractor. */
internal object LoginCodeText {
    private const val INVISIBLE =
        "\u00AD\u034F\u061C\u115F\u1160\u17B4\u17B5\u180E\u200B\u200C\u200D\u200E\u200F" +
            "\u202A\u202B\u202C\u202D\u202E\u2060\u2061\u2062\u2063\u2064\u2066\u2067\u2068\u2069\uFEFF"
    private const val DASHES = "\u2010\u2011\u2012\u2013\u2014\u2015\u2212\u2E3A\u2E3B\uFE58\uFE63\uFF0D"

    /**
     * NFKC folds full-width, circled and mathematical forms. Every Unicode decimal digit
     * (Arabic-Indic, Devanagari, Thai, Myanmar, ...) then becomes ASCII so one scanner serves
     * all scripts. Invisible and bidi controls are dropped because SMS gateways insert them
     * between digits.
     */
    fun normalize(raw: CharSequence): String {
        val nfkc = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        val out = StringBuilder(nfkc.length)
        for (ch in nfkc) {
            when {
                ch in '0'..'9' -> out.append(ch)
                INVISIBLE.indexOf(ch) >= 0 -> Unit
                ch == '\n' || ch == '\r' || ch == '\u2028' || ch == '\u2029' || ch == '\u0085' -> out.append('\n')
                ch == '\t' || ch == '\u000B' || ch == '\u000C' || Character.isSpaceChar(ch) -> out.append(' ')
                DASHES.indexOf(ch) >= 0 -> out.append('-')
                Character.isDigit(ch) -> {
                    val digit = Character.digit(ch, 10)
                    out.append(if (digit in 0..9) '0' + digit else ch)
                }
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    /** Char-wise lowering keeps indices aligned with [normalize] output (String.lowercase does not). */
    fun lower(text: String): String {
        val chars = CharArray(text.length)
        for (i in text.indices) chars[i] = Character.toLowerCase(text[i])
        return String(chars)
    }

    fun isAsciiDigit(ch: Char): Boolean = ch in '0'..'9'

    fun isAsciiUpper(ch: Char): Boolean = ch in 'A'..'Z'

    fun isAsciiAlnum(ch: Char): Boolean = ch in '0'..'9' || ch in 'A'..'Z' || ch in 'a'..'z'

    fun isWordChar(ch: Char): Boolean {
        if (Character.isLetterOrDigit(ch)) return true
        return when (Character.getType(ch).toByte()) {
            Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
            else -> false
        }
    }

    /** Scripts written without spaces between words, or with particles glued to nouns (Hangul). */
    fun isNoSpaceScript(ch: Char): Boolean {
        if (ch.code < 0x0E00) return false
        return when (Character.UnicodeScript.of(ch.code)) {
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.THAI,
            Character.UnicodeScript.LAO,
            Character.UnicodeScript.KHMER,
            Character.UnicodeScript.MYANMAR,
            Character.UnicodeScript.TIBETAN,
            Character.UnicodeScript.HANGUL -> true
            else -> false
        }
    }

    /** A neighbour that glues onto a term or a code in a space-delimited script. */
    fun joinsWord(ch: Char): Boolean = isWordChar(ch) && !isNoSpaceScript(ch)
}

internal class TermHit(val start: Int, val end: Int, val weight: Int)

/**
 * Multi-pattern matcher over lowered text, bucketed by first character.
 *
 * Pattern syntax: a leading `*` lets the term end a longer word (German/Nordic compounds such
 * as `Bestätigungscode`), a trailing `*` lets it start one (`verif*`). Terms in scripts without
 * word spacing never require boundaries.
 */
internal class TermIndex(entries: List<Pair<String, Int>>) {
    private class Term(val text: String, val weight: Int, val needLeft: Boolean, val needRight: Boolean)

    private val byFirst = HashMap<Char, MutableList<Term>>()

    /** Whole words and stems, used to decide whether a gap between a term and a code is filler. */
    val words = HashSet<String>()
    val prefixStems = ArrayList<String>()
    val suffixStems = ArrayList<String>()

    init {
        for ((raw, weight) in entries) {
            val openLeft = raw.startsWith('*')
            val openRight = raw.endsWith('*')
            val text = LoginCodeText.lower(LoginCodeText.normalize(raw.trim('*')))
            if (text.isEmpty()) continue
            val noSpace = text.any(LoginCodeText::isNoSpaceScript)
            val term = Term(
                text = text,
                weight = weight,
                needLeft = !openLeft && !noSpace && LoginCodeText.isWordChar(text.first()),
                needRight = !openRight && !noSpace && LoginCodeText.isWordChar(text.last()),
            )
            byFirst.getOrPut(text.first()) { ArrayList() }.add(term)
            if (' ' !in text) {
                when {
                    openLeft && openRight -> Unit
                    openRight -> prefixStems += text
                    openLeft -> suffixStems += text
                    else -> words += text
                }
            }
        }
        byFirst.values.forEach { bucket -> bucket.sortByDescending { it.text.length } }
    }

    fun find(lower: String): List<TermHit> {
        val hits = ArrayList<TermHit>()
        for (i in lower.indices) {
            val bucket = byFirst[lower[i]] ?: continue
            for (term in bucket) {
                if (!lower.startsWith(term.text, i)) continue
                val end = i + term.text.length
                if (term.needLeft && i > 0 && LoginCodeText.joinsWord(lower[i - 1])) continue
                if (term.needRight && end < lower.length && LoginCodeText.joinsWord(lower[end])) continue
                hits += TermHit(i, end, term.weight)
            }
        }
        if (hits.size < 2) return hits
        val unique = LinkedHashMap<Long, TermHit>()
        for (hit in hits) {
            val key = (hit.start.toLong() shl 32) or hit.end.toLong()
            val existing = unique[key]
            if (existing == null || existing.weight < hit.weight) unique[key] = hit
        }
        val distinct = unique.values.toList()
        // A phrase and the word inside it describe one mention; keep the stronger reading.
        return distinct.filter { hit ->
            distinct.none { other ->
                other !== hit &&
                    other.start <= hit.start && other.end >= hit.end &&
                    (other.end - other.start > hit.end - hit.start) &&
                    other.weight >= hit.weight
            }
        }
    }

    fun matchesWord(word: String): Boolean =
        word in words ||
            prefixStems.any { word.startsWith(it) } ||
            suffixStems.any { word.endsWith(it) }
}

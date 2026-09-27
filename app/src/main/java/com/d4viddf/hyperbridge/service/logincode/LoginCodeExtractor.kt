package com.d4viddf.hyperbridge.service.logincode

import com.d4viddf.hyperbridge.service.logincode.LoginCodeLexicon.MEDIUM
import com.d4viddf.hyperbridge.service.logincode.LoginCodeLexicon.STRONG
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A detected login code. [code] is ASCII, with group separators removed. */
data class LoginCode(val code: String, val score: Int)

/**
 * On-device detector for one-time login, verification and 2FA codes in notification text.
 *
 * Pipeline:
 * 1. Normalize every field (NFKC, all Unicode digit systems to ASCII, invisible/bidi marks
 *    removed, dash and space variants unified) and join the title and de-duplicated bodies.
 * 2. Resolve structured formats first (WebOTP `@domain #code` lines).
 * 3. Generate candidates from ASCII alphanumeric runs: digit runs, space/dash separated digit
 *    groups (`123 456`, `482-913`), upper-case alphanumerics (`F7K2P`) and letter-only codes.
 * 4. Score each candidate against a multilingual lexicon: distance and grammatical binding to
 *    code/verification terms, lead verbs, emphasis (brackets, own line, `G-` prefixes), and
 *    penalties for money, units, order/phone/reference context, masked card digits, dates,
 *    URLs, sender short codes and promotional messages.
 * 5. Merge repeated codes and return the best candidate above [MIN_SCORE].
 *
 * Pure Kotlin with no I/O, so it is deterministic and unit testable.
 */
object LoginCodeExtractor {
    const val MIN_SCORE = 32
    private const val MAX_INPUT = 4_000
    private const val OPENERS = "[(\"'«“‘【「『<*〔［"
    private const val CLOSERS = "])\"'»”’】」』>*〕］"
    private const val BULLETS = "•●·∙◦▪■"

    private val WEB_OTP = Regex("""(?:^|\s)@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+\s+#([A-Za-z0-9]{4,10})(?=\s|$)""")
    private val URL = Regex("""(?i)(?:https?://|www\.)\S+|\b[a-z0-9-]+(?:\.[a-z0-9-]+)*\.[a-z]{2,10}/\S*""")
    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")

    fun extract(text: CharSequence?): LoginCode? = extract(null, listOf(text))

    fun extract(title: CharSequence?, bodies: List<CharSequence?>): LoginCode? {
        val document = Document.build(title, bodies) ?: return null
        return Analysis(document).run()
    }

    private enum class Kind { DIGITS, GROUPED, ALNUM, LETTERS }

    private enum class Binding { NONE, SEMI, FILLER, DIRECT }

    private class Candidate(val start: Int, val end: Int, val code: String, val kind: Kind) {
        var fragment = false
        var longNumberMember = false
    }

    private class Run(val start: Int, val end: Int, val digitsOnly: Boolean, val lettersOnly: Boolean)

    private class Document(
        val text: String,
        val lower: String,
        val titleEnd: Int,
        val bodyRanges: List<IntRange>,
    ) {
        companion object {
            fun build(title: CharSequence?, bodies: List<CharSequence?>): Document? {
                val normalizedTitle = title?.let { LoginCodeText.normalize(it).trim() }.orEmpty()
                val bodyTexts = ArrayList<String>()
                for (body in bodies) {
                    val value = body?.let { LoginCodeText.normalize(it).trim() }.orEmpty()
                    if (value.isEmpty() || bodyTexts.any { it.contains(value) }) continue
                    bodyTexts.removeAll { value.contains(it) }
                    bodyTexts += value
                }
                if (normalizedTitle.isEmpty() && bodyTexts.isEmpty()) return null
                val out = StringBuilder()
                out.append(normalizedTitle)
                val titleEnd = out.length
                val ranges = ArrayList<IntRange>()
                for (body in bodyTexts) {
                    if (out.length >= MAX_INPUT) break
                    if (out.isNotEmpty()) out.append('\n')
                    val start = out.length
                    out.append(body)
                    ranges += start until min(out.length, MAX_INPUT)
                }
                val text = if (out.length > MAX_INPUT) out.substring(0, MAX_INPUT) else out.toString()
                return Document(text, LoginCodeText.lower(text), titleEnd, ranges)
            }
        }
    }

    private class Analysis(private val doc: Document) {
        private val text = doc.text
        private val lower = doc.lower
        private val n = text.length
        private lateinit var keywords: List<TermHit>
        private lateinit var negatives: List<TermHit>
        private var maxWeight = 0
        private var verificationContext = false
        private var promotional = false
        private var mostlyUpper = false
        private val excluded = ArrayList<IntRange>()

        fun run(): LoginCode? {
            if (text.none { LoginCodeText.isAsciiDigit(it) || LoginCodeText.isAsciiUpper(it) }) return null
            WEB_OTP.find(text)?.let { return LoginCode(it.groupValues[1], 100) }

            negatives = LoginCodeLexicon.negatives.find(lower)
            // "zip code", "postcode", "barcode": the negative phrase owns the word "code".
            keywords = LoginCodeLexicon.keywords.find(lower).filter { hit ->
                negatives.none { it.start <= hit.start && it.end >= hit.end }
            }
            maxWeight = keywords.maxOfOrNull { it.weight } ?: 0
            verificationContext = keywords.any { it.weight == MEDIUM }
            promotional = LoginCodeLexicon.promo.find(lower).isNotEmpty()
            val letters = text.count { it.isLetter() && it.code < 0x250 }
            mostlyUpper = letters >= 12 && text.count { it.isUpperCase() && it.code < 0x250 } * 10 >= letters * 6
            URL.findAll(text).forEach { excluded += it.range }
            EMAIL.findAll(text).forEach { excluded += it.range }

            val candidates = collectCandidates()
            if (candidates.isEmpty()) return null

            val scored = ArrayList<Pair<Candidate, Int>>(candidates.size)
            for (candidate in candidates) {
                score(candidate)?.let { scored += candidate to it }
            }
            if (scored.isEmpty()) return null

            val distinct = scored.groupBy { it.first.code }
            val soleCode = distinct.size == 1
            var bestCode: String? = null
            var bestScore = Int.MIN_VALUE
            for ((code, hits) in distinct) {
                val repeated = min(8, 4 * (hits.size - 1))
                val value = hits.maxOf { it.second } + repeated + if (soleCode) 5 else 0
                if (value > bestScore) {
                    bestScore = value
                    bestCode = code
                }
            }
            val code = bestCode ?: return null
            return if (bestScore >= MIN_SCORE) LoginCode(code, bestScore) else null
        }

        private fun collectCandidates(): List<Candidate> {
            val runs = ArrayList<Run>()
            var i = 0
            while (i < n) {
                if (!LoginCodeText.isAsciiAlnum(text[i])) {
                    i++
                    continue
                }
                val start = i
                var digits = true
                var upperLetters = true
                while (i < n && LoginCodeText.isAsciiAlnum(text[i])) {
                    val ch = text[i]
                    if (!LoginCodeText.isAsciiDigit(ch)) digits = false
                    if (!LoginCodeText.isAsciiUpper(ch)) upperLetters = false
                    i++
                }
                runs += Run(start, i, digits, upperLetters)
            }

            val byRun = arrayOfNulls<Candidate>(runs.size)
            val out = ArrayList<Candidate>()
            for ((index, run) in runs.withIndex()) {
                if (isExcluded(run.start, run.end)) continue
                val token = text.substring(run.start, run.end)
                val candidate = when {
                    run.digitsOnly -> if (token.length in 4..10) Candidate(run.start, run.end, token, Kind.DIGITS) else null
                    run.lettersOnly -> if (token.length in 4..8) Candidate(run.start, run.end, token, Kind.LETTERS) else null
                    else -> alphanumeric(run, token)
                }
                if (candidate != null) {
                    byRun[index] = candidate
                    out += candidate
                }
            }

            var j = 0
            while (j < runs.size) {
                if (!runs[j].digitsOnly) {
                    j++
                    continue
                }
                var k = j
                var separator: Char? = null
                while (k + 1 < runs.size) {
                    val a = runs[k]
                    val b = runs[k + 1]
                    if (!b.digitsOnly || b.start != a.end + 1) break
                    val sep = text[a.end]
                    if (sep != ' ' && sep != '-') break
                    if (separator == null) separator = sep else if (separator != sep) break
                    k++
                }
                if (k > j) {
                    val chain = runs.subList(j, k + 1)
                    val lengths = chain.map { it.end - it.start }
                    val total = lengths.sum()
                    val chunked = lengths.all { it in 2..4 }
                    val phonePrefix = charAt(chain.first().start - 1) == '+' ||
                        charAt(chain.first().start - 1) == '(' ||
                        (charAt(chain.first().start - 1) == ' ' && charAt(chain.first().start - 2) == '+')
                    val groupable = chunked && !phonePrefix && chain.size in 2..4 && total in 6..9 &&
                        lengths.max() - lengths.min() <= 1
                    if (groupable && !isExcluded(chain.first().start, chain.last().end)) {
                        val code = chain.joinToString("") { text.substring(it.start, it.end) }
                        out += Candidate(chain.first().start, chain.last().end, code, Kind.GROUPED)
                        for (m in j..k) byRun[m]?.fragment = true
                    } else if (phonePrefix || (chunked && (total > 9 || chain.size > 4))) {
                        for (m in j..k) byRun[m]?.longNumberMember = true
                    }
                    j = k + 1
                } else {
                    j++
                }
            }
            return out
        }

        private fun alphanumeric(run: Run, token: String): Candidate? {
            if (token.length !in 4..10) return null
            if (token.any { it in 'a'..'z' }) return null
            val letters = token.filter(LoginCodeText::isAsciiUpper)
            if (letters.isEmpty() || letters.length == token.length) return null
            // XXXX1234 is a masked card or account number.
            if (letters.length >= 2 && letters.all { it == 'X' }) return null
            val firstLetter = token.indexOfFirst(LoginCodeText::isAsciiUpper)
            if (firstLetter > 0 &&
                token.substring(firstLetter).all(LoginCodeText::isAsciiUpper) &&
                token.substring(firstLetter) in LoginCodeLexicon.unitSuffixes
            ) return null
            return Candidate(run.start, run.end, token, Kind.ALNUM)
        }

        private fun score(c: Candidate): Int? {
            if (rejectedByNeighbours(c)) return null
            val length = c.code.length
            var score = when (c.kind) {
                Kind.DIGITS -> digitLengthScore(length)
                Kind.GROUPED -> digitLengthScore(length) + 6
                Kind.ALNUM -> alnumLengthScore(length) + if (transitions(c.code) >= 2) 4 else 0
                Kind.LETTERS -> 2
            }

            var best = 0.0
            var second = 0.0
            var nearestKeyword = Int.MAX_VALUE
            var tightBonus = 0
            var strongBoundBefore = false
            var weakBound = false
            for (hit in keywords) {
                val before = hit.end <= c.start
                val after = hit.start >= c.end
                if (!before && !after) {
                    if (c.kind == Kind.LETTERS) return null
                    continue
                }
                val gapStart = if (before) hit.end else c.end
                val gapEnd = if (before) c.start else hit.start
                val distance = gapEnd - gapStart
                nearestKeyword = min(nearestKeyword, distance)
                val contribution = proximity(distance) * hit.weight / 3.0 * if (before) 1.0 else 0.9
                if (contribution > best) {
                    second = best
                    best = contribution
                } else if (contribution > second) {
                    second = contribution
                }
                if (distance > 40) continue
                val binding = binding(gapStart, gapEnd)
                val bonus = when (binding) {
                    Binding.DIRECT -> if (before) 18 else 12
                    Binding.FILLER -> if (before) 13 else 11
                    Binding.SEMI -> 6
                    Binding.NONE -> 0
                }
                val bound = binding == Binding.DIRECT || binding == Binding.FILLER
                if (hit.weight >= MEDIUM) {
                    tightBonus = max(tightBonus, bonus)
                    if (hit.weight == STRONG && before && bound) strongBoundBefore = true
                } else if (bound) {
                    weakBound = true
                    tightBonus = max(tightBonus, bonus / 2)
                }
            }
            score += (best + 0.3 * second).roundToInt() + tightBonus

            val bare = isBareBody(c)
            val ownLine = isOwnLine(c)
            val colonBefore = previousVisible(c.start) in ":=" 
            if (maxWeight < MEDIUM && !weakBound && !bare) return null

            when (c.kind) {
                Kind.LETTERS -> {
                    if (mostlyUpper || c.code in LoginCodeLexicon.letterStopWords) return null
                    if (LoginCodeLexicon.keywords.matchesWord(c.code.lowercase())) return null
                    if (!strongBoundBefore && !(ownLine && colonBefore && maxWeight == STRONG)) return null
                }
                Kind.ALNUM -> {
                    if (promotional && !verificationContext) return null
                    val near = nearestKeyword <= 20 || ((ownLine || colonBefore) && nearestKeyword <= 45)
                    if (tightBonus == 0 && !near) return null
                }
                else -> Unit
            }

            score += emphasis(c, ownLine, colonBefore)
            if (bare && c.kind != Kind.LETTERS && length in 4..8) score += 35
            if (leadVerbBefore(c)) score += 12

            score -= negativePenalty(c, nearestKeyword)
            if (currencyAdjacent(c)) score -= 50
            if (unitFollows(c)) score -= 30
            if (inSenderTitle(c)) score -= 30
            if (joinsLetters(c)) score -= 10
            if (c.fragment) score -= 12
            if (c.longNumberMember) score -= 35
            if (c.kind == Kind.DIGITS || c.kind == Kind.GROUPED) score -= digitPatternPenalty(c)
            if (c.kind == Kind.GROUPED && tightBonus == 0 && looksLikeDate(c)) score -= 10
            if (promotional) score -= if (verificationContext) 8 else 40
            return score
        }

        private fun digitLengthScore(length: Int): Int = when (length) {
            4 -> 12
            5 -> 12
            6 -> 20
            7 -> 14
            8 -> 15
            9 -> 6
            else -> 4
        }

        private fun alnumLengthScore(length: Int): Int = when (length) {
            4 -> 6
            5 -> 10
            6 -> 12
            7, 8 -> 10
            else -> 4
        }

        private fun proximity(distance: Int): Int = when {
            distance <= 2 -> 34
            distance <= 6 -> 30
            distance <= 15 -> 25
            distance <= 30 -> 19
            distance <= 60 -> 11
            distance <= 120 -> 5
            else -> 2
        }

        private fun transitions(code: String): Int {
            var count = 0
            for (i in 1 until code.length) {
                if (LoginCodeText.isAsciiDigit(code[i]) != LoginCodeText.isAsciiDigit(code[i - 1])) count++
            }
            return count
        }

        /** How strongly the text between a term and a candidate ties them together. */
        private fun binding(from: Int, to: Int): Binding {
            if (to - from > 40) return Binding.NONE
            val words = ArrayList<String>()
            var i = from
            while (i < to) {
                val ch = lower[i]
                if (LoginCodeText.isAsciiDigit(ch)) return Binding.NONE
                if (!LoginCodeText.isWordChar(ch)) {
                    i++
                    continue
                }
                val start = i
                while (i < to && LoginCodeText.isWordChar(lower[i]) && !LoginCodeText.isAsciiDigit(lower[i])) i++
                if (i < to && LoginCodeText.isAsciiDigit(lower[i])) return Binding.NONE
                words += lower.substring(start, i)
            }
            if (words.isEmpty()) return if (to - from <= 4) Binding.DIRECT else Binding.FILLER
            if (words.size > 4) return Binding.NONE
            val nonFiller = words.count { !isFiller(it) }
            return when {
                nonFiller == 0 -> Binding.FILLER
                nonFiller == 1 && to - from <= 25 -> Binding.SEMI
                else -> Binding.NONE
            }
        }

        private fun isFiller(word: String): Boolean {
            if (word in LoginCodeLexicon.fillers || LoginCodeLexicon.keywords.matchesWord(word)) return true
            if (!word.all(LoginCodeText::isNoSpaceScript)) return false
            // Unspaced scripts glue fillers together ("ของคุณคือ"); peel known pieces greedily.
            var rest = word
            while (rest.isNotEmpty()) {
                val piece = unspacedPieces.firstOrNull { rest.startsWith(it) } ?: return false
                rest = rest.substring(piece.length)
            }
            return true
        }

        private fun emphasis(c: Candidate, ownLine: Boolean, colonBefore: Boolean): Int {
            var bonus = 0
            val before = charAt(c.start - 1)
            val after = charAt(c.end)
            if (before != null && after != null && OPENERS.indexOf(before) >= 0 && CLOSERS.indexOf(after) >= 0) {
                bonus += 8
            } else if (colonBefore) {
                bonus += 6
            }
            if (ownLine) bonus += 10
            // Issuer prefixes: G-123456, FB-12345, WA-123456.
            if (before == '-') {
                var p = c.start - 2
                var letters = 0
                while (p >= 0 && LoginCodeText.isAsciiUpper(text[p]) && letters < 4) {
                    letters++
                    p--
                }
                if (letters in 1..3 && (p < 0 || !LoginCodeText.isAsciiAlnum(text[p]))) bonus += 6
            }
            return bonus
        }

        private fun leadVerbBefore(c: Candidate): Boolean {
            var end = c.start
            while (end > 0 && text[end - 1] == ' ') end--
            if (end == c.start || c.start - end > 2) return false
            var start = end
            while (start > 0 && LoginCodeText.isWordChar(lower[start - 1])) start--
            if (start == end) return false
            return lower.substring(start, end) in LoginCodeLexicon.leads
        }

        private fun negativePenalty(c: Candidate, nearestKeyword: Int): Int {
            var best = 0.0
            var second = 0.0
            for (hit in negatives) {
                val before = hit.end <= c.start
                val after = hit.start >= c.end
                if (!before && !after) continue
                val distance = if (before) c.start - hit.end else hit.start - c.end
                if (distance > 25 || distance >= nearestKeyword) continue
                val factor = when {
                    distance <= 3 -> 1.0
                    distance <= 10 -> 0.75
                    else -> 0.5
                } * if (before) 1.0 else 0.7
                val penalty = hit.weight * factor
                if (penalty > best) {
                    second = best
                    best = penalty
                } else if (penalty > second) {
                    second = penalty
                }
            }
            return (best + 0.3 * second).roundToInt()
        }

        private fun rejectedByNeighbours(c: Candidate): Boolean {
            val p1 = charAt(c.start - 1)
            val p2 = charAt(c.start - 2)
            val n1 = charAt(c.end)
            val n2 = charAt(c.end + 1)
            // Part of a larger number: 1,234.56 / 12:30 / 12/05/2024 / 3.14159
            if (p1 != null && p1 in ".,:/" && p2 != null && LoginCodeText.isAsciiDigit(p2)) return true
            if (n1 != null && n1 in ".,:/" && n2 != null && LoginCodeText.isAsciiDigit(n2)) return true
            if (p1 == '+') return true
            if (p1 != null && BULLETS.indexOf(p1) >= 0) return true
            if (p1 == '*' && p2 != null && (p2 == '*' || BULLETS.indexOf(p2) >= 0) && n1 != '*') return true
            if (n1 == '%' || (n1 == ' ' && n2 == '%')) return true
            return false
        }

        private fun currencyAdjacent(c: Candidate): Boolean {
            var i = c.start - 1
            if (charAt(i) == ' ') i--
            val symbolBefore = charAt(i)
            if (symbolBefore != null && LoginCodeLexicon.currencySymbols.indexOf(symbolBefore) >= 0) return true
            var start = i + 1
            while (start > 0 && i + 1 - start < 6 && isCurrencyChar(lower[start - 1])) start--
            if (start <= i) {
                val word = lower.substring(start, i + 1)
                if ((start == 0 || !LoginCodeText.isWordChar(lower[start - 1])) &&
                    (word in LoginCodeLexicon.currencyWords || word.trimEnd('.') in LoginCodeLexicon.currencyWords)
                ) return true
            }
            var j = c.end
            if (charAt(j) == ' ') j++
            val symbolAfter = charAt(j)
            if (symbolAfter != null && LoginCodeLexicon.currencySymbols.indexOf(symbolAfter) >= 0) return true
            var end = j
            while (end < n && end - j < 6 && isCurrencyChar(lower[end])) end++
            if (end > j) {
                val word = lower.substring(j, end)
                if ((end == n || !LoginCodeText.isWordChar(lower[end])) &&
                    (word in LoginCodeLexicon.currencyWords || word.trimEnd('.') in LoginCodeLexicon.currencyWords)
                ) return true
            }
            return false
        }

        private fun isCurrencyChar(ch: Char): Boolean =
            (LoginCodeText.isWordChar(ch) && !LoginCodeText.isAsciiDigit(ch)) || ch == '$' || ch == '.' || ch == '"'

        private fun unitFollows(c: Candidate): Boolean {
            var j = c.end
            if (charAt(j) == ' ') j++
            if (j >= n) return false
            for (unit in LoginCodeLexicon.units) {
                if (!lower.startsWith(unit, j)) continue
                val end = j + unit.length
                val unspaced = unit.any(LoginCodeText::isNoSpaceScript)
                val lastIsWord = LoginCodeText.isWordChar(unit.last())
                if (unspaced || !lastIsWord || end >= n || !LoginCodeText.joinsWord(lower[end])) return true
            }
            return false
        }

        /** A title that is nothing but this number is the sender (short code or phone). */
        private fun inSenderTitle(c: Candidate): Boolean {
            if (c.end > doc.titleEnd || doc.bodyRanges.isEmpty()) return false
            return onlyCandidateIn(0, doc.titleEnd, c)
        }

        private fun isBareBody(c: Candidate): Boolean =
            doc.bodyRanges.any { range -> c.start >= range.first && c.end <= range.last + 1 && onlyCandidateIn(range.first, range.last + 1, c) }

        private fun onlyCandidateIn(from: Int, to: Int, c: Candidate): Boolean {
            for (i in from until to) {
                if (i >= c.start && i < c.end) continue
                if (LoginCodeText.isWordChar(text[i])) return false
            }
            return true
        }

        private fun isOwnLine(c: Candidate): Boolean {
            var start = c.start
            while (start > 0 && text[start - 1] != '\n') start--
            var end = c.end
            while (end < n && text[end] != '\n') end++
            return onlyCandidateIn(start, end, c)
        }

        private fun joinsLetters(c: Candidate): Boolean {
            val before = charAt(c.start - 1)
            val after = charAt(c.end)
            return (before != null && before.isLetter() && LoginCodeText.joinsWord(before)) ||
                (after != null && after.isLetter() && LoginCodeText.joinsWord(after))
        }

        private fun digitPatternPenalty(c: Candidate): Int {
            val code = c.code
            var penalty = 0
            if (code.all { it == code[0] }) penalty += 8
            val ascending = (1 until code.length).all { code[it] - code[it - 1] == 1 }
            val descending = (1 until code.length).all { code[it - 1] - code[it] == 1 }
            if (ascending || descending) penalty += 6
            if (c.kind == Kind.DIGITS && code.length == 4 && code.toInt() in 1950..2099) penalty += 8
            return penalty
        }

        private fun looksLikeDate(c: Candidate): Boolean {
            val parts = text.substring(c.start, c.end).split(' ', '-').mapNotNull { it.toIntOrNull() }
            if (parts.size != 3) return false
            val (a, b, _) = parts
            return (a in 1..31 && b in 1..12) || (a in 1..12 && b in 1..31)
        }

        private fun previousVisible(index: Int): Char {
            var i = index - 1
            while (i >= 0 && text[i] == ' ') i--
            return if (i >= 0) text[i] else '\u0000'
        }

        private fun charAt(index: Int): Char? = if (index in 0 until n) text[index] else null

        private fun isExcluded(start: Int, end: Int): Boolean =
            excluded.any { start >= it.first && end <= it.last + 1 }
    }

    private val unspacedPieces: List<String> by lazy {
        (LoginCodeLexicon.fillers + LoginCodeLexicon.keywords.words)
            .filter { word -> word.isNotEmpty() && word.all(LoginCodeText::isNoSpaceScript) }
            .sortedByDescending { it.length }
    }
}

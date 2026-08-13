package com.mkilci.kmparchitect.buildlogic.rules

/**
 * Finds the bodies of actual `override fun reduce(...)` declarations.
 *
 * Deliberately signature-driven, not filename-driven: a rule that scans `*Event.kt` or `*Reducer.kt`
 * misses a reducer written anywhere else and flags side effects in methods that are not reducers at
 * all. This is a lightweight scanner, not a Kotlin parser — it is a guardrail, and the behavioural
 * reducer tests are what actually prove purity.
 *
 * Known limit: an expression body continued onto a following line with a leading `.` (a chained
 * call) is cut at the first balanced newline. That is a false negative, not a false positive.
 */
object ReducerExtractor {

    private val declaration = Regex("""override\s+fun\s+reduce\s*\(""")

    data class ReducerBody(
        val body: String,
        val isExpressionBody: Boolean,
        /** 1-based line of the declaration, for error messages that a human can act on. */
        val line: Int,
    )

    fun extract(source: String): List<ReducerBody> =
        declaration.findAll(source).mapNotNull { match ->
            val openParen = source.indexOf('(', startIndex = match.range.last - 1)
            val closeParen = matchingIndex(source, openParen, '(', ')') ?: return@mapNotNull null
            val bodyStart = skipToBodyStart(source, closeParen + 1) ?: return@mapNotNull null
            val line = source.take(match.range.first).count { it == '\n' } + 1

            when (source[bodyStart]) {
                '{' -> matchingIndex(source, bodyStart, '{', '}')?.let { end ->
                    ReducerBody(source.substring(bodyStart + 1, end), isExpressionBody = false, line = line)
                }
                '=' -> ReducerBody(expressionBody(source, bodyStart + 1), isExpressionBody = true, line = line)
                else -> null
            }
        }.toList()

    /** Skips the optional `: ReturnType` between the parameter list and the body. */
    private fun skipToBodyStart(source: String, from: Int): Int? {
        var index = from
        while (index < source.length) {
            when (source[index]) {
                '{', '=' -> return index
                else -> index++
            }
        }
        return null
    }

    private fun matchingIndex(source: String, openIndex: Int, open: Char, close: Char): Int? {
        var depth = 0
        var index = openIndex
        while (index < source.length) {
            when (source[index]) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return index
                }
            }
            index++
        }
        return null
    }

    private fun expressionBody(source: String, from: Int): String {
        val builder = StringBuilder()
        var depth = 0
        var index = from
        while (index < source.length) {
            val char = source[index]
            when (char) {
                '(', '{', '[' -> depth++
                ')', '}', ']' -> depth--
                '\n' -> if (depth <= 0 && builder.isNotBlank()) return builder.toString()
            }
            builder.append(char)
            index++
        }
        return builder.toString()
    }
}

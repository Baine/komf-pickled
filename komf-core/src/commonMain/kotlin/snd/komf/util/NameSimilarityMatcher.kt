package snd.komf.util

import kotlin.math.min

// ponytail: public class with mode state; removed companion factory/cached instances
class NameSimilarityMatcher(val mode: NameMatchingMode) {

    fun matches(name: String, namesToMatch: Collection<String>): Boolean {
        return namesToMatch.any { matches(name, it) }
    }

    fun matches(name: String, nameToMatch: String): Boolean {
        return if (mode == NameMatchingMode.EXACT || name.length in 1..3) name == nameToMatch
        else {
            val distance = levenshtein(name.uppercase(), nameToMatch.uppercase())
            val distanceThreshold = when (name.length) {
                in 4..6 -> 1
                in 7..9 -> 2
                else -> 3
            }
            return distance <= distanceThreshold
        }
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        if (lhs == rhs) return 0
        if (lhs.isEmpty()) return rhs.length
        if (rhs.isEmpty()) return lhs.length

        val previous = IntArray(rhs.length + 1) { it }
        val current = IntArray(rhs.length + 1)

        for (i in lhs.indices) {
            current[0] = i + 1
            for (j in rhs.indices) {
                val cost = if (lhs[i] == rhs[j]) 0 else 1
                current[j + 1] = min(
                    current[j] + 1,
                    min(previous[j + 1] + 1, previous[j] + cost)
                )
            }
            current.copyInto(previous)
        }
        return previous[rhs.length]
    }

    enum class NameMatchingMode {
        EXACT,
        CLOSEST_MATCH
    }

    companion object {
        init {
            val matcher = NameSimilarityMatcher(NameMatchingMode.CLOSEST_MATCH)
            check(matcher.matches("abcd", "abce")) { "levenshtein self-check failed" }
            check(!matcher.matches("abcd", "wxyz")) { "levenshtein self-check failed" }
        }
    }
}

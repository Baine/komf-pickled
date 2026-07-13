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

import io.github.microutils.kotlinstdlib.levenshtein

private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
    return levenshtein(lhs.toString(), rhs.toString())
}

enum class NameMatchingMode {
    EXACT,
    CLOSEST_MATCH
}
}

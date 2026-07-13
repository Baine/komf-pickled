package snd.komf.util

import kotlin.math.min
import com.github.h0tk3y.betterLevenshtein.LevenshteinDistance

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

    private val levenshtein = LevenshteinDistance()

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        return levenshtein.distance(lhs.toString(), rhs.toString())
    }

    enum class NameMatchingMode {
        EXACT,
        CLOSEST_MATCH
    }
}

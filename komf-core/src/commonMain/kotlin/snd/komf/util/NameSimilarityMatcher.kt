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

        val lhsLength = lhs.length + 1
        val rhsLength = rhs.length + 1

        val cost = Array(lhsLength) { it }
        val newCost = IntArray(lhsLength)

        for (i in 1 until rhsLength) {
            newCost[0] = i

            for (j in 1 until lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                newCost[j] = minOf(newCost[j - 1] + 1, cost[j] + 1, cost[j - 1] + match)
            }

            System.arraycopy(newCost, 0, cost, 0, lhsLength)
        }

        return cost[lhsLength - 1]
    }

    object NameMatchingMode {
        const val EXACT = "EXACT"
        const val CLOSEST_MATCH = "CLOSEST_MATCH"
    }
}

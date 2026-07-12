// adapted to kotlin from https://github.com/gpanther/java-nat-sort
package snd.komf.util

fun <T : CharSequence?> caseInsensitiveNatSortComparator(): Comparator<T> {
    return CaseInsensitiveSimpleNaturalComparator()
}

// ponytail: flattened abstract class hierarchy — one subclass only

private class CaseInsensitiveSimpleNaturalComparator<T : CharSequence?> : Comparator<T> {
    override fun compare(sequence1: T, sequence2: T): Int {
        val len1 = sequence1!!.length
        val len2 = sequence2!!.length
        var idx1 = 0
        var idx2 = 0

        while (idx1 < len1 && idx2 < len2) {
            val c1 = sequence1[idx1++]
            val c2 = sequence2[idx2++]

            val isDigit1 = isDigit(c1)
            val isDigit2 = isDigit(c2)

            when {
                isDigit1 && !isDigit2 -> return -1
                !isDigit1 && isDigit2 -> return 1
                !isDigit1 && !isDigit2 -> {
                    val c = c1.lowercaseChar() - c2.lowercaseChar()
                    if (c != 0) return c
                }

                else -> {
                    var num1 = parse(c1)
                    while (idx1 < len1) {
                        val digit = sequence1[idx1++]
                        if (isDigit(digit)) {
                            num1 = num1 * 10 + parse(digit)
                        } else {
                            idx1--
                            break
                        }
                    }

                    var num2 = parse(c2)
                    while (idx2 < len2) {
                        val digit = sequence2[idx2++]
                        if (isDigit(digit)) {
                            num2 = num2 * 10 + parse(digit)
                        } else {
                            idx2--
                            break
                        }
                    }

                    if (num1 != num2) {
                        return compareUnsigned(num1, num2)
                    }
                }
            }
        }

        return when {
            idx1 < len1 -> 1
            idx2 < len2 -> -1
            else -> 0
        }
    }

    companion object {
        private fun compareUnsigned(num1: Long, num2: Long): Int {
            return compare(num1 + Long.MIN_VALUE, num2 + Long.MIN_VALUE)
        }

        private fun compare(x: Long, y: Long): Int {
            return if ((x < y)) -1 else (if ((x == y)) 0 else 1)
        }

        private fun parse(c1: Char): Long {
            return (c1.code - '0'.code).toLong()
        }

        private fun isDigit(c: Char): Boolean {
            return ('0' <= c) and (c <= '9')
        }
    }
}

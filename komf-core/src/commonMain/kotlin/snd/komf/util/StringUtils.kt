package snd.komf.util

import java.text.Normalizer

private val fullwidthRegex = "[\uff01-\uff5e]".toRegex()
private val combiningMarksRegex = "\\p{InCombiningDiacriticalMarks}+".toRegex()

fun replaceFullwidthChars(input: String) = input.replace(fullwidthRegex) { match ->
    Character.toString(match.value.codePointAt(0) - 0xfee0)
}

// ponytail: replaces commons-text StringUtils.stripAccents; komf-core targets only JVM
fun stripAccents(input: String): String =
    Normalizer.normalize(input, Normalizer.Form.NFD).replace(combiningMarksRegex, "")

private val parenthesesRegex = "[(\\[{]([^)\\]}]+)[)\\]}]".toRegex()

fun removeParentheses(name: String): String =
    name.replace(parenthesesRegex, "").trim()

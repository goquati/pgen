package de.quati.pgen.plugin.intern.util

private val VALID_CHAR_REGEX = Regex("[^a-zA-Z0-9_ -]")

private fun String.toNameParts() =
    replace(VALID_CHAR_REGEX) {
        when (it.value) {
            "ä" -> "ae"
            "ö" -> "oe"
            "ü" -> "ue"
            "Ä" -> "Ae"
            "Ö" -> "oe"
            "Ü" -> "Ue"
            "ß" -> "ss"
            else -> ""
        }
    }
        .split(' ', '_', '-')
        .filter { it.isNotEmpty() }

private fun String.toValidName() = if (isEmpty())
    "_empty"
else if (first().isDigit())
    "_$this"
else
    this

fun String.toCamelCase(capitalized: Boolean) = toNameParts()
    .mapIndexed { idx, s ->
        s.replaceFirstChar {
            if (idx == 0 && !capitalized)
                it.lowercaseChar()
            else
                it.titlecaseChar()
        }
    }.joinToString(separator = "")
    .toValidName()

fun String.toKababCase() = toNameParts()
    .joinToString(separator = "-") { it.lowercase() }
    .toValidName()

fun String.toSnakeCase(uppercase: Boolean = false) = toNameParts()
    .joinToString(separator = "_") { if (uppercase) it.uppercase() else it.lowercase() }
    .toValidName()

fun String.toHyphenated() = toNameParts()
    .joinToString(separator = "-") { it.lowercase() }
    .toValidName()

fun String.makeDifferent(blackList: Iterable<String>): String {
    val blackListSet = blackList.toSet()
    var name = this
    while (name in blackListSet) {
        name = "_$name"
    }
    return name
}

val kotlinKeywords = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun",
    "if", "in", "interface", "is", "null", "object", "package", "return",
    "super", "this", "throw", "true", "try", "typealias", "val", "var",
    "when", "while", "by", "catch", "constructor", "delegate", "dynamic",
    "field", "file", "finally", "get", "import", "init", "param", "property",
    "receiver", "set", "setparam", "where", "actual", "abstract", "annotation",
    "companion", "const", "crossinline", "data", "enum", "expect", "external",
    "final", "infix", "inline", "inner", "internal", "lateinit", "noinline",
    "open", "operator", "out", "override", "private", "protected", "public",
    "reified", "sealed", "suspend", "tailrec", "vararg"
)

package com.algallad.store.data

private val tashkeel = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")
private val nonWord = Regex("[^\\p{L}\\p{Nd}]+")

fun normalizeSearchText(value: String): String = value
    .lowercase()
    .replace(tashkeel, "")
    .replace("\u0640", "")
    .replace('\u0623', '\u0627')
    .replace('\u0625', '\u0627')
    .replace('\u0622', '\u0627')
    .replace('\u0649', '\u064A')
    .replace('\u0629', '\u0647')
    .replace(nonWord, " ")
    .trim()
    .replace(Regex("\\s+"), " ")

fun searchWords(value: String): List<String> = normalizeSearchText(value)
    .split(' ')
    .filter { it.length >= 2 }

fun buildSearchTokens(vararg fields: String, maxTokens: Int = 80): List<String> {
    val out = linkedSetOf<String>()
    fields.asSequence()
        .flatMap { searchWords(it).asSequence() }
        .forEach { word ->
            for (len in 2..word.length) {
                out += word.substring(0, len)
                if (out.size >= maxTokens) return out.toList()
            }
        }
    return out.toList()
}
package ru.`object`.epsoncamera.extension

fun StringBuilder.appendIfNotNullOrBlank(prefix: String = "", value: String?, suffix: String = ""): StringBuilder {
    if (value.isNullOrBlank().not()) {
        append(prefix)
        append(value)
        append(suffix)
    }
    return this
}
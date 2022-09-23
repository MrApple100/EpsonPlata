package ru.`object`.detection.extension

fun Int?.orZero(): Int {
    return this ?: 0
}
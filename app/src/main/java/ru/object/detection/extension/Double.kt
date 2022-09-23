package ru.`object`.detection.extension

fun Double?.orZero(): Double {
    return this ?: 0.0
}
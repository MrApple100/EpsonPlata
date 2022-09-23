package ru.`object`.detection.extension

fun Long?.orZero(): Long {
    return this ?: 0L
}
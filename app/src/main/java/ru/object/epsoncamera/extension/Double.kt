package ru.`object`.epsoncamera.extension

fun Double?.orZero(): Double {
    return this ?: 0.0
}
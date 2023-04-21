package ru.`object`.epsoncamera.EpsonLocal.extension

fun Double?.orZero(): Double {
    return this ?: 0.0
}
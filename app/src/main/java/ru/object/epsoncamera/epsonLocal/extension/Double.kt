package ru.`object`.epsoncamera.epsonLocal.extension

fun Double?.orZero(): Double {
    return this ?: 0.0
}
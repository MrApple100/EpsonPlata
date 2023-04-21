package ru.`object`.epsoncamera.EpsonLocal.extension

fun Long?.orZero(): Long {
    return this ?: 0L
}
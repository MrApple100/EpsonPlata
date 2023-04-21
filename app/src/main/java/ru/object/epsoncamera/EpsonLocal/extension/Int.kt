package ru.`object`.epsoncamera.EpsonLocal.extension

fun Int?.orZero(): Int {
    return this ?: 0
}
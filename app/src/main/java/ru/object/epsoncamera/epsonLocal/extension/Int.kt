package ru.`object`.epsoncamera.epsonLocal.extension

fun Int?.orZero(): Int {
    return this ?: 0
}
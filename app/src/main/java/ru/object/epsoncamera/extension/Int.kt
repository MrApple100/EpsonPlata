package ru.`object`.epsoncamera.extension

fun Int?.orZero(): Int {
    return this ?: 0
}
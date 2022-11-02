package ru.`object`.epsoncamera.extension

fun Long?.orZero(): Long {
    return this ?: 0L
}
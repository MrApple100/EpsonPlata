package ru.`object`.epsoncamera.epsonLocal.extension

fun Long?.orZero(): Long {
    return this ?: 0L
}
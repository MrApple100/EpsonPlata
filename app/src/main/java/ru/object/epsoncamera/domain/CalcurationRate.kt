package ru.`object`.epsoncamera.domain

import android.widget.TextView

class CalcurationRate(_textView: TextView?) {
    var textView: TextView? = null
    var count = 0
    var startTime: Long = 0
    var endTime: Long = 0
    var rate = 0f

    init {
        textView = _textView
    }

    fun start() {
        count = 0
        startTime = System.currentTimeMillis()
        endTime = 0
        rate = 0f
    }

    fun updata() {
        endTime = System.currentTimeMillis()
        count++
        if (endTime - startTime > 1000) {
            rate = (count * 1000 / (endTime - startTime)).toFloat()
            startTime = endTime
            count = 0
            if(textView!=null)
                textView!!.text = rate.toString()
        }
    }

    fun finish() {
        count = 0
        startTime = 0
        endTime = 0
        rate = 0f
    }
}
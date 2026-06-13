package com.glassbox.hello.debug

import android.util.Log

object AppLog {
    fun d(tag: String, message: String): Int = Log.d(tag, message)
    fun d(tag: String, message: String, error: Throwable?): Int = Log.d(tag, message, error)
    fun i(tag: String, message: String): Int = Log.i(tag, message)
    fun i(tag: String, message: String, error: Throwable?): Int = Log.i(tag, message, error)
    fun w(tag: String, message: String): Int = Log.w(tag, message)
    fun w(tag: String, message: String, error: Throwable?): Int = Log.w(tag, message, error)
    fun e(tag: String, message: String): Int = Log.e(tag, message)
    fun e(tag: String, message: String, error: Throwable?): Int = Log.e(tag, message, error)
    fun v(tag: String, message: String): Int = Log.v(tag, message)
    fun v(tag: String, message: String, error: Throwable?): Int = Log.v(tag, message, error)
    fun wtf(tag: String, message: String): Int = Log.wtf(tag, message)
    fun wtf(tag: String, message: String, error: Throwable?): Int = Log.wtf(tag, message, error)
    fun println(priority: Int, tag: String, message: String): Int = Log.println(priority, tag, message)
    fun isLoggable(tag: String, level: Int): Boolean = Log.isLoggable(tag, level)
}

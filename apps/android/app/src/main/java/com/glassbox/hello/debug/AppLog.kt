package com.glassbox.hello.debug

object AppLog {
    fun d(tag: String, message: String): Int = 0
    fun d(tag: String, message: String, error: Throwable?): Int = 0
    fun i(tag: String, message: String): Int = 0
    fun i(tag: String, message: String, error: Throwable?): Int = 0
    fun w(tag: String, message: String): Int = 0
    fun w(tag: String, message: String, error: Throwable?): Int = 0
    fun e(tag: String, message: String): Int = 0
    fun e(tag: String, message: String, error: Throwable?): Int = 0
    fun v(tag: String, message: String): Int = 0
    fun v(tag: String, message: String, error: Throwable?): Int = 0
    fun wtf(tag: String, message: String): Int = 0
    fun wtf(tag: String, message: String, error: Throwable?): Int = 0
    fun println(priority: Int, tag: String, message: String): Int = 0
    fun isLoggable(tag: String, level: Int): Boolean = false
}

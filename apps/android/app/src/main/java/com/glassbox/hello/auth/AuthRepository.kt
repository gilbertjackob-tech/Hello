package com.glassbox.hello.auth

import com.glassbox.hello.core.User
import com.glassbox.hello.network.HelloApi

class AuthRepository(private val api: HelloApi) {

    suspend fun register(name: String, securityQuestion: String, securityAnswer: String): Result<User> {
        return api.register(name, securityQuestion, securityAnswer)
    }

    suspend fun getUserQuestion(name: String): Result<String> {
        return api.getUserQuestion(name)
    }

    suspend fun login(name: String, securityAnswer: String): Result<User> {
        return api.login(name, securityAnswer)
    }
}

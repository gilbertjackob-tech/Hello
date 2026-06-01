package com.glassbox.hello.auth

import com.glassbox.hello.core.User

class AuthRepository(
    private val cloudApi: CloudAuthApi = CloudAuthApi()
) {

    suspend fun register(name: String, securityQuestion: String, securityAnswer: String): Result<User> {
        return cloudApi.register(name, securityQuestion, securityAnswer)
    }

    suspend fun getUserQuestion(name: String): Result<String> {
        return cloudApi.getUserQuestion(name)
    }

    suspend fun login(name: String, securityAnswer: String): Result<User> {
        return cloudApi.login(name, securityAnswer)
    }
}

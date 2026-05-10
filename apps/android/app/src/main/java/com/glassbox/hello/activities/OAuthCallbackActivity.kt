package com.glassbox.hello.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.glassbox.hello.auth.OAuth2Manager
import com.glassbox.hello.client.ApiClient
import com.glassbox.hello.repository.ProfileRepository
import com.glassbox.hello.security.SecureDataStore
import kotlinx.coroutines.launch

/**
 * Receives OAuth redirect intents and persists the returned profile.
 */
class OAuthCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val callbackUri = intent?.data
        if (callbackUri == null) {
            finish()
            return
        }

        lifecycleScope.launch {
            val secureDataStore = SecureDataStore(applicationContext)
            val apiClient = ApiClient(applicationContext)
            val manager = OAuth2Manager(secureDataStore, apiClient)
            val result = manager.handleOAuthCallback(callbackUri)
            result
                .onSuccess { profile ->
                    ProfileRepository.create(applicationContext).createProfile(profile, activate = true)
                    Toast.makeText(applicationContext, "Profile connected", Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    Toast.makeText(
                        applicationContext,
                        error.message ?: "OAuth connection failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            apiClient.close()
            finish()
        }
    }
}

package com.medivox.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medivox.ai.presentation.AuthViewModel
import com.medivox.ai.presentation.VoiceViewModel
import com.medivox.ai.ui.AuthScreen
import com.medivox.ai.ui.VoiceScreen
import com.medivox.ai.ui.theme.MediVoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MediVoxTheme {
                val authViewModel: AuthViewModel = viewModel()
                val voiceViewModel: VoiceViewModel = viewModel()
                val authState by authViewModel.state.collectAsStateWithLifecycle()
                val voiceState by voiceViewModel.state.collectAsStateWithLifecycle()

                if (authState.user == null) {
                    AuthScreen(
                        state = authState,
                        onSignIn = authViewModel::signIn,
                        onRegister = authViewModel::register,
                    )
                } else {
                    VoiceScreen(
                        state = voiceState,
                        accountName = authState.user.displayName.ifBlank { authState.user.email },
                        onConnect = voiceViewModel::connect,
                        onDisconnect = voiceViewModel::disconnect,
                        onSignOut = {
                            voiceViewModel.disconnect()
                            authViewModel.signOut()
                        },
                        onPermissionDenied = voiceViewModel::reportPermissionDenied,
                        onDemoUtterance = voiceViewModel::addDemoUserUtterance,
                    )
                }
            }
        }
    }
}

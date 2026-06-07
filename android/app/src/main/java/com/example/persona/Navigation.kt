package com.example.persona

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.persona.data.PersonaRepository
import com.example.persona.ui.auth.AuthViewModel
import com.example.persona.ui.auth.LoginScreen
import com.example.persona.ui.auth.SignUpScreen
import com.example.persona.ui.main.MainScreen

@Composable
fun MainNavigation(repository: PersonaRepository) {
  val context = LocalContext.current
  val authViewModel: AuthViewModel = viewModel { AuthViewModel(repository, context) }

  val backStack = rememberNavBackStack(Login)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Login> {
          LoginScreen(
            viewModel = authViewModel,
            onNavigateToSignUp = { backStack.add(SignUp) },
            onLoginSuccess = { 
              // Clear login from backstack so back press closes app
              backStack.removeLastOrNull()
              backStack.add(Main) 
            }
          )
        }
        entry<SignUp> {
          SignUpScreen(
            viewModel = authViewModel,
            onNavigateToLogin = { backStack.removeLastOrNull() },
            onSignUpSuccess = {
              backStack.removeLastOrNull()
              backStack.add(Main)
            }
          )
        }
        entry<Main> {
          MainScreen(
            authViewModel = authViewModel,
            repository = repository,
            onLogout = {
              backStack.removeLastOrNull()
              backStack.add(Login)
            }
          )
        }
      },
  )
}

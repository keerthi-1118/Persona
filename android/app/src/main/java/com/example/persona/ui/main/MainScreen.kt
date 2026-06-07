package com.example.persona.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.persona.data.PersonaRepository
import com.example.persona.theme.*
import com.example.persona.ui.auth.AuthViewModel
import com.example.persona.ui.components.ClockNavigationMenu
import com.example.persona.ui.screens.*

import com.example.persona.ui.components.WavingGradientBackground

enum class PersonaTab(val title: String, val emoji: String) {
    Dashboard("Dashboard", "🏠"),
    Planner("Planner", "📅"),
    Assignments("Assignments", "📚"),
    Habits("Habits", "🔥"),
    Expenses("Expenses", "💰"),
    Notes("Notes", "📝")
}

@Composable
fun MainScreen(
    authViewModel: AuthViewModel,
    repository: PersonaRepository,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(PersonaTab.Dashboard) }
    var isEditMode by remember { mutableStateOf(false) }

    WavingGradientBackground(isDark = repository.isDarkTheme) {
        Scaffold(containerColor = Color.Transparent) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (selectedTab) {
                    PersonaTab.Dashboard -> DashboardScreen(
                        repository = repository,
                        isEditMode = isEditMode,
                        onEditModeChange = { isEditMode = it },
                        onNavigateToTab = { selectedTab = it },
                        onLogout = { authViewModel.logout(onLogout) }
                    )
                    PersonaTab.Planner -> PlannerScreen(repository)
                    PersonaTab.Assignments -> AssignmentsScreen(repository, authViewModel)
                    PersonaTab.Habits -> HabitsScreen(repository)
                    PersonaTab.Expenses -> ExpensesScreen(repository)
                    PersonaTab.Notes -> NotesScreen(repository)
                }

                // Floating Custom Clock Navigation
                ClockNavigationMenu(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    onDoubleTap = {
                        if (selectedTab != PersonaTab.Dashboard) {
                            selectedTab = PersonaTab.Dashboard
                            isEditMode = true
                        } else {
                            isEditMode = !isEditMode
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

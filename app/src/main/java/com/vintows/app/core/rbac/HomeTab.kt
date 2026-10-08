package com.vintows.app.core.rbac

import com.vintows.app.core.session.Session

/** Bottom-navigation destinations. Which ones a user gets depends on their role. */
enum class HomeTab(val label: String) {
    Home("Home"),
    Courses("Courses"),
    Tests("Tests"),
    Support("Support"),
    Modules("Modules"),
    Profile("Profile"),
}

/** Broad user groups the app cares about, derived from the JWT `role` and login `scope`. */
enum class AppRole { PlatformAdmin, Learner, Trainer, Institution, Other }

fun Session.appRole(): AppRole {
    if (isAdmin) return AppRole.PlatformAdmin
    return when (role?.lowercase()) {
        "learner", "student" -> AppRole.Learner
        "trainer", "instructor" -> AppRole.Trainer
        "institution", "company", "coaching" -> AppRole.Institution
        else -> AppRole.Other
    }
}

/** Tab rules from ANDROID_BUILD_PLAN.md (Phase 1). */
fun Session.homeTabs(): List<HomeTab> = when (appRole()) {
    AppRole.PlatformAdmin -> listOf(HomeTab.Support, HomeTab.Modules, HomeTab.Profile)
    AppRole.Learner -> listOf(HomeTab.Home, HomeTab.Courses, HomeTab.Tests, HomeTab.Support, HomeTab.Profile)
    AppRole.Trainer, AppRole.Institution -> listOf(HomeTab.Courses, HomeTab.Support, HomeTab.Modules, HomeTab.Profile)
    AppRole.Other -> listOf(HomeTab.Home, HomeTab.Support, HomeTab.Profile)
}

package com.vintows.app.feature.auth.domain

/** What the student fills in on the 4-step sign-up form. Values match the web /register form. */
data class LearnerRegistration(
    val email: String,
    val fullName: String = "",
    val phone: String = "",
    val currentRole: String = LearnerOptions.CURRENT_ROLES.first().value,
    val experienceYears: String = "",
    val techSkills: List<String> = emptyList(),
    val softSkills: List<String> = emptyList(),
    val targetRole: String = "",
    val upskillInterest: List<String> = emptyList(),
    val placementReady: String = "",
    val weeklyHours: String = "",
    val notes: String = "",
)

data class Option(val value: String, val label: String)

/** Option lists copied from the web learner registration form. */
object LearnerOptions {
    val CURRENT_ROLES = listOf(
        Option("student", "Student"),
        Option("working_pro", "Professional"),
        Option("jobseeker", "Job Seeker"),
    )
    val EXPERIENCE = listOf(
        Option("fresher", "Fresher (0 years)"),
        Option("1-2", "1-2 years"),
        Option("3-5", "3-5 years"),
        Option("5+", "5+ years"),
    )
    val TECH_SKILLS = listOf(
        "Python", "JavaScript", "Java", "React.js", "Node.js", "Data Analysis", "SQL",
        "Cloud (AWS/Azure)", "Machine Learning", "UI/UX Design", "DSA", "DevOps", "C++",
    )
    val SOFT_SKILLS = listOf("Communication", "Leadership", "Problem Solving", "Teamwork", "Time Management", "Adaptability")
    val UPSKILL_TRACKS = listOf(
        "Full Stack Dev", "Data Science", "Cloud Computing", "Cybersecurity",
        "Product Management", "Digital Marketing", "AI Engineering", "Business Analytics",
    )
    val PLACEMENT_TIMELINE = listOf(
        Option("immediate", "Immediately (within 1 month)"),
        Option("3months", "In 3 months"),
        Option("6months", "In 6 months"),
        Option("exploring", "Just exploring opportunities"),
    )
    val WEEKLY_HOURS = listOf(
        Option("<5", "Less than 5 hrs"),
        Option("5-10", "5-10 hours"),
        Option("10-20", "10-20 hours"),
        Option("20+", "20+ hours"),
    )
}

/** Step rules. The web only requires name, phone and email (step 1); the rest is optional. */
object LearnerRegistrationValidator {
    const val STEP_COUNT = 4
    private val PHONE = Regex("^[+]?[0-9 ()-]{7,20}$")

    /** Field → message for the given step; empty when the step is valid. */
    fun errors(step: Int, form: LearnerRegistration): Map<String, String> = buildMap {
        if (step == 0) {
            if (form.fullName.isBlank()) put("fullName", "Please enter your full name")
            when {
                form.phone.isBlank() -> put("phone", "Please enter your phone number")
                !PHONE.matches(form.phone.trim()) -> put("phone", "Enter a valid phone number")
            }
        }
    }
}

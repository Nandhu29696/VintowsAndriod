package com.vintows.app.feature.auth.ui.student

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.feature.auth.domain.AuthRepository
import com.vintows.app.feature.auth.domain.LearnerRegistration
import com.vintows.app.feature.auth.domain.LearnerRegistrationValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterUiState(
    val form: LearnerRegistration,
    val step: Int = 0,
    /** Free-text "Others" tech skills, comma separated; merged into techSkills on submit (like the web). */
    val otherTechSkills: String = "",
    val errors: Map<String, String> = emptyMap(),
    val submitting: Boolean = false,
    val submitError: String? = null,
) {
    val isLastStep: Boolean get() = step == LearnerRegistrationValidator.STEP_COUNT - 1
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val email: String = checkNotNull(savedStateHandle[ARG_EMAIL]) { "RegisterViewModel needs the verified email" }

    private val _state = MutableStateFlow(RegisterUiState(form = LearnerRegistration(email = email)))
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun update(transform: LearnerRegistration.() -> LearnerRegistration) =
        _state.update { it.copy(form = it.form.transform(), errors = emptyMap(), submitError = null) }

    fun onOtherTechSkillsChange(value: String) = _state.update { it.copy(otherTechSkills = value) }

    fun toggleTechSkill(skill: String) = update { copy(techSkills = techSkills.toggle(skill)) }
    fun toggleSoftSkill(skill: String) = update { copy(softSkills = softSkills.toggle(skill)) }
    fun toggleUpskill(track: String) = update { copy(upskillInterest = upskillInterest.toggle(track)) }

    fun next() {
        val s = _state.value
        val errors = LearnerRegistrationValidator.errors(s.step, s.form)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        if (s.isLastStep) submit() else _state.update { it.copy(step = it.step + 1) }
    }

    /** Returns false when already on the first step (screen should navigate back). */
    fun back(): Boolean {
        if (_state.value.step == 0 || _state.value.submitting) return false
        _state.update { it.copy(step = it.step - 1, errors = emptyMap(), submitError = null) }
        return true
    }

    private fun submit() {
        val s = _state.value
        if (s.submitting) return
        val others = s.otherTechSkills.split(',').map(String::trim).filter(String::isNotEmpty)
        val form = s.form.copy(techSkills = (s.form.techSkills + others).distinct())

        _state.update { it.copy(submitting = true, submitError = null) }
        viewModelScope.launch {
            when (val result = authRepository.registerLearner(form)) {
                // Session saved: the app shell switches to Home by itself.
                is NetworkResult.Success -> _state.update { it.copy(submitting = false) }
                is NetworkResult.Error -> _state.update { it.copy(submitting = false, submitError = result.message) }
            }
        }
    }

    private fun List<String>.toggle(item: String) = if (item in this) this - item else this + item

    companion object {
        /** Matches the property name of RegisterDestination. */
        const val ARG_EMAIL = "email"
    }
}

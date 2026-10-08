package com.vintows.app.feature.auth.ui.student

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vintows.app.feature.auth.domain.LearnerOptions
import com.vintows.app.feature.auth.domain.LearnerRegistration
import com.vintows.app.feature.auth.domain.LearnerRegistrationValidator
import com.vintows.app.feature.auth.domain.Option

private val STEP_TITLES = listOf("About you", "Your skills", "Your goals", "Commitment")

@Composable
fun RegisterRoute(
    onExit: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val goBack = { if (!viewModel.back()) onExit() }
    BackHandler(onBack = goBack)
    RegisterScreen(
        state = state,
        onBack = goBack,
        onNext = viewModel::next,
        onUpdate = viewModel::update,
        onOtherTechSkillsChange = viewModel::onOtherTechSkillsChange,
        onToggleTechSkill = viewModel::toggleTechSkill,
        onToggleSoftSkill = viewModel::toggleSoftSkill,
        onToggleUpskill = viewModel::toggleUpskill,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    state: RegisterUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onUpdate: (LearnerRegistration.() -> LearnerRegistration) -> Unit,
    onOtherTechSkillsChange: (String) -> Unit,
    onToggleTechSkill: (String) -> Unit,
    onToggleSoftSkill: (String) -> Unit,
    onToggleUpskill: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Create your profile") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
                LinearProgressIndicator(
                    progress = { (state.step + 1f) / LearnerRegistrationValidator.STEP_COUNT },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.step > 0) {
                        OutlinedButton(onClick = onBack, enabled = !state.submitting, modifier = Modifier.weight(1f).height(48.dp)) {
                            Text("Back")
                        }
                    }
                    Button(onClick = onNext, enabled = !state.submitting, modifier = Modifier.weight(1f).height(48.dp)) {
                        when {
                            state.submitting -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            state.isLastStep -> Text("Create account")
                            else -> Text("Next")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Step ${state.step + 1} of ${LearnerRegistrationValidator.STEP_COUNT} · ${STEP_TITLES[state.step]}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            when (state.step) {
                0 -> AboutYouStep(state, onUpdate)
                1 -> SkillsStep(state, onUpdate, onOtherTechSkillsChange, onToggleTechSkill, onToggleSoftSkill)
                2 -> GoalsStep(state.form, onUpdate, onToggleUpskill)
                else -> CommitmentStep(state.form, onUpdate)
            }
            if (state.submitError != null) {
                Text(state.submitError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AboutYouStep(state: RegisterUiState, onUpdate: (LearnerRegistration.() -> LearnerRegistration) -> Unit) {
    val form = state.form
    OutlinedTextField(
        value = form.fullName,
        onValueChange = { v -> onUpdate { copy(fullName = v) } },
        label = { Text("Full name *") },
        isError = "fullName" in state.errors,
        supportingText = state.errors["fullName"]?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.email,
        onValueChange = {},
        label = { Text("Email (verified)") },
        readOnly = true,
        enabled = false,
        trailingIcon = { Icon(Icons.Filled.Check, contentDescription = "Verified") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.phone,
        onValueChange = { v -> onUpdate { copy(phone = v) } },
        label = { Text("Phone *") },
        isError = "phone" in state.errors,
        supportingText = state.errors["phone"]?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )

    Text("Current professional status", style = MaterialTheme.typography.labelLarge)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        LearnerOptions.CURRENT_ROLES.forEachIndexed { index, option ->
            SegmentedButton(
                selected = form.currentRole == option.value,
                onClick = { onUpdate { copy(currentRole = option.value) } },
                shape = SegmentedButtonDefaults.itemShape(index, LearnerOptions.CURRENT_ROLES.size),
            ) { Text(option.label, maxLines = 1) }
        }
    }
}

@Composable
private fun SkillsStep(
    state: RegisterUiState,
    onUpdate: (LearnerRegistration.() -> LearnerRegistration) -> Unit,
    onOtherTechSkillsChange: (String) -> Unit,
    onToggleTechSkill: (String) -> Unit,
    onToggleSoftSkill: (String) -> Unit,
) {
    SelectField("Years of experience", LearnerOptions.EXPERIENCE, state.form.experienceYears) { v -> onUpdate { copy(experienceYears = v) } }
    Text("Tech skills", style = MaterialTheme.typography.labelLarge)
    ChipGroup(LearnerOptions.TECH_SKILLS, state.form.techSkills, onToggleTechSkill)
    OutlinedTextField(
        value = state.otherTechSkills,
        onValueChange = onOtherTechSkillsChange,
        label = { Text("Other skills (comma separated)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Soft skills", style = MaterialTheme.typography.labelLarge)
    ChipGroup(LearnerOptions.SOFT_SKILLS, state.form.softSkills, onToggleSoftSkill)
}

@Composable
private fun GoalsStep(
    form: LearnerRegistration,
    onUpdate: (LearnerRegistration.() -> LearnerRegistration) -> Unit,
    onToggleUpskill: (String) -> Unit,
) {
    OutlinedTextField(
        value = form.targetRole,
        onValueChange = { v -> onUpdate { copy(targetRole = v) } },
        label = { Text("Dream job title / target role") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Which upskilling tracks interest you most?", style = MaterialTheme.typography.labelLarge)
    ChipGroup(LearnerOptions.UPSKILL_TRACKS, form.upskillInterest, onToggleUpskill)
}

@Composable
private fun CommitmentStep(form: LearnerRegistration, onUpdate: (LearnerRegistration.() -> LearnerRegistration) -> Unit) {
    SelectField("How soon are you looking for a placement / job?", LearnerOptions.PLACEMENT_TIMELINE, form.placementReady) { v ->
        onUpdate { copy(placementReady = v) }
    }
    SelectField("Weekly hours you can dedicate to upskilling", LearnerOptions.WEEKLY_HOURS, form.weeklyHours) { v ->
        onUpdate { copy(weeklyHours = v) }
    }
    OutlinedTextField(
        value = form.notes,
        onValueChange = { v -> onUpdate { copy(notes = v) } },
        label = { Text("Anything else you'd like us to know? (optional)") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChipGroup(options: List<String>, selected: List<String>, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option in selected
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(option) },
                label = { Text(option) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
    }
}

/** Read-only text field that opens a menu; avoids ExposedDropdownMenu API churn between Material versions. */
@Composable
private fun SelectField(label: String, options: List<Option>, value: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = options.firstOrNull { it.value == value }?.label ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Select") },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent overlay so a tap anywhere on the field opens the menu.
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option.value)
                        open = false
                    },
                )
            }
        }
    }
}

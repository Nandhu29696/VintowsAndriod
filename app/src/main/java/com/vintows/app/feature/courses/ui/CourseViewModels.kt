package com.vintows.app.feature.courses.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.ui.Section
import com.vintows.app.feature.assessments.domain.Assessment
import com.vintows.app.feature.assessments.domain.AssessmentsRepository
import com.vintows.app.core.ui.toSection
import com.vintows.app.feature.courses.domain.ContentItem
import com.vintows.app.feature.courses.domain.ContentType
import com.vintows.app.feature.courses.domain.CourseNode
import com.vintows.app.feature.courses.domain.CoursesRepository
import com.vintows.app.feature.courses.domain.NodeContents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

// ---------- routes ----------
// The view models read these arguments from SavedStateHandle by property name, which is how
// type-safe navigation stores them (and keeps them across process death).

/** A program, subject, chapter, lesson or topic screen. [trail] is the breadcrumb ("Python › Basics"). */
@Serializable
data class CourseNodeDestination(
    val programId: String,
    val levelCode: String,
    val nodeId: String,
    val title: String,
    val trail: String = "",
    /** Topics only: the topic's external material link. */
    val contentUrl: String? = null,
)

@Serializable
data class ContentDestination(
    val type: String,
    val itemId: String,
    val levelCode: String,
    val recordId: String,
    val title: String,
)

// ---------- program list ----------

data class CoursesUiState(
    val programs: Section<List<CourseNode>> = Section.Loading,
    val query: String = "",
    val refreshing: Boolean = false,
) {
    val visiblePrograms: List<CourseNode>
        get() {
            val all = (programs as? Section.Loaded)?.data.orEmpty()
            val q = query.trim()
            return if (q.isEmpty()) all else all.filter { it.name.contains(q, ignoreCase = true) || it.description?.contains(q, ignoreCase = true) == true }
        }
}

@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val repository: CoursesRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(CoursesUiState(query = savedState[KEY_QUERY] ?: ""))
    val state: StateFlow<CoursesUiState> = _state.asStateFlow()

    init {
        load(refresh = false)
    }

    fun onQueryChange(query: String) {
        savedState[KEY_QUERY] = query
        _state.update { it.copy(query = query) }
    }

    fun refresh() = load(refresh = true)

    fun retry() = load(refresh = false)

    private fun load(refresh: Boolean) {
        _state.update { if (refresh) it.copy(refreshing = true) else it.copy(programs = Section.Loading) }
        viewModelScope.launch {
            val result = repository.programs().toSection()
            _state.update { s ->
                // A failed refresh keeps the list already on screen.
                val keepOld = refresh && result is Section.Failed && s.programs is Section.Loaded
                s.copy(programs = if (keepOld) s.programs else result, refreshing = false)
            }
        }
    }

    private companion object {
        const val KEY_QUERY = "courses_query"
    }
}

// ---------- one node ----------

data class CourseNodeUiState(
    val programId: String,
    val levelCode: String,
    val nodeId: String,
    val title: String,
    val trail: String,
    val contentUrl: String? = null,
    val contents: Section<NodeContents> = Section.Loading,
    /** Tests attached to this node. A failure here just hides the section. */
    val tests: List<Assessment> = emptyList(),
    val refreshing: Boolean = false,
)

@HiltViewModel
class CourseNodeViewModel @Inject constructor(
    private val repository: CoursesRepository,
    private val assessments: AssessmentsRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        CourseNodeUiState(
            programId = savedState.get<String>("programId").orEmpty(),
            levelCode = savedState.get<String>("levelCode").orEmpty(),
            nodeId = savedState.get<String>("nodeId").orEmpty(),
            title = savedState.get<String>("title").orEmpty(),
            trail = savedState.get<String>("trail").orEmpty(),
            contentUrl = savedState.get<String>("contentUrl")?.takeIf { it.isNotBlank() },
        ),
    )
    val state: StateFlow<CourseNodeUiState> = _state.asStateFlow()

    init {
        load(refresh = false)
    }

    fun refresh() = load(refresh = true)

    fun retry() = load(refresh = false)

    private fun load(refresh: Boolean) {
        val s = _state.value
        _state.update { if (refresh) it.copy(refreshing = true) else it.copy(contents = Section.Loading) }
        viewModelScope.launch {
            val tests = async { assessments.testsForNode(s.levelCode, s.nodeId, s.title) }
            val result = repository.nodeContents(s.levelCode, s.nodeId, s.programId).toSection()
            val testList = (tests.await() as? NetworkResult.Success)?.data
            _state.update { current ->
                val keepOld = refresh && result is Section.Failed && current.contents is Section.Loaded
                current.copy(
                    contents = if (keepOld) current.contents else result,
                    tests = testList ?: current.tests,
                    refreshing = false,
                )
            }
        }
    }
}

// ---------- one content item ----------

data class ContentUiState(
    val title: String,
    val type: ContentType?,
    val item: Section<ContentItem> = Section.Loading,
)

@HiltViewModel
class ContentViewModel @Inject constructor(
    private val repository: CoursesRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val type = ContentType.fromPath(savedState.get<String>("type").orEmpty())
    private val itemId = savedState.get<String>("itemId").orEmpty()
    private val levelCode = savedState.get<String>("levelCode").orEmpty()
    private val recordId = savedState.get<String>("recordId").orEmpty()

    private val _state = MutableStateFlow(ContentUiState(title = savedState.get<String>("title").orEmpty(), type = type))
    val state: StateFlow<ContentUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        if (type == null) {
            _state.update { it.copy(item = Section.Failed("This kind of material can't be opened in the app yet.")) }
            return
        }
        _state.update { it.copy(item = Section.Loading) }
        viewModelScope.launch {
            val result = repository.contentItem(type, itemId, levelCode, recordId).toSection()
            _state.update { it.copy(item = result) }
        }
    }
}

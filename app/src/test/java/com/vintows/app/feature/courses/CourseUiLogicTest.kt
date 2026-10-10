package com.vintows.app.feature.courses

import androidx.lifecycle.SavedStateHandle
import com.vintows.app.core.network.ErrorKind
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.ui.Section
import com.vintows.app.feature.assessments.domain.AnswerValue
import com.vintows.app.feature.assessments.domain.Assessment
import com.vintows.app.feature.assessments.domain.AssessmentsRepository
import com.vintows.app.feature.assessments.domain.Question
import com.vintows.app.feature.courses.domain.ContentItem
import com.vintows.app.feature.courses.domain.ContentType
import com.vintows.app.feature.courses.domain.CourseHierarchy
import com.vintows.app.feature.courses.domain.CourseLevel
import com.vintows.app.feature.courses.domain.CourseNode
import com.vintows.app.feature.courses.domain.CoursesRepository
import com.vintows.app.feature.courses.domain.NodeContents
import com.vintows.app.feature.courses.ui.ContentViewModel
import com.vintows.app.feature.courses.ui.CourseNodeViewModel
import com.vintows.app.feature.courses.ui.CoursesViewModel
import com.vintows.app.feature.courses.ui.fileSizeLabel
import com.vintows.app.feature.courses.ui.isWebUrl
import com.vintows.app.feature.courses.ui.pluralLabel
import com.vintows.app.feature.courses.ui.stripEditorPlaceholder
import com.vintows.app.feature.home.displayNameOf
import com.vintows.app.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CourseUiLogicTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val error = NetworkResult.Error(ErrorKind.Server, "Server error. Please try again later.", 500)

    private class FakeRepository : CoursesRepository {
        var programs: NetworkResult<List<CourseNode>> = NetworkResult.Success(
            listOf(
                CourseNode("p1", "Python Basics", "PROGRAM", description = "Start coding"),
                CourseNode("p2", "Data Science", "PROGRAM", isPaid = true, price = "499.00"),
            ),
        )
        var contents: NetworkResult<NodeContents> = NetworkResult.Success(
            NodeContents(CourseLevel("SUBJECT", "Subject", "subjects", 4), listOf(CourseNode("s1", "Intro", "SUBJECT")), emptyList()),
        )
        var item: NetworkResult<ContentItem> = NetworkResult.Success(ContentItem(ContentType.LiveDoc, "d1", "Doc", "PUBLISHED", html = "<p>Hi</p>"))
        val calls = mutableListOf<String>()

        override suspend fun hierarchy() = NetworkResult.Success(CourseHierarchy(emptyList()))
        override suspend fun programs() = programs.also { calls += "programs" }
        override suspend fun nodeContents(levelCode: String, nodeId: String, programId: String) =
            contents.also { calls += "node:$levelCode:$nodeId:$programId" }
        override suspend fun contentItem(type: ContentType, id: String, levelCode: String, recordId: String) =
            item.also { calls += "item:${type.apiPath}:$id:$levelCode:$recordId" }
    }

    private object NoTests : AssessmentsRepository {
        override suspend fun myTests() = NetworkResult.Success(emptyList<Assessment>())
        override suspend fun testsForNode(levelCode: String, recordId: String, nodeName: String?) = NetworkResult.Success(emptyList<Assessment>())
        override suspend fun check(assessmentId: String) = error("not used")
        override suspend fun start(assessmentId: String) = error("not used")
        override suspend fun attempt(attemptId: String) = error("not used")
        override suspend fun submit(attemptId: String, questions: List<Question>, answers: Map<String, AnswerValue>) = error("not used")
        override suspend fun reportProctoringEvent(attemptId: String, type: String, detail: String) = Unit
    }

    @Test
    fun `program search matches name and description, and is remembered`() {
        val saved = SavedStateHandle()
        val vm = CoursesViewModel(FakeRepository(), saved)

        vm.onQueryChange("coding")

        assertEquals(listOf("Python Basics"), vm.state.value.visiblePrograms.map { it.name })
        assertEquals("coding", saved.get<String>("courses_query"))
        vm.onQueryChange("  ")
        assertEquals(2, vm.state.value.visiblePrograms.size)
    }

    @Test
    fun `failed program refresh keeps the list`() {
        val repo = FakeRepository()
        val vm = CoursesViewModel(repo, SavedStateHandle())
        repo.programs = error

        vm.refresh()

        assertTrue(vm.state.value.programs is Section.Loaded)
        assertFalse(vm.state.value.refreshing)
    }

    @Test
    fun `node screen loads with its route arguments`() {
        val repo = FakeRepository()
        val vm = CourseNodeViewModel(
            repo,
            NoTests,
            SavedStateHandle(mapOf("programId" to "p1", "levelCode" to "PROGRAM", "nodeId" to "p1", "title" to "Python Basics", "trail" to "")),
        )

        assertEquals(listOf("node:PROGRAM:p1:p1"), repo.calls)
        assertEquals("Python Basics", vm.state.value.title)
        assertTrue(vm.state.value.contents is Section.Loaded)
    }

    @Test
    fun `content screen loads the item, unknown types fail without a call`() {
        val repo = FakeRepository()
        val args = mapOf("type" to "live-docs", "itemId" to "d1", "levelCode" to "SUBJECT", "recordId" to "s1", "title" to "Doc")

        val vm = ContentViewModel(repo, SavedStateHandle(args))
        assertEquals(listOf("item:live-docs:d1:SUBJECT:s1"), repo.calls)
        assertTrue(vm.state.value.item is Section.Loaded)

        repo.calls.clear()
        val unknown = ContentViewModel(repo, SavedStateHandle(args + ("type" to "holograms")))
        assertTrue(unknown.state.value.item is Section.Failed)
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun `level labels pluralise`() {
        assertEquals("Subjects", pluralLabel("Subject"))
        assertEquals("Chapters / Units", pluralLabel("Chapter / Unit"))
        assertEquals("Topics", pluralLabel("Topic"))
        assertEquals("Categories", pluralLabel("Category"))
        assertEquals("Classes", pluralLabel("Classes"))
    }

    @Test
    fun `untouched live docs count as empty`() {
        val qa = "<p>Press <span class=\"he-kbd-hint\">space</span> to Ask Vintows or <span class=\"he-kbd-hint\">/</span> to insert elements</p>"
        assertEquals("", stripEditorPlaceholder(qa))
        assertEquals("", stripEditorPlaceholder("<p>&nbsp;</p>"))
        assertEquals("<h1>Arrays</h1>", stripEditorPlaceholder(qa + "<h1>Arrays</h1>"))
    }

    @Test
    fun `only web links are opened`() {
        assertTrue(isWebUrl("https://loom.com/share/x"))
        assertTrue(isWebUrl("HTTP://example.com"))
        assertFalse(isWebUrl("javascript:alert(1)"))
        assertFalse(isWebUrl("file:///sdcard/x"))
        assertFalse(isWebUrl(""))
    }

    @Test
    fun `small formatting helpers`() {
        assertEquals("Asha", displayNameOf("asha.k@example.com"))
        assertEquals("Meena", displayNameOf("meena-r@example.com"))
        assertEquals("Ravi", displayNameOf("ravi_92@example.com"))
        assertEquals("", displayNameOf("1234@example.com"))
        assertEquals("2 KB", fileSizeLabel(2048))
        assertEquals("1.5 MB", fileSizeLabel(1_572_864).replace(',', '.'))
    }
}

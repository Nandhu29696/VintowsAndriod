package com.vintows.app.feature.courses.domain

import com.vintows.app.core.network.NetworkResult

/** One level of the course hierarchy, e.g. `SUBJECT` stored in table `subjects`. */
data class CourseLevel(
    val code: String,
    val label: String,
    val tableName: String,
    val order: Int,
)

/**
 * The enabled levels, top to bottom. Disabled levels (Department, Semester on QA) are skipped, so
 * Subject's parent becomes Program.
 */
data class CourseHierarchy(val levels: List<CourseLevel>) {
    val root: CourseLevel? get() = levels.firstOrNull()

    fun level(code: String): CourseLevel? = levels.firstOrNull { it.code == code }

    fun childOf(code: String): CourseLevel? {
        val index = levels.indexOfFirst { it.code == code }
        return if (index >= 0) levels.getOrNull(index + 1) else null
    }

    companion object {
        const val TOPIC = "TOPIC"

        /** FK column a child row uses to point at a [parent] row: `programs` → `program_id`. */
        fun foreignKeyOf(parent: CourseLevel): String = parent.tableName.removeSuffix("s") + "_id"
    }
}

/** A program, subject, chapter, lesson or topic. */
data class CourseNode(
    val id: String,
    val name: String,
    val levelCode: String,
    val description: String? = null,
    val isPaid: Boolean = false,
    val price: String? = null,
    /** Topics only: "45 min" style text when the duration is known. */
    val duration: String? = null,
    /** Topics only: external material link. */
    val contentUrl: String? = null,
)

enum class ContentType(val apiPath: String, val label: String, val icon: String) {
    LiveDoc("live-docs", "Live doc", "bi-broadcast"),
    CodeFile("code-files", "Code", "bi-code-slash"),
    SmartLink("smart-links", "Link", "bi-link-45deg"),
    LoomVideo("loom-videos", "Video", "bi-play"),
    CourseFile("course-files", "File", "bi-file-earmark"),
    Whiteboard("whiteboards", "Whiteboard", "bi-easel2"),
    ;

    companion object {
        fun fromPath(path: String): ContentType? = entries.firstOrNull { it.apiPath == path }
    }
}

/** A learning material attached to a node. Only the fields of its [type] are filled. */
data class ContentItem(
    val type: ContentType,
    val id: String,
    val name: String,
    /** `DRAFT`, `PUBLISHED`, `ARCHIVED` or null. */
    val status: String?,
    val html: String? = null,
    val code: String? = null,
    val language: String? = null,
    val url: String? = null,
    val mimeType: String? = null,
    val fileSize: Long? = null,
    val allowDownload: Boolean = true,
    val elementCount: Int = 0,
) {
    val isDraft: Boolean get() = status.equals("DRAFT", ignoreCase = true)

    /** Learners see published items and items without a status; drafts and archived items stay hidden. */
    val isVisibleToLearners: Boolean get() = status == null || status.equals("PUBLISHED", ignoreCase = true)
}

/** Everything one node screen shows. */
data class NodeContents(
    /** Label of the next level ("Subject", "Topic"), or null at the bottom of the tree. */
    val childLevel: CourseLevel?,
    val children: List<CourseNode>,
    val materials: List<ContentItem>,
)

interface CoursesRepository {
    suspend fun hierarchy(): NetworkResult<CourseHierarchy>

    /** Top-level nodes (programs on QA). */
    suspend fun programs(): NetworkResult<List<CourseNode>>

    suspend fun nodeContents(levelCode: String, nodeId: String, programId: String): NetworkResult<NodeContents>

    suspend fun contentItem(type: ContentType, id: String, levelCode: String, recordId: String): NetworkResult<ContentItem>
}

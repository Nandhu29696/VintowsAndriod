package com.vintows.app.core.rbac

import com.vintows.app.core.network.ApiEnvelope
import com.vintows.app.core.session.Session
import com.vintows.app.testing.resource
import com.vintows.app.testing.testJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MenuAndTabsTest {

    @Test
    fun `maps the real SuperAdmin roleaccess response`() {
        val envelope = testJson.decodeFromString<ApiEnvelope<List<RoleAccessDto>>>(resource("roleaccess_superadmin.json"))

        val items = envelope.data!!.toMenuItems()

        assertEquals(7, items.size)
        // sequenceNumber 1 first, then menus without a sequence in server order.
        assertEquals("Reports & Charts Access", items.first().title)
        assertEquals("/admin/reports-access", items.first().webRoute)

        val test = items.single { it.title == "Test" }
        assertEquals("bi-star-fill", test.iconName)
        assertNull(test.webRoute) // urlLink "" is treated as no route
        assertEquals(listOf("Action Icon Grants", "G_learners"), test.children.map { it.title })

        val schema = items.single { it.title == "Schema" }
        assertEquals("/app/module-assignment", schema.children.single().webRoute) // from source.filesUrl
    }

    private fun session(role: String?, scope: String?) =
        Session(accessToken = "t", userId = "1", email = "u@x.com", role = role, scope = scope)

    @Test
    fun `admin scope wins over role name`() {
        val s = session(role = "SuperAdmin", scope = "admin")
        assertEquals(AppRole.PlatformAdmin, s.appRole())
        assertEquals(listOf(HomeTab.Support, HomeTab.Modules, HomeTab.Profile), s.homeTabs())
    }

    @Test
    fun `learners and students get the learning tabs`() {
        val expected = listOf(HomeTab.Home, HomeTab.Courses, HomeTab.Tests, HomeTab.Support, HomeTab.Profile)
        assertEquals(expected, session("Learner", "global").homeTabs())
        assertEquals(expected, session("Student", "licensed").homeTabs())
    }

    @Test
    fun `trainer and institution roles`() {
        assertEquals(AppRole.Trainer, session("Instructor", "licensed").appRole())
        assertEquals(AppRole.Institution, session("Coaching", "licensed").appRole())
        assertEquals(
            listOf(HomeTab.Courses, HomeTab.Support, HomeTab.Modules, HomeTab.Profile),
            session("Trainer", "global").homeTabs(),
        )
    }

    @Test
    fun `unknown roles get a safe default`() {
        assertEquals(listOf(HomeTab.Home, HomeTab.Support, HomeTab.Profile), session("Recruiter", "global").homeTabs())
        assertEquals(AppRole.Other, session(null, null).appRole())
    }
}

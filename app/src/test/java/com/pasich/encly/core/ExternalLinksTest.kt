package com.pasich.encly.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Links the About screen opens: first-party, HTTPS, and never a third-party form service. */
class ExternalLinksTest {

    private val links = listOf(LINK_FEEDBACK, LINK_PRIVACY_POLICY)

    @Test
    fun linksPointToTheProjectRepositoryOverHttps() {
        links.forEach { link ->
            assertTrue(link, link.startsWith("https://github.com/pasichDev/Encly/"))
        }
    }

    @Test
    fun privacyPolicyIsTheTrackedPrivacyFile() {
        assertEquals("https://github.com/pasichDev/Encly/blob/main/PRIVACY.md", LINK_PRIVACY_POLICY)
    }
}

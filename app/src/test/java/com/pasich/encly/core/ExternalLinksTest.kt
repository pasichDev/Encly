package com.pasich.encly.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Links the About screen opens: first-party, HTTPS, and never a third-party form service. */
class ExternalLinksTest {

    @Test
    fun feedbackPointsToTheProjectRepositoryOverHttps() {
        assertTrue(LINK_FEEDBACK, LINK_FEEDBACK.startsWith("https://github.com/pasichDev/Encly/"))
    }

    @Test
    fun privacyPolicyIsTheDevelopersSiteOverHttps() {
        assertEquals("https://pasichdev.xyz/apps/encly/privacy-policy/", LINK_PRIVACY_POLICY)
    }
}

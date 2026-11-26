package com.pasich.encly.desktop

import com.pasich.encly.desktop.platform.DesktopExternalLinks
import com.pasich.encly.desktop.platform.FirstLinkOpener
import com.pasich.encly.desktop.platform.LinkOpener
import com.pasich.encly.desktop.platform.XdgOpenLinkOpener
import com.pasich.encly.desktop.platform.isSandboxed
import com.pasich.encly.desktop.platform.linuxLinkOpener
import com.pasich.encly.desktop.platform.startDetached
import java.net.URI
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** How a link leaves the app: the portal first, and outside a sandbox only, `xdg-open` and AWT after it. */
class LinkOpenersTest {
    private class Opener(private val takes: Boolean, private val throws: Boolean = false) : LinkOpener {
        val opened = mutableListOf<URI>()

        override fun open(uri: URI): Boolean {
            opened += uri
            if (throws) error("no bus")
            return takes
        }
    }

    private val link = URI("https://example.org/page")

    @Test
    fun theFirstOpenerThatTakesTheLinkWinsAndTheRestAreNotAsked() {
        val portal = Opener(takes = true)
        val xdg = Opener(takes = true)

        assertTrue(FirstLinkOpener(listOf(portal, xdg)).open(link))

        assertEquals(listOf(link), portal.opened)
        assertTrue(xdg.opened.isEmpty())
    }

    @Test
    fun anOpenerThatRefusesOrThrowsPassesTheLinkOn() {
        val refusing = Opener(takes = false)
        val broken = Opener(takes = true, throws = true)
        val working = Opener(takes = true)

        assertTrue(FirstLinkOpener(listOf(refusing, broken, working)).open(link))

        assertEquals(listOf(link), working.opened)
        assertFalse(FirstLinkOpener(listOf(refusing, broken)).open(link))
        assertFalse(FirstLinkOpener(emptyList()).open(link))
    }

    @Test
    fun insideASandboxOnlyThePortalIsAskedAndALinkItCannotOpenIsRefused() {
        val portal = Opener(takes = false)
        val xdg = Opener(takes = true)
        val awt = Opener(takes = true)

        assertFalse(linuxLinkOpener(portal, sandboxed = true, xdgOpen = xdg, awt = awt).open(link))

        assertEquals(listOf(link), portal.opened)
        assertTrue(xdg.opened.isEmpty() && awt.opened.isEmpty(), "neither can work in a sandbox")
    }

    @Test
    fun outsideASandboxTheLinkFallsBackToXdgOpenThenAwt() {
        val portal = Opener(takes = false)
        val xdg = Opener(takes = false)
        val awt = Opener(takes = true)

        assertTrue(linuxLinkOpener(portal, sandboxed = false, xdgOpen = xdg, awt = awt).open(link))
        assertEquals(1, portal.opened.size)
        assertEquals(1, xdg.opened.size)
        assertEquals(1, awt.opened.size)

        val noPortal = Opener(takes = false)
        val noXdg = Opener(takes = false)
        val noAwt = Opener(takes = false)
        assertFalse(linuxLinkOpener(noPortal, sandboxed = false, xdgOpen = noXdg, awt = noAwt).open(link))
        assertEquals(1, noAwt.opened.size)
    }

    @Test
    fun theSameSchemesAreAllowedAsBeforeWhateverTheOpener() {
        val opener = Opener(takes = true)
        val links = DesktopExternalLinks(linuxLinkOpener(opener, sandboxed = true))

        assertTrue(links.openUrl("https://example.org"))
        assertTrue(links.openUrl("http://example.org"))
        assertTrue(links.writeMail("someone@example.org"))
        assertFalse(links.openUrl("file:///etc/passwd"))
        assertFalse(links.openUrl("javascript:alert(1)"))
        assertFalse(links.openUrl("not a link"))
        assertEquals(3, opener.opened.size)
    }

    @Test
    fun xdgOpenIsStartedWithTheLinkAndItsFailureToStartIsARefusal() {
        val started = mutableListOf<List<String>>()

        assertTrue(
            XdgOpenLinkOpener {
                started += it
                true
            }.open(link),
        )
        assertFalse(XdgOpenLinkOpener { false }.open(link))

        assertEquals(listOf(listOf("xdg-open", link.toString())), started)
    }

    @Test
    fun aCommandThatCannotBeStartedIsFalseAndOneThatCanIsTrue() {
        assertFalse(startDetached(listOf("/no/such/program-for-encly-tests")))
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        assertTrue(startDetached(listOf(java, "-version")))
    }

    @Test
    fun aSandboxIsFlatpakOrSnap() {
        val nothing: (String) -> String? = { null }
        assertFalse(isSandboxed(nothing) { false })
        assertTrue(isSandboxed(nothing) { it == Path.of("/.flatpak-info") })
        assertTrue(isSandboxed({ if (it == "FLATPAK_ID") "io.github.pasichdev.Encly" else null }) { false })
        assertTrue(isSandboxed({ if (it == "SNAP") "/snap/encly" else null }) { false })
        // The real one answers without throwing, whatever this machine is.
        isSandboxed()
    }
}

package io.nekohasekai.sagernet.bg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalProxyManagerTest {

    @Test
    fun rotateSession_createsRuntimeCredentialsAndClearDropsThem() {
        LocalProxyManager.clear()

        val session = LocalProxyManager.rotateSession()

        assertNotNull(LocalProxyManager.currentSession())
        assertEquals(session, LocalProxyManager.currentSession())
        assertNotEquals(session.username, session.password)
        assertTrue(session.username.isNotBlank())
        assertTrue(session.password.isNotBlank())
        assertTrue(session.port > 0)

        LocalProxyManager.clear()

        assertNull(LocalProxyManager.currentSession())
    }
}

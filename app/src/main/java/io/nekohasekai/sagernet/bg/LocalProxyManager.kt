package io.nekohasekai.sagernet.bg

import io.nekohasekai.sagernet.ktx.mkPort
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

data class LocalProxySession(
    val port: Int,
    val username: String,
    val password: String,
)

object LocalProxyManager {

    private val random = SecureRandom()
    private val currentSession = AtomicReference<LocalProxySession?>()

    fun currentSession(): LocalProxySession? {
        return currentSession.get()
    }

    fun rotateSession(): LocalProxySession {
        val session = LocalProxySession(
            port = mkPort(),
            username = randomToken(),
            password = randomToken()
        )
        currentSession.set(session)
        return session
    }

    fun clear() {
        currentSession.set(null)
    }

    private fun randomToken(): String {
        val bytes = ByteArray(12)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

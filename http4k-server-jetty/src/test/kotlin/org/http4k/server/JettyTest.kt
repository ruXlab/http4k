package org.http4k.server

import org.http4k.client.ApacheClient
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.junit.jupiter.api.Test
import java.io.File

class JettyTest : ServerContract(::Jetty, ApacheClient())

class JettyHttp2Test {

    @Test
    fun `can configure http2`() {
        val server = HttpHandler { Response(OK) }.asServer(Jetty(0,
            http2(0,
                File("src/test/resources/keystore.jks").absolutePath,
                "password")))
        server.start().stop()
    }
}
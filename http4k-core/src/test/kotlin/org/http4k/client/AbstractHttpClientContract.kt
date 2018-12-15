package org.http4k.client

import org.http4k.core.HttpHandler
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.core.cookie.cookies
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.server.Http4kServer
import org.http4k.server.ServerConfig
import org.http4k.server.asServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.Arrays

abstract class AbstractHttpClientContract(private val serverConfig: (Int) -> ServerConfig) {

    private lateinit var server: Http4kServer

    val port: Int
        get() = server.port()

    @BeforeEach
    fun before() {
        val defaultHandler = HttpHandler { request: Request ->
            Response(OK)
                .header("uri", request.uri.toString())
                .header("header", request.header("header"))
                .header("query", request.query("query"))
                .body(request.body)
        }
        val app = routes("/someUri" bind POST to defaultHandler,
            "/cookies/set" bind GET to HttpHandler { req: Request ->
                Response(FOUND).header("Location", "/cookies").cookie(Cookie(req.query("name")!!, req.query("value")!!))
            },
            "/cookies" bind GET to HttpHandler { req: Request ->
                Response(OK).body(req.cookies().joinToString(",") { it.name + "=" + it.value })
            },
            "/empty" bind GET to HttpHandler { Response(OK).body("") },
            "/relative-redirect/{times}" bind GET to HttpHandler { req: Request ->
                val times = req.path("times")?.toInt() ?: 0
                if (times == 0) Response(OK)
                else Response(FOUND).header("Location", "/relative-redirect/${times - 1}")
            },
            "/redirect" bind GET to HttpHandler { Response(FOUND).header("Location", "/someUri").body("") },
            "/stream" bind GET to HttpHandler { Response(OK).body("stream".byteInputStream()) },
            "/delay/{millis}" bind GET to HttpHandler { r: Request ->
                Thread.sleep(r.path("millis")!!.toLong())
                Response(OK)
            },
            "/echo" bind routes(
                DELETE to HttpHandler { Response(OK).body("delete") },
                GET to HttpHandler { request: Request -> Response(OK).body(request.uri.toString()) },
                POST to HttpHandler { request: Request -> Response(OK).body(request.bodyString()) }
            ),
            "/headers" bind HttpHandler { request: Request -> Response(OK).body(request.headers.joinToString(",") { it.first }) },
            "/check-image" bind POST to HttpHandler { request: Request ->
                if (Arrays.equals(testImageBytes(), request.body.payload.array()))
                    Response(OK) else Response(BAD_REQUEST.description("Image content does not match"))
            },
            "/status/{status}" bind GET to HttpHandler { r: Request ->
                val status = Status(r.path("status")!!.toInt(), "")
                Response(status).body("body for status ${status.code}")
            })
        server = app.asServer(serverConfig(0)).start()
    }

    protected fun testImageBytes() = this::class.java.getResourceAsStream("/test.png").readBytes()

    @AfterEach
    fun after() {
        server.stop()
    }
}
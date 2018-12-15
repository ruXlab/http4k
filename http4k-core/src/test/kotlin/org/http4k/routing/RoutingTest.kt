package org.http4k.routing

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import kotlinx.coroutines.runBlocking
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Filter
import org.http4k.core.Method
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.METHOD_NOT_ALLOWED
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.hamkrest.hasStatus
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class RoutingTest {

    @Test
    fun `can bind a verb to a static handler`() = runBlocking {
        val routes = routes(
            "/path1" bind GET to static(),
            "/path2" bind static(),
            "/path3" bind POST to static(), // this will never match, but we're proving that it will fall through
            "/path3/{path:.*}" bind HttpHandler { Response(CREATED) }
        )

        routes(Request(GET, "/path1/index.html")) shouldMatch hasStatus(OK)
        routes(Request(GET, "/path2/index.html")) shouldMatch hasStatus(OK)
        routes(Request(GET, "/path3/index.html")) shouldMatch hasStatus(CREATED)
    }

    @Test
    fun `not found`() = runBlocking {
        val routes = routes("/a/b" bind GET to HttpHandler { Response(OK) })

        val response = routes(Request(GET, "/a/something"))

        assertThat(response.status, equalTo(NOT_FOUND))
    }

    @Test
    @Disabled
    fun `method not allowed`() = runBlocking {
        val routes = routes(
            "/a/{route}" bind GET to HttpHandler { Response(OK).body("matched") }
        )

        val response = routes(Request(POST, "/a/something"))

        assertThat(response.status, equalTo(METHOD_NOT_ALLOWED))
    }

    @Test
    fun `matches uri template and method`() = runBlocking {
        val routes = routes(
            "/a/{route}" bind GET to HttpHandler { Response(OK).body("matched") }
        )

        val response = routes(Request(GET, "/a/something"))

        assertThat(response.bodyString(), equalTo("matched"))
    }

    @Test
    @Disabled("this doesn't have a name so isn't bound...")
    fun `matches empty uri template and method`() = runBlocking {
        val routes = routes(
            "/{.*}" bind GET to HttpHandler { Response(OK).body("matched") }
        )

        val response = routes(Request(GET, "/a/something"))

        assertThat(response.bodyString(), equalTo("matched"))
    }

    @Test
    fun `matches uri template only`() = runBlocking {
        val routes = routes(
            "/a/{route}" bind HttpHandler { Response(OK).body("matched") }
        )

        Method.values().forEach {
            assertThat(routes(Request(it, "/a/something")).bodyString(), equalTo("matched"))
        }
    }

    @Test
    fun `can mix and match Route styles`() = runBlocking {
        val routes = routes(
            "/a" bind GET to HttpHandler { Response(OK).body("matched a") },
            "/b/c" bind routes(
                "/d" bind GET to HttpHandler { Response(OK).body("matched b/c/d") },
                "/e" bind routes(
                    "/f" bind GET to HttpHandler { Response(OK).body("matched b/c/e/f") },
                    "/g" bind routes(
                        GET to HttpHandler { Response(OK).body("matched b/c/e/g/GET") },
                        POST to HttpHandler { Response(OK).body("matched b/c/e/g/POST") }
                    )
                ),
                "/" bind GET to HttpHandler { Response(OK).body("matched b/c") }
            )
        )

        assertThat(routes(Request(GET, "/a")).bodyString(), equalTo("matched a"))
        assertThat(routes(Request(GET, "/b/c/d")).bodyString(), equalTo("matched b/c/d"))
        assertThat(routes(Request(GET, "/b/c")).bodyString(), equalTo("matched b/c"))
        assertThat(routes(Request(GET, "/b/c/e/f")).bodyString(), equalTo("matched b/c/e/f"))
        assertThat(routes(Request(GET, "/b/c/e/g")).bodyString(), equalTo("matched b/c/e/g/GET"))
        assertThat(routes(Request(POST, "/b/c/e/g")).bodyString(), equalTo("matched b/c/e/g/POST"))
        assertThat(routes(Request(GET, "/b/c/e/h")).status, equalTo(NOT_FOUND))
        assertThat(routes(Request(GET, "/b")).status, equalTo(NOT_FOUND))
        assertThat(routes(Request(GET, "/b/e")).status, equalTo(NOT_FOUND))
    }

    @Test
    fun `matches uses first match`() = runBlocking {
        val routes = routes(
            "/a/{route}" bind GET to HttpHandler { Response(OK).body("matched a") },
            "/a/{route}" bind GET to HttpHandler { Response(OK).body("matched b") }
        )

        val response = routes(Request(GET, "/a/something"))

        assertThat(response.bodyString(), equalTo("matched a"))
    }

    @Test
    fun `capture pattern until the end of the path`() = runBlocking {
        val routes = routes(
            "/a/{route:.*}" bind GET to HttpHandler { Response(OK).body(it.path("route")!!) }
        )

        assertThat(routes(Request(GET, "/a/something")).bodyString(), equalTo("something"))
        assertThat(routes(Request(GET, "/a/something/somethingelse")).bodyString(), equalTo("something/somethingelse"))
    }

    @Test
    fun `path parameters are available in request`() = runBlocking {
        val routes = routes(
            "/{a}/{b}/{c}" bind GET to HttpHandler { req: Request -> Response(OK).body("matched ${req.path("a")}, ${req.path("b")}, ${req.path("c")}") }
        )

        val response = routes(Request(GET, "/x/y/z"))

        assertThat(response.bodyString(), equalTo("matched x, y, z"))
    }

    @Test
    fun `matches uri with query`() = runBlocking {
        val routes = routes("/a/b" bind GET to HttpHandler { Response(OK) })

        val response = routes(Request(GET, "/a/b?foo=bar"))

        assertThat(response, equalTo(Response(OK)))
    }

    @Test
    fun `matches regex uri with query`() = runBlocking {
        val handler = routes("/a/{b:.+}" bind GET to HttpHandler { Response(OK).body(it.path("b")!!) })
        assertThat(handler(Request(GET, "/a/foo?bob=rita")).bodyString(), equalTo("foo"))
    }

    @Test
    fun `does not matche request with extra path parts`() = runBlocking {
        val routes = routes("/a" bind GET to HttpHandler { Response(OK) })

        val response = routes(Request(GET, "/a/b"))

        assertThat(response, equalTo(Response(NOT_FOUND)))
    }

    @Test
    fun `can stop matching extra parts`() = runBlocking {
        val routes = routes("/a{$}" bind GET to HttpHandler { Response(OK) })

        val response = routes(Request(GET, "/a/b"))

        assertThat(response, equalTo(Response(NOT_FOUND)))
    }

    @Test
    fun `breaks if trying to access path parameters without header present`() = runBlocking {
        try {
            Request(GET, "/").path("abc")
            fail("Expected exception")
        } catch (e: IllegalStateException) {
            assertThat(e.message, equalTo("Request was not routed, so no uri-template not present"))
        }
    }

    @Test
    fun `can put routes inside of routes`() = runBlocking {
        val subRoutes = routes(
            "/a/{route}" bind GET to HttpHandler { Response(OK).header("header", it.header("header")).body("matched") }
        )

        var count = 0
        val filter = Filter { next ->
            HttpHandler {
                next(it.replaceHeader("header", "value" + count++))
            }
        }

        val app = routes("/prefix" bind filter.then(subRoutes))

        val response = app(Request(GET, "/prefix/a/something"))

        assertThat(response.status, equalTo(OK))
        assertThat(response.header("header"), equalTo("value0"))
        assertThat(response.bodyString(), equalTo("matched"))
    }

    @Test
    fun `group router shortcuts if parent prefix does not match`() = runBlocking {
        val app = routes("/prefix" bind routes(
            "/{.*}" bind GET to HttpHandler { Response(OK).body("matched") }
        ))

        assertThat(app(Request(GET, "/prefix/foo")).status, equalTo(OK))
        assertThat(app(Request(GET, "/prefix/foo/something")).status, equalTo(NOT_FOUND))
        assertThat(app(Request(GET, "/notprefix/a/something")).status, equalTo(NOT_FOUND))
    }

    @Test
    fun `route grouping prefix can contain a dynamic segment`() = runBlocking {
        val subRoutes = routes(
            "/a/{route}" bind GET to HttpHandler { Response(OK).body(it.path("name") + it.path("route")) }
        )

        val app = routes("/{name:\\d+}" bind subRoutes)

        assertThat(app(Request(GET, "/123/a/something")).status, equalTo(OK))
        assertThat(app(Request(GET, "/123/a/something")).bodyString(), equalTo("123something"))
        assertThat(app(Request(GET, "/asd/a/something")).status, equalTo(NOT_FOUND))
    }

    @Test
    fun `can add filter to router`() = runBlocking {
        val changePathFilter = Filter { next ->
            HttpHandler { next(it.uri(it.uri.path("/svc/mybob.xml"))) }
        }
        val handler = "/svc" bind changePathFilter.then(static())
        val req = Request(GET, Uri.of("/svc/notmybob.xml"))
        assertThat(handler(req).status, equalTo(OK))
    }

    @Test
    fun `can add filter to a RoutingHttpHandler`() = runBlocking {
        val changePathFilter = Filter { next ->
            HttpHandler { next(it.uri(it.uri.path("/svc/mybob.xml"))) }
        }
        val handler = changePathFilter.then("/svc" bind static())
        val req = Request(GET, Uri.of("/svc/notmybob.xml"))
        assertThat(handler(req).status, equalTo(OK))
    }

    @Test
    fun `can apply a filter to a RoutingHttpHandler`() = runBlocking {
        val routes = Filter { next -> HttpHandler { next(it.header("name", "value")) } }
            .then { Response(OK).body(it.header("name")!!) }

        val routingHttpHandler = routes(
            "/a/thing" bind GET to routes
        )
        assertThat(routingHttpHandler(Request(GET, "/a/thing")).bodyString(), equalTo("value"))
    }

    @Test
    fun `RoutingHttpHandler with filters also applies when route is not found`() = runBlocking {
        val filter = Filter { next -> HttpHandler { next(it).body("value") } }

        val routingHttpHandler = filter.then(routes(
            "/a/thing" bind GET to HttpHandler { Response(OK) }
        ))

        assertThat(routingHttpHandler(Request(GET, "/not-found")).bodyString(), equalTo("value"))
    }

    @Test
    fun `can apply a filter to a Router`() = runBlocking {
        val routes = Filter { next -> HttpHandler { next(it.header("name", "value")) } }
            .then(routes(
                "/a/thing" bind GET to HttpHandler { Response(OK).body(it.header("name")!!) }
            ))

        assertThat(routes(Request(GET, "/a/thing")).bodyString(), equalTo("value"))
    }
}

package org.http4k.core

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import kotlinx.coroutines.runBlocking
import org.http4k.core.Method.GET
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Uri.Companion.of
import org.junit.jupiter.api.Test

class FilterTest {

    private val echoHeaders = HttpHandler { req: Request -> req.headers.fold(Response(OK)) { memo, next -> memo.header(next.first, next.second) } }

    private val addRequestHeader = Filter { next -> HttpHandler { next(it.header("hello", "world")) } }
    private val addResponseHeader = Filter { next -> HttpHandler { next(it).header("goodbye", "cruel") } }

    @Test
    fun `can manipulate value on way in and out of service`() = runBlocking {
        val svc = addRequestHeader.then(addResponseHeader).then(echoHeaders)
        val response = svc(Request(GET, of("/")))
        assertThat(response.header("hello"), equalTo("world"))
        assertThat(response.header("goodbye"), equalTo("cruel"))
    }

    @Test
    fun `applies in order of chain`() = runBlocking {
        val minus10 = Filter { next -> HttpHandler { next(it.replaceHeader("hello", (it.header("hello")!!.toInt() - 10).toString())) } }
        val double = Filter { next -> HttpHandler { next(it.replaceHeader("hello", (it.header("hello")!!.toInt() * 2).toString())) } }

        val final = double.then(minus10).then(echoHeaders)
        val response = final(Request(GET, of("/")).header("hello", "10"))
        assertThat(response.header("hello"), equalTo("10"))
    }
}
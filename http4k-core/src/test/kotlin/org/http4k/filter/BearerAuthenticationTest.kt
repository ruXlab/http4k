package org.http4k.filter

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import kotlinx.coroutines.runBlocking
import org.http4k.core.Credentials
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.RequestContexts
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.http4k.core.then
import org.http4k.filter.ServerFilters.BearerAuth
import org.http4k.filter.ServerFilters.InitialiseRequestContext
import org.http4k.lens.RequestContextKey
import org.junit.jupiter.api.Test

class BearerAuthenticationTest {
    @Test
    fun fails_to_authenticate() = runBlocking {
        val handler = ServerFilters.BearerAuth("token").then { _: Request -> Response(OK) }
        val response = handler(Request(GET, "/"))
        assertThat(response.status, equalTo(UNAUTHORIZED))
    }

    @Test
    fun authenticate_using_client_extension() = runBlocking {
        val handler = ServerFilters.BearerAuth("token").then { _: Request -> Response(OK) }
        val response = ClientFilters.BearerAuth("token").then(handler)(Request(GET, "/"))
        assertThat(response.status, equalTo(OK))
    }

    @Test
    fun fails_to_authenticate_if_credentials_do_not_match() = runBlocking {
        val handler = ServerFilters.BearerAuth("token").then { _: Request -> Response(OK) }
        val response = ClientFilters.BearerAuth("not token").then(handler)(Request(GET, "/"))
        assertThat(response.status, equalTo(UNAUTHORIZED))
    }

    @Test
    fun populates_request_context_for_later_retrieval() = runBlocking {
        val contexts = RequestContexts()
        val key = RequestContextKey.required<Credentials>(contexts)

        val handler =
            InitialiseRequestContext(contexts)
                .then(BearerAuth(key) { Credentials(it, it) })
                .then { req -> Response(OK).body(key(req).toString()) }

        val response = ClientFilters.BearerAuth("token").then(handler)(Request(GET, "/"))

        assertThat(response.bodyString(), equalTo("Credentials(user=token, password=token)"))
    }
}

package cookbook.monitoring

import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.DebuggingFilters

suspend fun main() {

    val app = HttpHandler { _: Request -> Response(OK).body("hello there you look nice today") }

    val debuggedApp = DebuggingFilters.PrintRequestAndResponse().then(app)

    debuggedApp(Request(Method.GET, "/foobar").header("Accepted", "my-great-content/type"))
}
package guide.modules.resilience

import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadConfig
import kotlinx.coroutines.runBlocking
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.filter.ResilienceFilters
import kotlin.concurrent.thread

suspend fun main() {

    // configure the Bulkhead filter here
    val config = BulkheadConfig.custom()
        .maxConcurrentCalls(5)
        .maxWaitTime(0)
        .build()

    val bulkheading = ResilienceFilters.Bulkheading(Bulkhead.of("bulkhead", config)).then {
        Thread.sleep(100)
        Response(Status.OK)
    }

    // throw a bunch of requests at the filter - only 5 should pass
    (1..10).forEach {
        thread {
            runBlocking {
                println(bulkheading(Request(Method.GET, "/")).status)
            }
        }
    }
}
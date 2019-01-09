package cookbook.using_json

import com.fasterxml.jackson.databind.JsonNode
import org.http4k.core.Body
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.format.Jackson
import org.http4k.format.Jackson.asJsonArray
import org.http4k.format.Jackson.asJsonObject
import org.http4k.format.Jackson.asJsonValue
import org.http4k.format.Jackson.asPrettyJsonString
import org.http4k.format.Jackson.json

suspend fun main() {

    val json = Jackson

    val objectUsingExtensionFunctions =
        listOf(
            "thisIsAString" to "stringValue".asJsonValue(),
            "thisIsANumber" to 12345.asJsonValue(),
            "thisIsAList" to listOf(true.asJsonValue()).asJsonArray()
        ).asJsonObject()

    val objectUsingDirectApi = json.obj(
        "thisIsAString" to json.string("stringValue"),
        "thisIsANumber" to json.number(12345),
        "thisIsAList" to json.array(listOf(json.boolean(true)))
    )

    val objectUsingDslApi: JsonNode = json {
        obj(
            "thisIsAString" to string("stringValue"),
            "thisIsANumber" to number(12345),
            "thisIsAList" to array(listOf(boolean(true)))
        )
    }

    println(objectUsingExtensionFunctions.asPrettyJsonString())

    println(
        Response(OK).with(
            Body.json().toLens() of
                listOf(
                    objectUsingDirectApi,
                    objectUsingDslApi,
                    objectUsingExtensionFunctions
                ).asJsonArray())
    )
}

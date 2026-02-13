package org.multipaz.web

import emotion.react.css
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import js.promise.Promise
import js.promise.await
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import react.FC
import react.dom.html.ReactHTML.button
import web.cssom.*
import web.dom.Element

private val scope = MainScope()

val VerifierApp = FC<MultipazProps> { props ->
    val definition = props.definition
    button {
        css {
            padding = Padding(12.px, 24.px)
            fontSize = 16.px
            backgroundColor = Color("#0066cc")
            color = Color("#ffffff")
            border = None.none
            borderRadius = 6.px
            cursor = Cursor.pointer
            marginTop = 16.px
            disabled {
                backgroundColor = Color("#cccccc")
                cursor = Cursor.default
            }
        }

        +"Verify"

        onClick = {
            scope.launch {
                query(definition)
            }
        }
    }
}

val httpClient = HttpClient(Js)

private suspend fun query(definition: Element) {
    val query = Json.parseToJsonElement(definition.getAttribute("data-dcql")!!)
    val response = httpClient.post("/verifier/light/request") {
        headers {
            contentType(ContentType.Application.Json)
        }
        setBody(buildJsonObject {
            put("dcql", query)
        }.toString())
    }
    if (response.status != HttpStatusCode.OK) {
        throw IllegalStateException("Error creating a DC request")
    }
    val request = Json.parseToJsonElement(response.readRawBytes().decodeToString()).jsonObject
    val sessionId = request["session_id"]!!.jsonPrimitive.content
    val dcRequest = request["dc_request"]
    val data = getCredential(dcRequest.toString())
    val verifierResponse = httpClient.post("/verifier/light/response") {
        headers {
            contentType(ContentType.Application.Json)
        }
        setBody(buildJsonObject {
            put("session_id", sessionId)
            put("dc_response", Json.parseToJsonElement(data))
        }.toString())
    }
    println("Response: ${verifierResponse.readRawBytes().decodeToString()}")
}

private suspend fun getCredential(dcRequest: String): String =
    extractData(processCredentialRequest(dcRequest).await())

private fun processCredentialRequest(dcRequest: String): Promise<Any> = js("""
    navigator.credentials.get({
        digital: {
            requests: [
                {
                    protocol: 'openid4vp-v1-signed',
                    data: JSON.parse(dcRequest)
                }
            ]
        },
        mediation: 'required'
    })
""")

private fun extractData(dcResponse: Any): String = js("""
    JSON.stringify(dcResponse.data)
""")
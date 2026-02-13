package org.multipaz.verifier.request

import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Nint
import org.multipaz.cbor.Simple
import org.multipaz.cbor.Tstr
import org.multipaz.cbor.Uint
import org.multipaz.cbor.addCborArray
import org.multipaz.cbor.buildCborArray
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.JsonWebEncryption
import org.multipaz.mdoc.response.DeviceResponse
import org.multipaz.openid.OpenID4VP
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.cache
import org.multipaz.rpc.handler.InvalidRequestException
import org.multipaz.sdjwt.SdJwt
import org.multipaz.sdjwt.SdJwtKb
import org.multipaz.server.common.getBaseUrl
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.trustmanagement.TrustManagerLocal
import org.multipaz.util.Logger
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64
import org.multipaz.util.toBase64Url
import org.multipaz.verifier.session.RequestedClaim
import org.multipaz.verifier.session.RequestedDocument
import org.multipaz.verifier.session.Session
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

suspend fun makeRequest(call: ApplicationCall) {
    val request = Json.parseToJsonElement(call.receiveText()) as JsonObject
    val dcqlQuery = request["dcql"] as? JsonObject
        ?: throw InvalidRequestException("'dsql' is missing or invalid")
    val transactionData = (request["transaction_data"] as? JsonArray)?.map {
        it.toString().encodeToByteArray().toBase64Url()
    }
    val (sessionId, session) = Session.createSession(
        requestedDocuments = extractRequestedDocuments(dcqlQuery),
        transactionData = transactionData
    )
    val sessionIdentity = session.getIdentity(sessionId)
    val baseUrl = BackendEnvironment.getBaseUrl()
    val host = Url(baseUrl).host
    val dcRequest = OpenID4VP.generateRequest(
        version = OpenID4VP.Version.DRAFT_29,
        nonce = session.nonce,
        origin = baseUrl,
        clientId = "x509_san_dns:$host",
        responseEncryptionKey = session.encryptionPrivateKey.publicKey,
        responseMode = OpenID4VP.ResponseMode.DC_API,
        requestSigningKey = sessionIdentity,
        responseUri = null,
        dclqQuery = dcqlQuery,
        transactionData = transactionData ?: listOf()
    )
    call.respondText(
        contentType = ContentType.Application.Json,
        text = buildJsonObject {
            put("session_id", sessionId)
            putJsonObject("dc_request") {
               putJsonObject("digital") {
                    putJsonArray("requests") {
                        addJsonObject {
                            put("protocol", "openid4vp-v1-signed")
                            put("data", dcRequest)
                        }
                    }
                }
                put("mediation", "required")
            }
        }.toString()
    )
}

suspend fun processResponse(call: ApplicationCall) {
    val request = Json.parseToJsonElement(call.receiveText()) as JsonObject
    val sessionId = (request["session_id"] as? JsonPrimitive)?.content
        ?: throw InvalidRequestException("'session_id' is missing or invalid")
    val dcResponse = request["dc_response"] as? JsonObject
        ?: throw InvalidRequestException("'dc_response' is missing or invalid")
    val dcData = dcResponse["data"] as? JsonObject
        ?: throw InvalidRequestException("'dc_response.data' is missing or invalid")
    val session = Session.getSession(sessionId)
        ?: throw InvalidRequestException("Session '$sessionId' is missing or expired")
    val decryptedResponse = JsonWebEncryption.decrypt(
        dcData["response"]!!.jsonPrimitive.content,
        AsymmetricKey.anonymous(
            privateKey = session.encryptionPrivateKey,
            algorithm = session.encryptionPrivateKey.curve.defaultKeyAgreementAlgorithm
        )
    ).jsonObject
    val baseUrl = BackendEnvironment.getBaseUrl()
    val token = decryptedResponse["vp_token"]!!.jsonObject
    val jwkThumbPrint = session.encryptionPrivateKey.publicKey
        .toJwkThumbprint(Algorithm.SHA256).toByteArray()
    val handoverInfo = Cbor.encode(
        buildCborArray {
            add(baseUrl)
            add(session.nonce)
            add(jwkThumbPrint)
        }
    )
    val handoverInfoDigest = Crypto.digest(Algorithm.SHA256, handoverInfo)
    val mdocSessionTranscript by lazy {
        buildCborArray {
            add(Simple.NULL) // DeviceEngagementBytes
            add(Simple.NULL) // EReaderKeyBytes
            addCborArray {
                add("OpenID4VPDCAPIHandover")
                add(handoverInfoDigest)
            }
        }
    }
    val documentRequests = session.requestedDocuments.associateBy { it.id }
    val content = buildJsonObject {
        for ((id, value) in token) {
            val documentRequest = documentRequests[id]!!
            val cbor = documentRequest.format == "mso_mdoc"
            val responses = value.jsonArray.map { credentialResponse ->
                val responseText = credentialResponse.jsonPrimitive.content
                buildJsonObject {
                    if (cbor) {
                        val decoded = Cbor.decode(responseText.fromBase64Url())
                        processMdocResponse(decoded, mdocSessionTranscript, documentRequest.claims)
                    } else {
                        processSdJwtResponse(responseText, documentRequest.claims)
                    }
                }
            }
            if (documentRequest.multiple) {
                put(id, JsonArray(responses))
            } else {
                put(id, responses.first())
            }
        }
    }

    Session.deleteSession(sessionId)

    call.respondText(
        contentType = ContentType.Application.Json,
        text = buildJsonObject {
            put("content", content)
        }.toString()
    )
}

private suspend fun getTrustManager(): TrustManager =
    BackendEnvironment.cache(TrustManager::class) { configuration, resources ->
        // TODO: load from configuration?
        TrustManagerLocal(EphemeralStorage())
    }

private suspend fun JsonObjectBuilder.processMdocResponse(
    credentialResponse: DataItem,
    mdocSessionTranscript: DataItem,
    requestedClaims: List<RequestedClaim>
) {
    val trustManager = getTrustManager()
    val deviceResponse = DeviceResponse.fromDataItem(credentialResponse)
    try {
        deviceResponse.verify(sessionTranscript = mdocSessionTranscript)
    } catch (err: Exception) {
        Logger.e(TAG, "Device response verification failed", err)
    }
    // TODO: can there be multiple responses when in OpenID4VP?
    val document = deviceResponse.documents.first()
    try {
        val trustResult = trustManager.verify(document.issuerCertChain.certificates)
        put("_trusted", trustResult.isTrusted)
    } catch (e: Throwable) {
        Logger.e(TAG, "Trust verification failed", e)
    }

    for (claim in requestedClaims) {
        if (claim.path.size != 2) {
            // TODO: nested values in mdoc?
            continue
        }
        val issuerSignedItemsMap = document.issuerNamespaces.data[claim.path.first().asTstr]
            ?: continue
        val issuerSignedItem = issuerSignedItemsMap[claim.path.last().asTstr]
            ?: continue
        val jsonItem = when (val item = issuerSignedItem.dataElementValue) {
            is Tstr -> JsonPrimitive(item.asTstr)
            is Bstr -> JsonPrimitive(item.asBstr.toBase64())
            is Nint, is Uint -> JsonPrimitive(item.asNumber)
            Simple.TRUE -> JsonPrimitive(true)
            Simple.FALSE -> JsonPrimitive(false)
            Simple.NULL -> JsonPrimitive(null as String?)
            else -> JsonPrimitive("<unsupported>")
        }
        val id = claim.id ?: claim.path.last().asTstr
        put(id, jsonItem)
    }
}

private suspend fun JsonObjectBuilder.processSdJwtResponse(
    credentialResponse: String,
    requestedClaims: List<RequestedClaim>,
    sessionNonce: String,
    transactionData: OpenID4VP.TransactionData
) {
    val (sdJwt, sdJwtKb) = if (credentialResponse.endsWith("~")) {
        Pair(SdJwt.fromCompactSerialization(credentialResponse), null)
    } else {
        val sdJwtKb = SdJwtKb.fromCompactSerialization(credentialResponse)
        Pair(sdJwtKb.sdJwt, sdJwtKb)
    }
    if (sdJwtKb == null && sdJwt.jwtBody["cnf"] != null) {
        throw InvalidRequestException("`cnf` claim present but we got a SD-JWT, not a SD-JWT+KB")
    }
    val issuerCert = sdJwt.x5c?.certificates?.first()
        ?: throw InvalidRequestException("'x5c' not found")
    val trustManager = getTrustManager()
    val trustResult = trustManager.verify(sdJwt.x5c!!.certificates)
    put("_trusted", trustResult.isTrusted)

    val claimMap = sdJwtKb?.verify(
        issuerKey = issuerCert.ecPublicKey,
        checkNonce = { nonce -> nonce == sessionNonce },
        // TODO: check audience, and creationTime
        checkAudience = { audience -> true },
        checkCreationTime = { creationTime -> true },
    )
        ?: sdJwt.verify(issuerCert.ecPublicKey)

    for (claim in requestedClaims) {
        var value: JsonElement = claimMap
        for (key in claim.path) {
            value = when (key) {
                is Tstr -> value.jsonObject[key.asTstr]!!
                is Uint -> value.jsonArray[key.asNumber.toInt()]
                else -> throw IllegalStateException("Unexpected key in claim path")
            }
        }
        val id = claim.id ?: claim.path.last().asTstr
        put(id, value)
    }
}

private fun extractRequestedDocuments(dcql: JsonObject): List<RequestedDocument> {
    val credentials = dcql["credentials"] as? JsonArray
        ?: throw InvalidRequestException("'credentials' is missing or invalid in dsql")
    return credentials.map { credential ->
        credential as? JsonObject
            ?: throw InvalidRequestException("credential query most be an object")
        val id = credential["id"] as? JsonPrimitive
            ?: throw InvalidRequestException("'id' is missing or invalid")
        val format = credential["format"] as? JsonPrimitive
            ?: throw InvalidRequestException("'format' is missing or invalid")
        val claims = credential["claims"] as? JsonArray
            ?: throw InvalidRequestException("'claims' is missing or invalid")
        RequestedDocument(
            id = id.content,
            format = format.content,
            multiple = (credential["multiple"] as JsonPrimitive?)?.booleanOrNull ?: false,
            claims = claims.map { claim ->
                claim as? JsonObject
                    ?: throw InvalidRequestException("claim is not an object")
                val id = claim["id"] as? JsonPrimitive
                val path = claim["path"] as? JsonArray
                    ?: throw InvalidRequestException("'path' is missing or invalid")
                RequestedClaim(
                    id = id?.content,
                    path = path.map {
                        it as? JsonPrimitive
                            ?: throw InvalidRequestException("path element is not primitive")
                        if (it.isString) {
                            Tstr(it.content)
                        } else if (it.contentOrNull == null) {
                            Simple.NULL
                        } else if (it.long >= 0){
                            Uint(it.long.toULong())
                        } else {
                            throw InvalidRequestException("path element is negative")
                        }
                    }
                )
            }
        )
    }
}

private const val TAG = "verifyCredentials"


package org.multipaz.verifier.session

import io.ktor.http.Url
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.io.bytestring.ByteString
import org.multipaz.asn1.ASN1
import org.multipaz.asn1.ASN1Encoding
import org.multipaz.asn1.ASN1Integer
import org.multipaz.asn1.ASN1Sequence
import org.multipaz.asn1.ASN1TagClass
import org.multipaz.asn1.ASN1TaggedObject
import org.multipaz.asn1.OID
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.crypto.X509KeyUsage
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.getTable
import org.multipaz.server.common.getBaseUrl
import org.multipaz.server.enrollment.ServerIdentity
import org.multipaz.server.enrollment.getServerIdentity
import org.multipaz.storage.StorageTableSpec
import org.multipaz.util.toBase64Url
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@CborSerializable
data class Session(
    val nonce: String,
    val ephemeralPrivateKey: EcPrivateKey,
    val encryptionPrivateKey: EcPrivateKey,
    val requestedDocuments: List<RequestedDocument>
) {
    suspend fun getIdentity(id: String): AsymmetricKey.X509Certified {
        val now = Clock.System.now()
        val validFrom = now.plus(DateTimePeriod(minutes = -10), TimeZone.currentSystemDefault())
        val validUntil = now.plus(DateTimePeriod(minutes = 10), TimeZone.currentSystemDefault())
        val readerKeySubject = "CN=OWF Multipaz Online Verifier Single-Use Reader Key [$id]"

        val readerIdentity = getServerIdentity(ServerIdentity.VERIFIER)
        val host = Url(BackendEnvironment.getBaseUrl()).host
        val cert = readerIdentity.certChain.certificates.first()
        val readerKeyCertificate = X509Cert.Builder(
            publicKey = ephemeralPrivateKey.publicKey,
            signingKey = readerIdentity,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName(readerKeySubject),
            issuer = cert.subject,
            validFrom = validFrom,
            validUntil = validUntil
        )
            .includeSubjectKeyIdentifier()
            .setAuthorityKeyIdentifierToCertificate(cert)
            .setKeyUsage(setOf(X509KeyUsage.DIGITAL_SIGNATURE))
            .addExtension(
                OID.X509_EXTENSION_SUBJECT_ALT_NAME.oid,
                false,
                ASN1.encode(
                    ASN1Sequence(
                        listOf(
                            ASN1TaggedObject(
                                ASN1TagClass.CONTEXT_SPECIFIC,
                                ASN1Encoding.PRIMITIVE,
                                2, // dNSName
                                host.encodeToByteArray()
                            )
                        )
                    )
                )
            )
            .build()

        return AsymmetricKey.X509CertifiedExplicit(
            privateKey = ephemeralPrivateKey,
            certChain = X509CertChain(listOf(readerKeyCertificate) + readerIdentity.certChain.certificates)
        )
    }

    companion object {
        suspend fun createSession(
            requestedDocuments: List<RequestedDocument>,
            transactionData: List<String>?
        ): Pair<String, Session> {
            val session = Session(
                nonce = Random.nextBytes(15).toBase64Url(),
                ephemeralPrivateKey = Crypto.createEcPrivateKey(EcCurve.P256),
                encryptionPrivateKey = Crypto.createEcPrivateKey(EcCurve.P256),
                requestedDocuments = requestedDocuments
            )
            val id = BackendEnvironment.getTable(tableSpec).insert(
                key = null,
                data = ByteString(session.toCbor()),
                expiration = Clock.System.now() + 1.hours
            )
            return Pair(id, session)
        }

        suspend fun getSession(sessionId: String): Session? =
            BackendEnvironment.getTable(tableSpec).get(sessionId)
                ?.let { Session.fromCbor(it.toByteArray()) }

        suspend fun deleteSession(sessionId: String) {
            BackendEnvironment.getTable(tableSpec).delete(sessionId)
        }

        private val tableSpec = StorageTableSpec(
            name = "VerifierLightSessions",
            supportPartitions = false,
            supportExpiration = true
        )
    }
}
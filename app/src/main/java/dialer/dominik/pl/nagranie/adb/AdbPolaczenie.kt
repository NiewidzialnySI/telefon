package dialer.dominik.pl.nagranie.adb

import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateExtensions
import android.sun.security.x509.CertificateIssuerName
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateSubjectName
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.KeyIdentifier
import android.sun.security.x509.PrivateKeyUsageExtension
import android.sun.security.x509.SubjectKeyIdentifierExtension
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import org.conscrypt.Conscrypt
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Random
import java.util.concurrent.atomic.AtomicReference

/**
 * Własny klient ADB (Wireless debugging) — klucz RSA w filesDir/adb/.
 * Po jednorazowym [pair] [connectTls] działa do unieważnienia / reinstalacji.
 */
class AdbPolaczenie private constructor(ctx: Context) : AbsAdbConnectionManager() {

    private val dir = File(ctx.applicationContext.filesDir, "adb").also { it.mkdirs() }
    private val keyFile = File(dir, "adbkey")
    private val certFile = File(dir, "adbkey.crt")
    private val privateKey: PrivateKey
    private val certificate: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        upewnijConscrypt()
        val loaded = zaladuj()
        if (loaded != null) {
            privateKey = loaded.first
            certificate = loaded.second
        } else {
            val gen = wygeneruj()
            privateKey = gen.first
            certificate = gen.second
            zapisz(privateKey, certificate)
        }
    }

    override fun getPrivateKey(): PrivateKey = privateKey
    override fun getCertificate(): Certificate = certificate
    override fun getDeviceName(): String = "TelefonDialer"

    fun czyKluczGotowy(): Boolean = keyFile.isFile && certFile.isFile

    companion object {
        private const val TAG = "AdbPolaczenie"
        @Volatile private var instancja: AdbPolaczenie? = null
        private val keepalive = AtomicReference<AdbStream?>(null)

        fun get(ctx: Context): AdbPolaczenie {
            instancja?.let { return it }
            synchronized(this) {
                instancja?.let { return it }
                return AdbPolaczenie(ctx.applicationContext).also { instancja = it }
            }
        }

        fun upewnijConscrypt() {
            if (Security.getProvider("Conscrypt") == null) {
                runCatching {
                    Security.insertProviderAt(Conscrypt.newProvider(), 1)
                }.onFailure { Log.w(TAG, "Conscrypt", it) }
            }
        }

        fun trzymanyStream(): AdbStream? = keepalive.get()

        fun ustawKeepalive(stream: AdbStream?) {
            keepalive.getAndSet(stream)?.runCatching { close() }
        }
    }

    private fun zaladuj(): Pair<PrivateKey, Certificate>? = runCatching {
        if (!keyFile.isFile || !certFile.isFile) return null
        val keyBytes = Base64.decode(keyFile.readText().trim(), Base64.DEFAULT)
        val key = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(FileInputStream(certFile))
        key to cert
    }.getOrNull()

    private fun zapisz(key: PrivateKey, cert: Certificate) {
        keyFile.writeText(Base64.encodeToString(key.encoded, Base64.NO_WRAP))
        FileOutputStream(certFile).use { it.write(cert.encoded) }
    }

    private fun wygeneruj(): Pair<PrivateKey, Certificate> {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom.getInstance("SHA1PRNG"))
        val pair = kpg.generateKeyPair()
        val publicKey = pair.public
        val privateKey = pair.private
        val subject = "CN=TelefonDialer"
        val algorithmName = "SHA512withRSA"
        val expiry = System.currentTimeMillis() + 86400_000L * 3650
        val extensions = CertificateExtensions()
        extensions.set(
            "SubjectKeyIdentifier",
            SubjectKeyIdentifierExtension(KeyIdentifier(publicKey).identifier),
        )
        val x500 = X500Name(subject)
        val notBefore = Date()
        val notAfter = Date(expiry)
        extensions.set("PrivateKeyUsage", PrivateKeyUsageExtension(notBefore, notAfter))
        val validity = CertificateValidity(notBefore, notAfter)
        val info = X509CertInfo()
        info.set("version", CertificateVersion(2))
        info.set("serialNumber", CertificateSerialNumber(Random().nextInt() and Int.MAX_VALUE))
        info.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algorithmName)))
        info.set("subject", CertificateSubjectName(x500))
        info.set("key", CertificateX509Key(publicKey))
        info.set("validity", validity)
        info.set("issuer", CertificateIssuerName(x500))
        info.set("extensions", extensions)
        val impl = X509CertImpl(info)
        impl.sign(privateKey, algorithmName)
        return privateKey to impl
    }
}

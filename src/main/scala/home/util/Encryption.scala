package home.util

import java.security.MessageDigest

import com.typesafe.config.Config
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

import scala.util.Try

object Encryption {
    private val BASE64DECODER: java.util.Base64.Decoder = java.util.Base64.getDecoder
    private val BASE64ENCODER: java.util.Base64.Encoder = java.util.Base64.getEncoder
    private val TRANSFORMATION: String = "AES/ECB/PKCS5Padding"
    private val SALT: String = "_"

    def encrypt(key: String, value: String): String = {
        val cipher: Cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyToSpec(key))
        BASE64ENCODER.encodeToString(cipher.doFinal(value.getBytes("UTF-8")))
    }

    /**
      * @return Failure for bad keys
      */
    def decrypt(key: String, encryptedValue: String): Try[String] = Try {
        val cipher: Cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyToSpec(key))
        new String(cipher.doFinal(BASE64DECODER.decode(encryptedValue)))
    }

    private def keyToSpec(key: String): SecretKeySpec = {
        var keyBytes: Array[Byte] = (SALT + key).getBytes("UTF-8")
        val sha: MessageDigest = MessageDigest.getInstance("SHA-1")
        keyBytes = sha.digest(keyBytes)
        keyBytes = java.util.Arrays.copyOf(keyBytes, 16)
        new SecretKeySpec(keyBytes, "AES")
    }

    /**
      * The key used to decrypt things can be provided in a system environment property (e.g. export key=XXX) or in a
      * system property (e.g. java -Dauthorization.key=XXX ...).
      */
    def getKey(config: Config): String = sys.env.get("key") match {
        case Some(key) => key
        case _ => config.getString("authorization.key")
    }
}
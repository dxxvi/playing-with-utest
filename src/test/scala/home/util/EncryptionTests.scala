package home.util

import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

import scala.util.{Failure, Success}

class EncryptionTests {
    @Test
    def testEncryptDecrypt() {
        val key = "xxxx"
        val stringToEncrypt = "xxx"

        val encryptedString = Encryption.encrypt(key, stringToEncrypt)
        Encryption.decrypt(key, encryptedString) match {
            case Success(decryptedString) => assertEquals(stringToEncrypt, decryptedString)
            case Failure(ex) => fail(ex)
        }
    }
}

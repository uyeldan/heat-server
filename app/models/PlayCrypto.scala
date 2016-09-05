/*
 * The MIT License (MIT)
 * Copyright (c) 2016 Heat Ledger Ltd.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * */
package models

import heat.util.Convert
import heat.crypto.Crypto
import java.security.SecureRandom

object PlayCrypto {

  case class EncryptedData(
    data: Array[Byte],
    nonce: Array[Byte]
  )

  def signMessage(message: String, secretPhrase: String): Option[String] = {
    try {
      val messageBytes = Convert.toBytes(message);
      if (messageBytes != null) {
        val signature = Crypto.sign(messageBytes, secretPhrase);
        return Some(Convert.toHexString(signature))
      }
    }
    catch {
      case e: NumberFormatException => None
    }
    None
  }

  def publicKeyToAccountId(publicKey: Array[Byte]): Long = {
    val publicKeyHash = Crypto.sha256().digest(publicKey);
    Convert.fullHashToId(publicKeyHash);
  }

  def secretPhraseToAccountId(secretPhrase: String): Long = {
    publicKeyToAccountId(Crypto.getPublicKey(secretPhrase))
  }

  def secretPhraseToPublicKey(secretPhrase: String): Array[Byte] = {
    Crypto.getPublicKey(secretPhrase)
  }

  def encryptToRecipient(publicKey: Array[Byte],secretPhrase: String, message: Array[Byte]): EncryptedData = {
    val privateKey = Crypto.getPrivateKey(secretPhrase)
    val nonce = new Array[Byte](32)
    val secureRandom = new SecureRandom()
    secureRandom.nextBytes(nonce);
    val data = Crypto.aesEncrypt(message, privateKey, publicKey, nonce);
    EncryptedData(data, nonce)
  }
}
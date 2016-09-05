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

import scala.concurrent.Future
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import heat.util.Convert

object FIMK {

  val FIMK_FEE = "10000000"
  val FIMK_DEADLINE = "1400"

  def sendPrivateMessage(ws: WSClient, recipientPublicKey: String, message: String) : Future[Option[String]] = {
    val recipientPublicKeyBytes = Convert.parseHexString(recipientPublicKey)
    val account = java.lang.Long.toUnsignedString(PlayCrypto.publicKeyToAccountId(recipientPublicKeyBytes))
    val encrypted = encryptMessage(recipientPublicKeyBytes, message)
    val senderPublicKeyBytes = PlayCrypto.secretPhraseToPublicKey(Config.fimkVerifierSecret)

    val rawTransaction = createTransaction(ws, "sendMessage", Map(
      "recipient" -> Seq(account),
      "recipientPublicKey" -> Seq(recipientPublicKey),
      "publicKey" -> Seq(Convert.toHexString(senderPublicKeyBytes)),
      "feeNQT" -> Seq(FIMK_FEE),
      "deadline" -> Seq(FIMK_DEADLINE),
      "encryptedMessageData" -> Seq(Convert.toHexString(encrypted.data)),
      "encryptedMessageNonce" -> Seq(Convert.toHexString(encrypted.nonce)),
      "messageToEncryptIsText" -> Seq("true")
    ))

    rawTransaction.flatMap { response =>
      response match {
        case Some(json) => {
          val unsignedTransactionBytes = Convert.parseHexString((json \ "unsignedTransactionBytes").as[String])
          val signature = heat.crypto.Crypto.sign(unsignedTransactionBytes, Config.fimkVerifierSecret);
          val unsignedTransactionJson = (json \ "transactionJSON").get.as[JsObject]
          val signedTransactionJson = unsignedTransactionJson + ("signature" -> Json.toJson(Convert.toHexString(signature)))
          val broadcastedTransaction = broadcastTransaction(ws, Map(
              "transactionJSON" -> Seq(signedTransactionJson.toString())
          ))

          broadcastedTransaction.flatMap { response =>
            Future.successful(Some("Hello"))
          }
        }
        case None => Future.successful(None)
      }
    }
  }

  private def encryptMessage(publicKey: Array[Byte], message: String) : PlayCrypto.EncryptedData = {
    val messageBytes = Convert.toBytes(message)
    PlayCrypto.encryptToRecipient(publicKey, Config.fimkVerifierSecret, messageBytes)
  }

  private def createTransaction(ws: WSClient, requestType: String, data: Map[String, Seq[String]]) : Future[Option[JsValue]] = {
    val url = s"${Config.fimkVerifierUrl}:${Config.fimkVerifierPort}/nxt?requestType=$requestType"
    ws.url(url).post(data).flatMap { response =>
      (response.json \ "unsignedTransactionBytes").asOpt[String] match {
        case Some(unsignedBytes) => Future.successful(Some(response.json))
        case None => Future.successful(None)
      }
    }
  }

  private def broadcastTransaction(ws: WSClient, data: Map[String, Seq[String]]) : Future[Option[String]] = {
    val url = s"${Config.fimkVerifierUrl}:${Config.fimkVerifierPort}/nxt?requestType=broadcastTransaction"
    ws.url(url).post(data).flatMap { response =>
      (response.json \ "transaction").asOpt[String] match {
        case Some(transaction) => Future.successful(Some(transaction))
        case None => Future.successful(None)
      }
    }
  }
}
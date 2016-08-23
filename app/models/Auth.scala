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

import heat.crypto.Crypto
import heat.util.Convert
import play.api.libs.functional.syntax.functionalCanBuildApplicative
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.JsPath
import play.api.libs.json.JsValue
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.Reads

object Auth {
  
  case class AuthRequest(
    accountRS: String,
    signature: String,
    timestamp: Long,
    publicKey: String)

  implicit val authRequestReads: Reads[AuthRequest] = (
    (JsPath \ "accountRS").read[String] and
    (JsPath \ "signature").read[String] and
    (JsPath \ "timestamp").read[Long] and 
    (JsPath \ "publicKey").read[String])(AuthRequest.apply _)  
  
  def getAccountId(request: JsValue): Option[Long] = {
    (request \ "auth").validate[AuthRequest].fold(
      error => None,
      req => {
        val accountId = Convert.parseAccountId(req.accountRS)
        ReplicatorPublicKey.find(accountId) match {
          case Some(publicKey) => if (validAuthToken(req, publicKey)) Some(accountId) else None
          case None => if (validAuthToken(req, Convert.parseHexString(req.publicKey))) Some(accountId) else None
        }
      }
    )
  }  
  
  def getAccountId(publicKey: String, timestamp: Long, signature: String): Option[Long] = {
    val publicKeyBytes = Convert.parseHexString(publicKey)
    val accountId = PlayCrypto.publicKeyToAccountId(publicKeyBytes)
    val accountRS = Convert.rsAccount(accountId)
    if (validAuthToken(accountRS, timestamp, signature, publicKeyBytes)) {
      Some(accountId)
    }
    else None
  }  
  
  def validAuthToken(accountRS: String, timestamp: Long, signature: String, publicKey: Array[Byte]): Boolean = {
    val baseMessage = accountRS.concat(timestamp.toString())
    val hexMessage = Convert.toHexString(Convert.toBytes(baseMessage))
    val sig = Convert.parseHexString(signature)
    val messageBytes = Convert.parseHexString(hexMessage)
    Crypto.verify(sig, messageBytes, publicKey, false)
  }  
  
  def validAuthToken(req: AuthRequest, publicKey: Array[Byte]): Boolean = {
    val baseMessage = req.accountRS.concat(req.timestamp.toString())
    val hexMessage = Convert.toHexString(Convert.toBytes(baseMessage))
    val signature = Convert.parseHexString(req.signature)
    val messageBytes = Convert.parseHexString(hexMessage)
    Crypto.verify(signature, messageBytes, publicKey, false)
  }  
}
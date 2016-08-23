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

import java.sql.SQLException

import play.api.Logger
import play.api.Play.current
import play.api.db.DB
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import heat.util.Convert

object ReplicatorPublicKey {
  
  def find(accountId: Long): Option[Array[Byte]] = {
    DB.withConnection { conn =>
      try {
        val pstmt = conn.prepareStatement("SELECT public_key FROM replicator_public_key WHERE account_id = ? LIMIT 1")
        pstmt.setLong(1, accountId)
        val result = pstmt.executeQuery()
        if (result.next()) {
          val publicKeyBytes = result.getBytes("public_key")
          if (PlayCrypto.publicKeyToAccountId(publicKeyBytes).equals(accountId)) {
            return Some(publicKeyBytes)
          }
          else {
            Logger.info("Weirdness, public key in db does not match account id")
          }
        }
      } catch {
        case e:SQLException => Logger.error("PublicKey.find error", e)
        case e:Exception => Logger.error("PublicKey.find error", e)
      }
    }
    None
  }
  
  case class PublicKeyEntry(
    accountId: Long,
    publicKey: Array[Byte]
  )  
  
  def toJson(model: PublicKeyEntry): JsValue = {
    Json.obj(
      "account" -> java.lang.Long.toUnsignedString(model.accountId),
      "accountRS" -> Convert.rsAccount(model.accountId),
      "publicKey" -> Convert.toHexString(model.publicKey)
    )
  }
  
  def list(): Option[List[PublicKeyEntry]] = {
    DB.withConnection { conn =>
      try {
        val pstmt = conn.prepareStatement("SELECT public_key, account_id FROM replicator_public_key")
        val result = pstmt.executeQuery()
        val iter = new Iterator[PublicKeyEntry] {
          def hasNext = result.next()
          def next() = {
            PublicKeyEntry(
              result.getLong("account_id"),
              result.getBytes("public_key")
            )
          }
        }
        return Some(iter.toList)
      } catch {
        case e:SQLException => Logger.error("PublicKey.list error", e)
        case e:Exception => Logger.error("PublicKey.list error", e)
      }
    }
    None
  }
}
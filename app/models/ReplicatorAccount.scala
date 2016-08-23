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

object ReplicatorAccount {
  
  def find(accountId: Long): Option[AccountEntry] = {
    val sql = """
      SELECT 
        a.account_id,
        a.public_key,
        b.identifier AS email,
        c.name
      FROM 
        replicator_public_key a
        LEFT OUTER JOIN replicator_identifier b
          ON a.account_id=b.account_id
          AND b.db_id = (
            SELECT b1.db_id FROM replicator_identifier b1
            WHERE b1.account_id = a.account_id
            ORDER BY b1.db_id DESC LIMIT 1
          )
        LEFT OUTER JOIN replicator_user c
          ON a.account_id=c.account_id
      WHERE 
        a.account_id = ? LIMIT 1
    """
    
    DB.withConnection { conn =>
      try {
        val pstmt = conn.prepareStatement(sql)
        pstmt.setLong(1, accountId)
        val result = pstmt.executeQuery()
        if (result.next()) {
          val publicKeyBytes = result.getBytes("public_key")
          if (PlayCrypto.publicKeyToAccountId(publicKeyBytes).equals(accountId)) {
            return Some(AccountEntry(
              result.getLong("account_id"),
              result.getBytes("public_key"),
              result.getString("email"),
              result.getString("name")
            ))
          }
          else Logger.info("Weirdness, public key in db does not match account id")
        }
      } catch {
        case e:SQLException => Logger.error("Account.find error", e)
        case e:Exception => Logger.error("Account.find error", e)
      }
    }
    None
  }
  
  case class AccountEntry(
    accountId: Long,
    publicKey: Array[Byte],
    email: String,
    name: String
  )  
  
  def toJson(model: AccountEntry): JsValue = {
    Json.obj(
      "account" -> java.lang.Long.toUnsignedString(model.accountId),
      "accountRS" -> Convert.rsAccount(model.accountId),
      "publicKey" -> Convert.toHexString(model.publicKey),
      "accountName" -> model.name,
      "accountEmail" -> model.email
    )
  }
 
}
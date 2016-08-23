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

object ReplicatorIdentifier {
  
  case class Model(
    accountIdentifier: String,
    accountId: Long,
    accountColorId: Long ,
    accountName: String,
    accountPublicKey: Array[Byte]
  )
  
  def toJson(model: Model): JsValue = {
    Json.obj(
      "accountName" -> model.accountName,
      "accountColorId" -> java.lang.Long.toUnsignedString(model.accountColorId),
      "accountColorName" -> "NOT YET IMPLEMENTED",
      "accountEmail" -> model.accountIdentifier,
      "account" -> java.lang.Long.toUnsignedString(model.accountId),
      "accountRS" -> Convert.rsAccount(model.accountId),
      "accountPublicKey" -> Convert.toHexString(model.accountPublicKey)
    )
  }    
  
  def find(query: String, requirePublicKey: Boolean, accountColorId: Long, firstIndex: Int, lastIndex: Int): Option[List[ReplicatorIdentifier.Model]] = {
    DB.withConnection { conn =>
      try {
        val pstmt = conn.prepareStatement(
          s"""
          SELECT
            a.account_id,
            a.account_color_id,
            a.identifier,
            b.public_key,
            c.name
          FROM
            replicator_identifier a
            LEFT OUTER JOIN replicator_public_key b
              ON a.account_id=b.account_id
            LEFT OUTER JOIN replicator_user c
              ON a.account_id=c.account_id
          WHERE a.account_color_id = ? AND a.identifier LIKE ?
          ${if (requirePublicKey) " AND b.public_key IS NOT NULL "}
          ${DBUtils.limitsClause(firstIndex, lastIndex)}
          """
        )
        pstmt.setLong(1, accountColorId)
        pstmt.setString(2, s"%${query.toLowerCase()}%")
        DBUtils.setLimits(3, pstmt, firstIndex, lastIndex)
        
        val result = pstmt.executeQuery()
        val iter = new Iterator[Model] {
          def hasNext = result.next()
          def next() = {
            Model(
              result.getString("identifier"),
              result.getLong("account_id"),
              result.getLong("account_color_id"),
              result.getString("name"),
              result.getBytes("public_key")
            )
          }
        }
        return Some(iter.toList)
      } catch {
        case e:SQLException => Logger.error("ReplicatorIdentifier.find error", e)
        case e:Exception => Logger.error("ReplicatorIdentifier.find error", e)
      }
    }
    None
  }
}
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

import play.api._
import play.api.Play.current
import play.api.db._
import play.api.libs.json._
import play.api.libs.functional.syntax._

import java.sql.SQLException
import java.sql.PreparedStatement
import java.sql.Connection
import heat.util.Convert

object ReplicatorPayment {
  
  case class Model(
    transactionId: Long,
    recipientId: Long,
    recipientPublicKey: Array[Byte],
    recipientIdentifier: String,
    senderId: Long,
    senderPublicKey: Array[Byte],
    senderIdentifier: String,
    amount: Long,
    fee: Long,
    timestamp: Int,
    message: ReplicatorMessage.Model,
    height: Int,
    transactionIdex: Int,
    confirmed: Boolean
  )
  
  val sortColumns = Set("sender_id", "recipient_id", "timestamp")    
  
  def toJson(model: Model): JsValue = {
    Json.obj(
      "transactionId" -> java.lang.Long.toUnsignedString(model.transactionId),
      "recipientRS" -> Convert.rsAccount(model.recipientId),
      "recipientPublicKey" -> Convert.toHexString(model.recipientPublicKey),
      "recipientIdentifier" -> model.recipientIdentifier,
      "senderRS" -> Convert.rsAccount(model.senderId),
      "senderPublicKey" -> Convert.toHexString(model.senderPublicKey),
      "senderIdentifier" -> model.senderIdentifier,
      "amount" -> String.valueOf(model.amount),
      "fee" -> String.valueOf(model.fee),
      "timestamp" -> model.timestamp,
      "message" -> (if (model.message!=null) ReplicatorMessage.toJson(model.message) else false),
      "height" -> model.height,
      "transactionIdex" -> model.transactionIdex,
      "confirmed" -> model.confirmed
    )
  }  
  
  def find(conn: Connection, transactionId: Long, includeMessage: Boolean): Option[Model] = {
    val sql = s"""
      SELECT 
        a.transaction_id,
        a.recipient_id, 
        b.public_key AS recipient_public_key,
        d.identifier AS recipient_identifier,    
        a.sender_id,
        c.public_key AS sender_public_key,
        e.identifier AS sender_identifier,
        a.amount,
        a.fee,
        a.timestamp,
        a.transaction_index,
        a.has_message,
        a.height,
        a.confirmed
      FROM
        replicator_payment a
          LEFT OUTER JOIN replicator_public_key b
            ON a.recipient_id=b.account_id
          LEFT OUTER JOIN replicator_public_key c
            ON a.sender_id=c.account_id
          LEFT OUTER JOIN replicator_identifier d
            ON a.recipient_id=d.account_id
            AND d.db_id = (
              SELECT d1.db_id FROM replicator_identifier d1
              WHERE d1.account_id = a.recipient_id
              ORDER BY d1.db_id DESC LIMIT 1
            )
          LEFT OUTER JOIN replicator_identifier e
            ON a.sender_id=e.account_id
            AND e.db_id = (
              SELECT e1.db_id FROM replicator_identifier e1
              WHERE e1.account_id = a.sender_id
              ORDER BY e1.db_id DESC LIMIT 1
            )
      WHERE a.transaction_id = ? LIMIT 1
      """      
    
    try {
      val pstmt = conn.prepareStatement(sql)
      pstmt.setLong(1, transactionId)
      val result = pstmt.executeQuery()
      if (result.next()) {
        val message = if (includeMessage && result.getBoolean("has_message")) {
          ReplicatorMessage.find(result.getLong("transaction_id")) match {
            case Some(msg) => msg
            case None => null
          }
        } else null            
        return Some(Model(
          result.getLong("transaction_id"),
          result.getLong("recipient_id"),
          result.getBytes("recipient_public_key"),
          result.getString("recipient_identifier"),
          result.getLong("sender_id"),
          result.getBytes("sender_public_key"),
          result.getString("sender_identifier"),
          result.getLong("amount"),
          result.getLong("fee"),
          result.getInt("timestamp"),
          message,
          result.getInt("height"),
          result.getInt("transaction_index"),
          result.getBoolean("confirmed")
        ))        
      }
    } catch {
      case e:SQLException => Logger.error("ReplicatePayment.list error", e)
      case e:Exception => Logger.error("ReplicatePayment.list error", e)
    }
    None
  }
  
  /* Lists all payments from/to either a specific account or if you pass accountId=0
   * for all accounts. */
  def list(accountId: Long, sortColumn: String, sortAsc: Boolean, 
      firstIndex: Int, lastIndex: Int): Option[List[Model]] = {
    
    if (!sortColumns.contains(sortColumn)) {
      Logger.info("Weirdness, ReplicatePayment.list called with invalid sortColumn: "+sortColumn)
      return None
    } 
    
    val sql = if (accountId == 0) {
      s"""
      SELECT 
        a.transaction_id,
        a.recipient_id, 
        b.public_key AS recipient_public_key,
        d.identifier AS recipient_identifier,    
        a.sender_id,
        c.public_key AS sender_public_key,
        e.identifier AS sender_identifier,
        a.amount,
        a.fee,
        a.timestamp,
        a.transaction_index,
        a.has_message,
        a.height,
        a.confirmed
      FROM
        replicator_payment a
          LEFT OUTER JOIN replicator_public_key b
            ON a.recipient_id=b.account_id
          LEFT OUTER JOIN replicator_public_key c
            ON a.sender_id=c.account_id
          LEFT OUTER JOIN replicator_identifier d
            ON a.recipient_id=d.account_id
            AND d.db_id = (
              SELECT d1.db_id FROM replicator_identifier d1
              WHERE d1.account_id = a.recipient_id
              ORDER BY d1.db_id DESC LIMIT 1
            )
          LEFT OUTER JOIN replicator_identifier e
            ON a.sender_id=e.account_id
            AND e.db_id = (
              SELECT e1.db_id FROM replicator_identifier e1
              WHERE e1.account_id = a.sender_id
              ORDER BY e1.db_id DESC LIMIT 1
            )
      ORDER BY $sortColumn ${if (sortAsc) "ASC" else "DESC"}
      ${DBUtils.limitsClause(firstIndex, lastIndex)}
      """      
    } else {
      s"""
      SELECT 
        a.transaction_id,
        a.recipient_id, 
        b.public_key AS recipient_public_key,
        d.identifier AS recipient_identifier,    
        a.sender_id,
        c.public_key AS sender_public_key,
        e.identifier AS sender_identifier,
        a.amount,
        a.fee,
        a.timestamp,
        a.transaction_index,
        a.has_message,
        a.height,
        a.confirmed
      FROM
        replicator_payment a
          LEFT OUTER JOIN replicator_public_key b
            ON a.recipient_id=b.account_id
          LEFT OUTER JOIN replicator_public_key c
            ON a.sender_id=c.account_id
          LEFT OUTER JOIN replicator_identifier d
            ON a.recipient_id=d.account_id
            AND d.db_id = (
              SELECT d1.db_id FROM replicator_identifier d1
              WHERE d1.account_id = a.recipient_id
              ORDER BY d1.db_id DESC LIMIT 1
            )
          LEFT OUTER JOIN replicator_identifier e
            ON a.sender_id=e.account_id
            AND e.db_id = (
              SELECT e1.db_id FROM replicator_identifier e1
              WHERE e1.account_id = a.sender_id
              ORDER BY e1.db_id DESC LIMIT 1
            )
      WHERE a.recipient_id = ? AND a.sender_id <> ?
      UNION ALL
      SELECT 
        a.transaction_id,
        a.recipient_id, 
        b.public_key AS recipient_public_key,
        d.identifier AS recipient_identifier,    
        a.sender_id,
        c.public_key AS sender_public_key,
        e.identifier AS sender_identifier,
        a.amount,
        a.fee,
        a.timestamp,
        a.transaction_index,
        a.has_message,
        a.height,
        a.confirmed
      FROM
        replicator_payment a
          LEFT OUTER JOIN replicator_public_key b
            ON a.recipient_id=b.account_id
          LEFT OUTER JOIN replicator_public_key c
            ON a.sender_id=c.account_id
          LEFT OUTER JOIN replicator_identifier d
            ON a.recipient_id=d.account_id
            AND d.db_id = (
              SELECT d1.db_id FROM replicator_identifier d1
              WHERE d1.account_id = a.recipient_id
              ORDER BY d1.db_id DESC LIMIT 1
            )
          LEFT OUTER JOIN replicator_identifier e
            ON a.sender_id=e.account_id
            AND e.db_id = (
              SELECT e1.db_id FROM replicator_identifier e1
              WHERE e1.account_id = a.sender_id
              ORDER BY e1.db_id DESC LIMIT 1
            )
      WHERE a.sender_id = ?
      ORDER BY $sortColumn ${if (sortAsc) "ASC" else "DESC"}
      ${DBUtils.limitsClause(firstIndex, lastIndex)}
      """
    }
    
    DB.withConnection { conn =>
      try {
        val pstmt = conn.prepareStatement(sql)
        if (accountId == 0) {
          DBUtils.setLimits(1, pstmt, firstIndex, lastIndex)
        }
        else {
          pstmt.setLong(1, accountId)
          pstmt.setLong(2, accountId)
          pstmt.setLong(3, accountId)
          DBUtils.setLimits(4, pstmt, firstIndex, lastIndex)
        }
        val result = pstmt.executeQuery()
        val iter = new Iterator[Model] {
          def hasNext = result.next()
          def next() = {
            val message = if (result.getBoolean("has_message")) {
              ReplicatorMessage.find(result.getLong("transaction_id")) match {
                case Some(msg) => msg
                case None => null
              }
            } else null            
            Model(
              result.getLong("transaction_id"),
              result.getLong("recipient_id"),
              result.getBytes("recipient_public_key"),
              result.getString("recipient_identifier"),
              result.getLong("sender_id"),
              result.getBytes("sender_public_key"),
              result.getString("sender_identifier"),
              result.getLong("amount"),
              result.getLong("fee"),
              result.getInt("timestamp"),
              message,
              result.getInt("height"),
              result.getInt("transaction_index"),
              result.getBoolean("confirmed")
            )
          }
        }
        return Some(iter.toList)
      } catch {
        case e:SQLException => Logger.error("ReplicatePayment.list error", e)
        case e:Exception => Logger.error("ReplicatePayment.list error", e)
      }
    }
    None
  }
  
  def count(accountId: Long): Option[Int] = {
    DB.withConnection { conn =>
      try {
        val pstmt = conn.prepareStatement(
          s"""
          SELECT COUNT(*) FROM replicator_payment
          ${ if (accountId != 0) " WHERE recipient_id = ? OR sender_id = ?" else "" } 
          """
        )
        if (accountId != 0) {
          pstmt.setLong(1, accountId)
          pstmt.setLong(2, accountId)
        }
        val result = pstmt.executeQuery()
        if (result.next()) {
          return Some(result.getInt(1))
        }
        return Some(0)
      } catch {
        case e:SQLException => Logger.error("ReplicatePayment.count error", e)
        case e:Exception => Logger.error("ReplicatePayment.count error", e)
      }
    }
    None     
  } 
}
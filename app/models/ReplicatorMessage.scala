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

import heat.util.Convert
import play.api.Logger
import play.api.Play.current
import play.api.db.DB
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import java.sql.Statement
import java.sql.PreparedStatement
import java.sql.Connection
import actors.PushTopics

object ReplicatorMessage {
  
  object Masks {
    val READ = 1;
    val TRASHED = 2;    
    val REPLIED = 4;
    val STARRED = 8;
  }  
  
  case class Model(
    var id: Long,                  /* transaction_id or auto increment id */
    unread: Boolean,
    recipientStatus: Int,
    senderStatus: Int,    
    var blockchain: Boolean,       /* True in case of blockchain message */
    senderId: Long,
    senderName: String,
    senderEmail: String,
    senderPublicKey: Array[Byte],
    recipientId: Long,
    recipientName: String,
    recipientEmail: String,
    recipientPublicKey: Array[Byte],
    timestamp: Int,
    isText: Boolean, 
    data: Array[Byte],
    nonce: Array[Byte],
    payment: ReplicatorPayment.Model,
    var confirmed: Boolean
  )
  
  val sortColumns = Set("timestamp","sender_id","recipient_id","default","unread")

  def create(senderId: Long, recipientId: Long, isText: Boolean, data: String, nonce: String): Model = {
    val timestamp: Int = Convert.toEpochTime(System.currentTimeMillis)
    val dataBytes = Convert.parseHexString(data)
    val nonceBytes = Convert.parseHexString(nonce)
    Model(0, true, 0, 0, false, senderId, null, null, null, recipientId, null, null, null, timestamp, isText, dataBytes, nonceBytes, null, true)
  }  
  
  def toJson(model: Model): JsValue = {
    val obj = Json.obj(
      "id" -> java.lang.Long.toUnsignedString(model.id),
      "unread" -> model.unread,
      "recipientStatus" -> model.recipientStatus,
      "senderStatus" -> model.senderStatus,
      "blockchain" -> model.blockchain,
      "senderRS" -> Convert.rsAccount(model.senderId),
      "senderName" -> model.senderName,
      "senderEmail" -> model.senderEmail,
      "senderPublicKey" -> Convert.toHexString(model.senderPublicKey),
      "recipientRS" -> Convert.rsAccount(model.recipientId),
      "recipientName" -> model.recipientName,
      "recipientEmail" -> model.recipientEmail,
      "recipientPublicKey" -> Convert.toHexString(model.recipientPublicKey),
      "timestamp" -> model.timestamp,
      "isText" -> model.isText,
      "data" -> Convert.toHexString(model.data),
      "nonce" -> Convert.toHexString(model.nonce),
      "confirmed" -> model.confirmed
    )
    if (model.payment == null) obj else obj ++ Json.obj(
      "payment" -> ReplicatorPayment.toJson(model.payment)
    )
  }  
  
  def save(model: Model): Long = {
    DB.withConnection { conn =>
      try {
        val pstmt = conn.prepareStatement("""
          INSERT INTO message (
            is_text,
            data,
            nonce,
            recipient_id,
            sender_id,
            timestamp,
            recipient_status,
            sender_status,
            unread)
          VALUES (?,?,?,?,?,?,?,?,?)
        """, Statement.RETURN_GENERATED_KEYS)
        pstmt.setBoolean(1, model.isText)
        pstmt.setBytes(2, model.data)
        pstmt.setBytes(3, model.nonce)
        pstmt.setLong(4, model.recipientId)
        pstmt.setLong(5, model.senderId)
        pstmt.setInt(6, model.timestamp)
        pstmt.setInt(7, model.recipientStatus)
        pstmt.setInt(8, model.senderStatus)
        pstmt.setBoolean(9, model.unread)
          
        pstmt.executeUpdate()
        
        val rs = pstmt.getGeneratedKeys();
        if (rs.next()){
          val id = rs.getLong(1)
          model.id = id       
          model.blockchain = false
          model.confirmed = true
          PushTopics.message(model)
          return id;
        }
        return 0
      } catch {
        case e:SQLException => Logger.error("Messaging.save error", e)
        case e:Exception => Logger.error("Messaging.save error", e)
      }
    }
    -1
  }    
  
  def find(id: Long): Option[Model] = {
    val sql = s"""
      SELECT
        a.id,
        FALSE AS blockchain, 
        a.unread,
        a.recipient_status,
        a.sender_status,
        a.sender_id,
        a.recipient_id,
        a.timestamp,
        a.is_text, 
        a.data,
        a.nonce,
        b.public_key AS recipient_public_key,
        c.public_key AS sender_public_key,
        d.identifier AS recipient_identifier,
        e.identifier AS sender_identifier,  
        f.name AS recipient_name,
        g.name AS sender_name,
        TRUE AS confirmed
      FROM
        message a
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
        LEFT OUTER JOIN replicator_user f
          ON a.recipient_id=f.account_id
        LEFT OUTER JOIN replicator_user g
          ON a.recipient_id=g.account_id
      WHERE id = ?
      UNION ALL 
      SELECT
        a.transaction_id AS id,
        TRUE AS blockchain, 
        a.unread,
        a.recipient_status,
        a.sender_status,
        a.sender_id,
        a.recipient_id,
        a.timestamp,
        a.is_text, 
        a.data,
        a.nonce,
        b.public_key AS recipient_public_key,
        c.public_key AS sender_public_key,
        d.identifier AS recipient_identifier,
        e.identifier AS sender_identifier,  
        f.name AS recipient_name,
        g.name AS sender_name,
        a.confirmed
      FROM
        replicator_message a
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
        LEFT OUTER JOIN replicator_user f
          ON a.recipient_id=f.account_id
        LEFT OUTER JOIN replicator_user g
          ON a.recipient_id=g.account_id
      WHERE transaction_id = ?
      """

    DB.withConnection { conn =>
      try {
        val pstmt = conn.prepareStatement(sql)
        pstmt.setLong(1, id)
        pstmt.setLong(2, id)
        val result = pstmt.executeQuery()
        if (result.next()) {
          return Some(Model(
            result.getLong("id"),   
            result.getBoolean("unread"),
            result.getInt("recipient_status"),
            result.getInt("sender_status"),
            result.getBoolean("blockchain"),
            result.getLong("sender_id"),           
            result.getString("sender_name"),    
            result.getString("sender_identifier"),
            result.getBytes("sender_public_key"),
            result.getLong("recipient_id"),           
            result.getString("recipient_name"),    
            result.getString("recipient_identifier"),
            result.getBytes("recipient_public_key"),   
            result.getInt("timestamp"),
            result.getBoolean("is_text"), 
            result.getBytes("data"),
            result.getBytes("nonce"),
            null,
            result.getBoolean("confirmed")
          ))
        }
      } catch {
        case e:SQLException => Logger.error("Messaging.find error", e)
        case e:Exception => Logger.error("Messaging.find error", e)
      }
    }
    None
  }
  
  def setFlag(id: Long, flag: Int, table: String, pkColumn: String, forRecipient: Boolean): Option[Int] = {
    DB.withConnection { conn =>
      try {
        val column = if (forRecipient) "recipient_status" else "sender_status"
        val pstmt1 = conn.prepareStatement(s"UPDATE $table SET $column = ($column | ?) WHERE $pkColumn = ?")
        pstmt1.setInt(1, flag)
        pstmt1.setLong(2, id)
        pstmt1.executeUpdate()
        
        val pstmt2 = conn.prepareStatement(s"SELECT $column FROM $table WHERE $pkColumn = ?")
        pstmt2.setLong(1, id)
        val result = pstmt2.executeQuery()
        if (result.next()) {
          find(id) match {
            case Some(message) => PushTopics.messageUpdate(message)      
            case None => Logger.error("Messaging.setFlag weirdness, model not found")
          }          
          return Some(result.getInt(column))
        }
      } catch {
        case e:SQLException => Logger.error("Messaging.setFlag error", e)
        case e:Exception => Logger.error("Messaging.setFlag error", e)
      }
    }
    None
  }
  
  /**
   * Some flags can only be set and not be reset, 
   * flags in this category are:
   * 
   * 1. DEPOSIT
   * 2. WITHDRAWAL
   */
  def resetFlag(id: Long, flag: Int, table: String, pkColumn: String, forRecipient: Boolean): Option[Int] = {
    DB.withConnection { conn =>
      try {
        val column = if (forRecipient) "recipient_status" else "sender_status"
        val pstmt1 = conn.prepareStatement(s"UPDATE $table SET $column = (($column | ?) ^?) WHERE $pkColumn = ?")
        pstmt1.setInt(1, flag)
        pstmt1.setInt(2, flag)
        pstmt1.setLong(3, id)
        pstmt1.executeUpdate()
        
        val pstmt2 = conn.prepareStatement(s"SELECT $column FROM $table WHERE $pkColumn = ?")
        pstmt2.setLong(1, id)
        val result = pstmt2.executeQuery()
        if (result.next()) {
          find(id) match {
            case Some(message) => PushTopics.messageUpdate(message)     
            case None => Logger.error("Messaging.resetFlag weirdness, model not found")
          }
          return Some(result.getInt(column))
        }          
      } catch {
        case e:SQLException => Logger.error("Messaging.resetFlag error", e)
        case e:Exception => Logger.error("Messaging.resetFlag error", e)
      }
    }
    None
  }
  
  def update_unread(id: Long, unread: Boolean, table: String, pkColumn: String): Boolean = {
    DB.withConnection { conn =>
      try {
        val pstmt1 = conn.prepareStatement(s"UPDATE $table SET unread = ? WHERE $pkColumn = ?")
        pstmt1.setBoolean(1, unread)
        pstmt1.setLong(2, id)
        pstmt1.executeUpdate()
        find(id) match {
          case Some(message) => PushTopics.messageUpdate(message)      
          case None => Logger.error("Messaging.update_unread weirdness, model not found")
        }
        return true
      } catch {
        case e:SQLException => Logger.error("Messaging.update_status error", e)
        case e:Exception => Logger.error("Messaging.update_status error", e)
      }
    }
    false
  }  
  
  case class MessagingCount(
    unread: Int,
    sent: Int,
    received: Int,
    total: Int
  )
  
  def count(accountId: Long, otherAccountId: Long): Option[MessagingCount] = {
    val sql = if (otherAccountId == 0) s"""
    SELECT unread, sent, received, total FROM (
      SELECT
        (
          SELECT 
            (SELECT COUNT(*) FROM message WHERE recipient_id = ? AND unread = TRUE) +
            (SELECT COUNT(*) FROM replicator_message WHERE recipient_id = ? AND unread = TRUE AND standalone = TRUE)
        ) AS unread,
        (
          SELECT 
            (SELECT COUNT(*) FROM message WHERE sender_id = ?) +
            (SELECT COUNT(*) FROM replicator_message WHERE sender_id = ? AND standalone = TRUE)
        ) AS sent,
        (
          SELECT 
            (SELECT COUNT(*) FROM message WHERE recipient_id = ?) +
            (SELECT COUNT(*) FROM replicator_message WHERE recipient_id = ? AND standalone = TRUE)
        ) AS received,
        (
          SELECT 
            (SELECT COUNT(*) FROM message WHERE sender_id = ? OR recipient_id = ?) +
            (SELECT COUNT(*) FROM replicator_message WHERE (sender_id = ? OR recipient_id = ?) AND standalone = TRUE)
        ) AS total
    ) AS a
    """
    else s"""
    SELECT unread, total, 0 AS sent, 0 AS received FROM (
      SELECT
        (
          SELECT 
            (SELECT COUNT(*) FROM message WHERE recipient_id = ? AND sender_id = ? AND unread = TRUE) +
            (SELECT COUNT(*) FROM replicator_message WHERE recipient_id = ? AND sender_id = ? AND unread = TRUE AND standalone = TRUE)
        ) AS unread,
        (
          SELECT 
            (SELECT COUNT(*) FROM (
              SELECT 1 FROM message WHERE sender_id = ? AND recipient_id = ?
              UNION ALL
              SELECT 1 FROM message WHERE sender_id = ? AND recipient_id = ?
            ) a1 ) +
            (SELECT COUNT(*) FROM (
              SELECT 1 FROM replicator_message WHERE sender_id = ? AND recipient_id = ? AND standalone = TRUE
              UNION ALL
              SELECT 1 FROM replicator_message WHERE sender_id = ? AND recipient_id = ? AND standalone = TRUE
            ) a2)
        ) AS total
    ) AS a
    """
    
    DB.withConnection { conn =>
      try {
        val pstmt = conn.prepareStatement(sql)
        if (otherAccountId == 0) {
          pstmt.setLong(1, accountId)
          pstmt.setLong(2, accountId)
          pstmt.setLong(3, accountId)
          pstmt.setLong(4, accountId)
          pstmt.setLong(5, accountId)
          pstmt.setLong(6, accountId)
          pstmt.setLong(7, accountId)
          pstmt.setLong(8, accountId)
          pstmt.setLong(9, accountId)
          pstmt.setLong(10, accountId)
        }
        else {
          pstmt.setLong(1, accountId)
          pstmt.setLong(2, otherAccountId)
          pstmt.setLong(3, otherAccountId)
          pstmt.setLong(4, accountId)    
          pstmt.setLong(5, accountId)
          pstmt.setLong(6, otherAccountId)
          pstmt.setLong(7, otherAccountId)
          pstmt.setLong(8, accountId)             
          pstmt.setLong(9, accountId)
          pstmt.setLong(10, otherAccountId)
          pstmt.setLong(11, otherAccountId)
          pstmt.setLong(12, accountId)    
        }
        
        val result = pstmt.executeQuery()
        if (result.next()) {
          return Some(
            MessagingCount(
              result.getInt("unread"), 
              result.getInt("sent"),
              result.getInt("received"),
              result.getInt("total")
            )
          )
        }
      } catch {
        case e:SQLException => Logger.error("Messaging.count error", e)
        case e:Exception => Logger.error("Messaging.count error", e)
      }
    }
    None  
  }
  
  /* The latest query groups an accounts messages (in and out) and groups them
   * per contact, we also include the number of unread messages. */
  case class MessagingLatest(
    accountId: Long,
    accountName: String,
    accountEmail: String,
    accountPublicKey: Array[Byte],    
    unreadCount: Long,
    latestTimestamp: Int
  )
  
  def latest(accountId: Long, firstIndex: Int, lastIndex: Int): Option[List[MessagingLatest]] = {
    val sql = s"""
      SELECT 
      	T.account_id, 
      	MAX(T.timestamp) AS latest, 
      	(
          SELECT
            (
              SELECT COUNT(*) FROM message a 
              WHERE a.recipient_id = ? AND a.sender_id = T.account_id AND a.unread = TRUE
            ) +
            (
              SELECT COUNT(*) FROM replicator_message b 
              WHERE b.recipient_id = ? AND b.sender_id = T.account_id AND b.unread = TRUE AND b.standalone = TRUE
            )
        ) AS unread_count,
        (
          SELECT public_key FROM replicator_public_key c 
          WHERE c.account_id = T.account_id LIMIT 1
        ) AS public_key,
        (
          SELECT name FROM replicator_user d 
          WHERE d.account_id = T.account_id LIMIT 1
        ) AS name,
        (
          SELECT identifier FROM replicator_identifier e 
          WHERE e.account_id = T.account_id 
          ORDER BY e.db_id DESC LIMIT 1
        ) AS identifier
      FROM (
        SELECT timestamp, recipient_id AS account_id FROM message f 
        WHERE f.sender_id = ?
      	UNION ALL
      	SELECT timestamp, sender_id AS account_id FROM message g 
        WHERE g.recipient_id = ? AND g.sender_id <> ?    
        UNION ALL  
        SELECT timestamp, recipient_id AS account_id FROM replicator_message h 
        WHERE h.sender_id = ?
      	UNION ALL
      	SELECT timestamp, sender_id AS account_id FROM replicator_message i 
        WHERE i.recipient_id = ? AND i.sender_id <> ?
      ) T GROUP BY T.account_id
      ORDER BY latest DESC
      ${DBUtils.limitsClause(firstIndex, lastIndex)}
      """    
    
    DB.withConnection { conn =>
      try {
        val pstmt = conn.prepareStatement(sql)
        pstmt.setLong(1, accountId)
        pstmt.setLong(2, accountId)
        pstmt.setLong(3, accountId)
        pstmt.setLong(4, accountId)
        pstmt.setLong(5, accountId)
        pstmt.setLong(6, accountId)    
        pstmt.setLong(7, accountId)
        pstmt.setLong(8, accountId)       
        
        DBUtils.setLimits(9, pstmt, firstIndex, lastIndex)
        
        val result = pstmt.executeQuery()
        val iter = new Iterator[MessagingLatest] {
          def hasNext = result.next()
          def next() = {
            MessagingLatest(
              result.getLong("account_id"),
              result.getString("name"),
              result.getString("identifier"),
              result.getBytes("public_key"),
              result.getLong("unread_count"),
              result.getInt("latest")
            )
          }
        }
        return Some(iter.toList)
      } catch {
        case e:SQLException => Logger.error("Messaging.latest error", e)
        case e:Exception => Logger.error("Messaging.latest error", e)
      }
    }
    None  
  }  
  
  def list(sortColumn: String, sortAsc: Boolean, firstIndex: Int, lastIndex: Int, accountId: Long, otherAccountId: Long): Option[List[Model]] = {
    if (!sortColumns.contains(sortColumn)) {
      Logger.info("Weirdness, Messaging.list called with invalid sortColumn: "+sortColumn)
      return None
    }
    
    val sql = s"""
    SELECT 
      T.id,
      T.blockchain,
      T.unread,
      T.recipient_status,
      T.sender_status,
      T.sender_id,
      T.recipient_id,
      T.timestamp,
      T.is_text,
      T.data,
      T.nonce,
      e.public_key AS recipient_public_key,
      f.public_key AS sender_public_key,
      g.identifier AS recipient_identifier,
      h.identifier AS sender_identifier,
      i.name AS recipient_name,
      j.name AS sender_name,
      T.has_payment,
      T.confirmed
    FROM (
      SELECT 
        a.id,
        FALSE AS blockchain,
        a.unread,
        a.recipient_status,
        a.sender_status,
        a.recipient_id,
        a.sender_id,
        a.timestamp,
        a.is_text,
        a.data,
        a.nonce,
        FALSE AS has_payment,
        TRUE AS confirmed
      FROM
        message a
      WHERE a.sender_id = ? AND a.recipient_id = ?
      UNION ALL
      SELECT 
        b.id,
        FALSE AS blockchain,
        b.unread,
        b.recipient_status,
        b.sender_status,
        b.recipient_id,
        b.sender_id,
        b.timestamp,
        b.is_text,
        b.data,
        b.nonce,
        FALSE AS has_payment,
        TRUE AS confirmed
      FROM
        message b
      WHERE b.sender_id = ? AND b.recipient_id = ? AND b.sender_id <> ? AND b.recipient_id <> ?
      UNION ALL
      SELECT 
        c.transaction_id AS id,
        TRUE AS blockchain,
        c.unread,
        c.recipient_status,
        c.sender_status,
        c.recipient_id,
        c.sender_id,
        c.timestamp,
        c.is_text,
        c.data,
        c.nonce,
        k.transaction_id IS NOT NULL AS has_payment,
        c.confirmed
      FROM
        replicator_message c
        LEFT OUTER JOIN replicator_payment k
          ON c.transaction_id = k.transaction_id
      WHERE c.sender_id = ? AND c.recipient_id = ?
      UNION ALL
      SELECT 
        d.transaction_id AS id,
        TRUE AS blockchain,
        d.unread,
        d.recipient_status,
        d.sender_status,
        d.recipient_id,
        d.sender_id,
        d.timestamp,
        d.is_text,
        d.data,
        d.nonce,
        l.transaction_id IS NOT NULL AS has_payment,
        d.confirmed
      FROM
        replicator_message d
        LEFT OUTER JOIN replicator_payment l
          ON d.transaction_id = l.transaction_id
      WHERE d.sender_id = ? AND d.recipient_id = ? AND d.sender_id <> ? AND d.recipient_id <> ?
    ) T
    LEFT OUTER JOIN replicator_public_key e 
      ON T.recipient_id = e.account_id
    LEFT OUTER JOIN replicator_public_key f 
      ON T.sender_id    = f.account_id
    LEFT OUTER JOIN replicator_identifier g
      ON T.recipient_id = g.account_id
      AND g.db_id = (
        SELECT g1.db_id FROM replicator_identifier g1
        WHERE T.recipient_id = g1.account_id
        ORDER BY g1.db_id DESC LIMIT 1
      )
    LEFT OUTER JOIN replicator_identifier h
      ON T.sender_id = h.account_id
      AND h.db_id = (
        SELECT h1.db_id FROM replicator_identifier h1
        WHERE T.sender_id = h1.account_id
        ORDER BY h1.db_id DESC LIMIT 1
      )
    LEFT OUTER JOIN replicator_user i
      ON T.recipient_id = i.account_id
    LEFT OUTER JOIN replicator_user j
      ON T.sender_id = j.account_id
    ORDER BY $sortColumn ${if (sortAsc) "ASC" else "DESC"}
    ${DBUtils.limitsClause(firstIndex, lastIndex)}
    """

    DB.withConnection { conn =>
      try {
        val pstmt = conn.prepareStatement(sql)
        pstmt.setLong(1, accountId)
        pstmt.setLong(2, otherAccountId)
        pstmt.setLong(3, otherAccountId)
        pstmt.setLong(4, accountId)
        pstmt.setLong(5, accountId)
        pstmt.setLong(6, otherAccountId)        
        pstmt.setLong(7, otherAccountId)
        pstmt.setLong(8, accountId)        
        pstmt.setLong(9, accountId)
        pstmt.setLong(10, otherAccountId)
        pstmt.setLong(11, otherAccountId)
        pstmt.setLong(12, accountId) 
        DBUtils.setLimits(13, pstmt, firstIndex, lastIndex)
        
        val result = pstmt.executeQuery()
        val iter = new Iterator[Model] {
          def hasNext = result.next()
          def next() = {
            val payment = if (result.getBoolean("has_payment")) {
              ReplicatorPayment.find(conn, result.getLong("id"), false) match {
                case Some(msg) => msg
                case None => null
              }
            } else null             
            
            Model(
              result.getLong("id"),   
              result.getBoolean("unread"), 
              result.getInt("recipient_status"), 
              result.getInt("sender_status"), 
              result.getBoolean("blockchain"),
              result.getLong("sender_id"),           
              result.getString("sender_name"),    
              result.getString("sender_identifier"),
              result.getBytes("sender_public_key"),
              result.getLong("recipient_id"),           
              result.getString("recipient_name"),    
              result.getString("recipient_identifier"),
              result.getBytes("recipient_public_key"), 
              result.getInt("timestamp"), 
              result.getBoolean("is_text"), 
              result.getBytes("data"), 
              result.getBytes("nonce"),
              payment,
              result.getBoolean("confirmed")
            )
          }
        }
        return Some(iter.toList)
      } catch {
        case e:SQLException => Logger.error("Messaging.list error", e)
        case e:Exception => Logger.error("Messaging.list error", e)
      }
    }
    None    
  }
  
  def inboxCountClause(otherAccountId: Long): String = {
    if (otherAccountId == 0) s"""
      (SELECT COUNT(*) FROM message 
       WHERE recipient_id = ? AND (recipient_status & 2) <> 2) +
      (SELECT COUNT(*) FROM replicator_message 
       WHERE recipient_id = ? AND standalone = TRUE AND (recipient_status & 2) <> 2)
    """
    else s"""
      (SELECT COUNT(*) FROM message 
       WHERE recipient_id = ? AND sender_id = ? AND (recipient_status & 2) <> 2) +
      (SELECT COUNT(*) FROM replicator_message 
       WHERE recipient_id = ? AND sender_id = ? AND standalone = TRUE AND (recipient_status & 2) <> 2)
    """      
  }
  
  def inboxCountParams(pstmt: PreparedStatement, accountId: Long, otherAccountId: Long) = {
    if (otherAccountId == 0) {
      pstmt.setLong(1, accountId)
      pstmt.setLong(2, accountId)
    }
    else {
      pstmt.setLong(1, accountId)
      pstmt.setLong(2, otherAccountId)
      pstmt.setLong(3, accountId)
      pstmt.setLong(4, otherAccountId)
    }
  }
  
  def inboxCount(accountId: Long, otherAccountId: Long): Option[Int] = {
    val sql = s"""
      SELECT count FROM (
        SELECT (
          SELECT 
            ${inboxCountClause(otherAccountId)}
        ) AS count
      ) a
    """ 
    DB.withConnection { conn =>
      try {
        val pstmt = conn.prepareStatement(sql)
        inboxCountParams(pstmt, accountId, otherAccountId)
        val result = pstmt.executeQuery()
        if (result.next()) {
          return Some(result.getInt("count"))
        }
      } catch {
        case e:SQLException => Logger.error("Messaging.inboxCount error", e)
        case e:Exception => Logger.error("Messaging.inboxCount error", e)
      }
    }
    None        
  }

  def inboxUnionClause(otherAccountId: Long, withReplied: Boolean): String = {
    if (otherAccountId == 0) s"""
      SELECT 
        a.id,
        FALSE AS blockchain,
        a.unread,
        a.recipient_status,
        a.sender_status,
        a.recipient_id,
        a.sender_id,
        a.timestamp,
        a.is_text,
        a.data,
        a.nonce,
        FALSE AS has_payment,
        ${ if (withReplied) "((a.recipient_status & 4) = 4) AS replied, " else "" }
        TRUE AS confirmed
      FROM
        message a
      WHERE a.recipient_id = ?
      UNION ALL
      SELECT 
        c.transaction_id AS id,
        TRUE AS blockchain,
        c.unread,
        c.recipient_status,
        c.sender_status,
        c.recipient_id,
        c.sender_id,
        c.timestamp,
        c.is_text,
        c.data,
        c.nonce,
        FALSE AS has_payment,
        ${ if (withReplied) "((c.recipient_status & 4) = 4) AS replied, " else "" }
        c.confirmed
      FROM
        replicator_message c
      WHERE 
        c.recipient_id = ? AND c.standalone = TRUE AND (c.recipient_status & 2) <> 2
      """
    else s"""
      SELECT 
        a.id,
        FALSE AS blockchain,
        a.unread,
        a.recipient_status,
        a.sender_status,
        a.recipient_id,
        a.sender_id,
        a.timestamp,
        a.is_text,
        a.data,
        a.nonce,
        FALSE AS has_payment,
        ${ if (withReplied) "((a.recipient_status & 4) = 4) AS replied, " else "" }
        TRUE AS confirmed
      FROM
        message a
      WHERE a.recipient_id = ? AND a.sender_id = ?
      UNION ALL
      SELECT 
        c.transaction_id AS id,
        TRUE AS blockchain,
        c.unread,
        c.recipient_status,
        c.sender_status,
        c.recipient_id,
        c.sender_id,
        c.timestamp,
        c.is_text,
        c.data,
        c.nonce,
        FALSE AS has_payment,
        ${ if (withReplied) "((c.recipient_status & 4) = 4) AS replied, " else "" }
        c.confirmed
      FROM
        replicator_message c
      WHERE 
        c.recipient_id = ? AND c.sender_id = ? AND c.standalone = TRUE AND (c.recipient_status & 2) <> 2
      """      
  }
  
  def inboxUnionParams(pstmt: PreparedStatement, accountId: Long, otherAccountId: Long): Int = {
    if (otherAccountId == 0) {
      pstmt.setLong(1, accountId)
      pstmt.setLong(2, accountId)
      return 3
    }
    pstmt.setLong(1, accountId)
    pstmt.setLong(2, otherAccountId)
    pstmt.setLong(3, accountId)
    pstmt.setLong(4, otherAccountId)        
    return 5  
  }  
  
  def inbox(sortColumn: String, sortAsc: Boolean, firstIndex: Int, lastIndex: Int, 
            accountId: Long, otherAccountId: Long): Option[List[Model]] = {
    if (!sortColumns.contains(sortColumn)) {
      Logger.info("Weirdness, Messaging.inbox called with invalid sortColumn: "+sortColumn)
      return None
    }
    val withReplied = "default".equals(sortColumn)
    
    val sql = s"""
    SELECT 
      T.id,
      T.blockchain,
      T.unread,
      T.recipient_status,
      T.sender_status,
      T.sender_id,
      T.recipient_id,
      T.timestamp,
      T.is_text,
      T.data,
      T.nonce,
      e.public_key AS recipient_public_key,
      f.public_key AS sender_public_key,
      g.identifier AS recipient_identifier,
      h.identifier AS sender_identifier,
      i.name AS recipient_name,
      j.name AS sender_name,
      T.has_payment,
      ${ if (withReplied) "T.replied, " else "" }
      T.confirmed
    FROM (
      ${inboxUnionClause(otherAccountId, withReplied)}
    ) T
    LEFT OUTER JOIN replicator_public_key e 
      ON T.recipient_id = e.account_id
    LEFT OUTER JOIN replicator_public_key f 
      ON T.sender_id    = f.account_id
    LEFT OUTER JOIN replicator_identifier g
      ON T.recipient_id = g.account_id
      AND g.db_id = (
        SELECT g1.db_id FROM replicator_identifier g1
        WHERE T.recipient_id = g1.account_id
        ORDER BY g1.db_id DESC LIMIT 1
      )
    LEFT OUTER JOIN replicator_identifier h
      ON T.sender_id = h.account_id
      AND h.db_id = (
        SELECT h1.db_id FROM replicator_identifier h1
        WHERE T.sender_id = h1.account_id
        ORDER BY h1.db_id DESC LIMIT 1
      )
    LEFT OUTER JOIN replicator_user i
      ON T.recipient_id = i.account_id
    LEFT OUTER JOIN replicator_user j
      ON T.sender_id = j.account_id
    ORDER BY 
    ${ 
      if (withReplied) "unread DESC, replied DESC, timestamp DESC" 
      else s"$sortColumn ${if (sortAsc) "ASC" else "DESC"}"
    } 
    ${DBUtils.limitsClause(firstIndex, lastIndex)}
    """

    DB.withConnection { conn =>
      try {
        val pstmt = conn.prepareStatement(sql)
        val i = inboxUnionParams(pstmt, accountId, otherAccountId)
        DBUtils.setLimits(i, pstmt, firstIndex, lastIndex)
        
        val result = pstmt.executeQuery()
        val iter = new Iterator[Model] {
          def hasNext = result.next()
          def next() = {            
            val payment = if (result.getBoolean("has_payment")) {
              ReplicatorPayment.find(conn, result.getLong("id"), false) match {
                case Some(payment) => payment
                case None => null
              }
            } else null     
            
            Model(
              result.getLong("id"),   
              result.getBoolean("unread"), 
              result.getInt("recipient_status"), 
              result.getInt("sender_status"), 
              result.getBoolean("blockchain"),
              result.getLong("sender_id"),           
              result.getString("sender_name"),    
              result.getString("sender_identifier"),
              result.getBytes("sender_public_key"),
              result.getLong("recipient_id"),           
              result.getString("recipient_name"),    
              result.getString("recipient_identifier"),
              result.getBytes("recipient_public_key"), 
              result.getInt("timestamp"), 
              result.getBoolean("is_text"), 
              result.getBytes("data"), 
              result.getBytes("nonce"),
              payment,
              result.getBoolean("confirmed")
            )
          }
        }
        return Some(iter.toList)
      } catch {
        case e:SQLException => Logger.error("Messaging.inbox error", e)
        case e:Exception => Logger.error("Messaging.inbox error", e)
      }
    }
    None
  }    
  
  def outboxCountClause(otherAccountId: Long): String = {
    if (otherAccountId == 0) s"""
      (SELECT COUNT(*) FROM message 
       WHERE sender_id = ? AND (sender_status & 2) <> 2) +
      (SELECT COUNT(*) FROM replicator_message 
       WHERE sender_id = ? AND standalone = TRUE AND (sender_status & 2) <> 2)
    """
    else s"""
      (SELECT COUNT(*) FROM message 
       WHERE recipient_id = ? AND sender_id = ? AND (sender_status & 2) <> 2) +
      (SELECT COUNT(*) FROM replicator_message 
       WHERE recipient_id = ? AND sender_id = ? AND standalone = TRUE AND (sender_status & 2) <> 2)
    """
  }
  
  def outboxCountParams(pstmt: PreparedStatement, accountId: Long, otherAccountId: Long) = {
    if (otherAccountId == 0) {
      pstmt.setLong(1, accountId)
      pstmt.setLong(2, accountId)
    }
    else {
      pstmt.setLong(1, otherAccountId)
      pstmt.setLong(2, accountId)
      pstmt.setLong(3, otherAccountId)
      pstmt.setLong(4, accountId)
    }
  }
  
  def outboxCount(accountId: Long, otherAccountId: Long): Option[Int] = {
    val sql = s"""
      SELECT count FROM (
        SELECT (
          SELECT 
            ${outboxCountClause(otherAccountId)}
        ) AS count
      ) a
    """ 
    
    Logger.info(sql)
    
    DB.withConnection { conn =>
      try {
        val pstmt = conn.prepareStatement(sql)
        outboxCountParams(pstmt, accountId, otherAccountId)
        val result = pstmt.executeQuery()
        if (result.next()) {
          return Some(result.getInt("count"))
        }
      } catch {
        case e:SQLException => Logger.error("Messaging.outboxCount error", e)
        case e:Exception => Logger.error("Messaging.outboxCount error", e)
      }
    }
    None        
  }

  def outboxUnionClause(otherAccountId: Long): String = {
    if (otherAccountId == 0) s"""
    SELECT 
      a.id,
      FALSE AS blockchain,
      a.unread,
      a.recipient_status,
      a.sender_status,
      a.recipient_id,
      a.sender_id,
      a.timestamp,
      a.is_text,
      a.data,
      a.nonce,
      FALSE AS has_payment,
      TRUE AS confirmed
    FROM
      message a
    WHERE a.sender_id = ? AND (a.sender_status & 2) <> 2
    UNION ALL
    SELECT 
      c.transaction_id AS id,
      TRUE AS blockchain,
      c.unread,
      c.recipient_status,
      c.sender_status,
      c.recipient_id,
      c.sender_id,
      c.timestamp,
      c.is_text,
      c.data,
      c.nonce,
      FALSE AS has_payment,
      c.confirmed
    FROM
      replicator_message c
    WHERE 
      c.sender_id = ? AND c.standalone = TRUE AND (c.sender_status & 2) <> 2
    """
    else s"""
    SELECT 
      a.id,
      FALSE AS blockchain,
      a.unread,
      a.recipient_status,
      a.sender_status,
      a.recipient_id,
      a.sender_id,
      a.timestamp,
      a.is_text,
      a.data,
      a.nonce,
      FALSE AS has_payment,
      TRUE AS confirmed
    FROM
      message a
    WHERE a.recipient_id = ? AND a.sender_id = ? AND (a.sender_status & 2) <> 2
    UNION ALL
    SELECT 
      c.transaction_id AS id,
      TRUE AS blockchain,
      c.unread,
      c.recipient_status,
      c.sender_status,
      c.recipient_id,
      c.sender_id,
      c.timestamp,
      c.is_text,
      c.data,
      c.nonce,
      FALSE AS has_payment,
      c.confirmed
    FROM
      replicator_message c
    WHERE 
      c.recipient_id = ? AND c.sender_id = ? AND c.standalone = TRUE AND (c.sender_status & 2) <> 2
    """
  }
  
  def outboxUnionParams(pstmt: PreparedStatement, accountId: Long, otherAccountId: Long): Int = {
    if (otherAccountId == 0) {
      pstmt.setLong(1, accountId)
      pstmt.setLong(2, accountId)
      3
    }
    else {
      pstmt.setLong(1, otherAccountId)
      pstmt.setLong(2, accountId)
      pstmt.setLong(3, otherAccountId)
      pstmt.setLong(4, accountId)
      5
    }
  }  
  
  def outbox(sortColumn: String, sortAsc: Boolean, firstIndex: Int, lastIndex: Int, 
            accountId: Long, otherAccountId: Long): Option[List[Model]] = {
    if (!sortColumns.contains(sortColumn)) {
      Logger.info("Weirdness, Messaging.outbox called with invalid sortColumn: "+sortColumn)
      return None
    }
    
    val sql = s"""
    SELECT 
      T.id,
      T.blockchain,
      T.unread,
      T.recipient_status,
      T.sender_status,
      T.sender_id,
      T.recipient_id,
      T.timestamp,
      T.is_text,
      T.data,
      T.nonce,
      e.public_key AS recipient_public_key,
      f.public_key AS sender_public_key,
      g.identifier AS recipient_identifier,
      h.identifier AS sender_identifier,
      i.name AS recipient_name,
      j.name AS sender_name,
      T.has_payment,
      T.confirmed
    FROM (
      ${outboxUnionClause(otherAccountId)}
    ) T
    LEFT OUTER JOIN replicator_public_key e 
      ON T.recipient_id = e.account_id
    LEFT OUTER JOIN replicator_public_key f 
      ON T.sender_id    = f.account_id
    LEFT OUTER JOIN replicator_identifier g
      ON T.recipient_id = g.account_id
      AND g.db_id = (
        SELECT g1.db_id FROM replicator_identifier g1
        WHERE T.recipient_id = g1.account_id
        ORDER BY g1.db_id DESC LIMIT 1
      )
    LEFT OUTER JOIN replicator_identifier h
      ON T.sender_id = h.account_id
      AND h.db_id = (
        SELECT h1.db_id FROM replicator_identifier h1
        WHERE T.sender_id = h1.account_id
        ORDER BY h1.db_id DESC LIMIT 1
      )
    LEFT OUTER JOIN replicator_user i
      ON T.recipient_id = i.account_id
    LEFT OUTER JOIN replicator_user j
      ON T.sender_id = j.account_id
    ORDER BY $sortColumn ${if (sortAsc) "ASC" else "DESC"}
    ${DBUtils.limitsClause(firstIndex, lastIndex)}
    """
    
    Logger.info(sql) 

    DB.withConnection { conn =>
      try {
        val pstmt = conn.prepareStatement(sql)
        val i = outboxUnionParams(pstmt, accountId, otherAccountId)
        DBUtils.setLimits(i, pstmt, firstIndex, lastIndex)
        
        val result = pstmt.executeQuery()
        val iter = new Iterator[Model] {
          def hasNext = result.next()
          def next() = {            
            val payment = if (result.getBoolean("has_payment")) {
              ReplicatorPayment.find(conn, result.getLong("id"), false) match {
                case Some(payment) => payment
                case None => null
              }
            } else null
            
            Model(
              result.getLong("id"),   
              result.getBoolean("unread"), 
              result.getInt("recipient_status"), 
              result.getInt("sender_status"), 
              result.getBoolean("blockchain"),
              result.getLong("sender_id"),           
              result.getString("sender_name"),    
              result.getString("sender_identifier"),
              result.getBytes("sender_public_key"),
              result.getLong("recipient_id"),           
              result.getString("recipient_name"),    
              result.getString("recipient_identifier"),
              result.getBytes("recipient_public_key"), 
              result.getInt("timestamp"), 
              result.getBoolean("is_text"), 
              result.getBytes("data"), 
              result.getBytes("nonce"),
              payment,
              result.getBoolean("confirmed")
            )
          }
        }
        return Some(iter.toList)
      } catch {
        case e:SQLException => Logger.error("Messaging.outbox error", e)
        case e:Exception => Logger.error("Messaging.outbox error", e)
      }
    }
    None
  }   
  
  def trashedCountClause(otherAccountId: Long): String = {
    if (otherAccountId == 0) s"""
      (SELECT COUNT(*) FROM replicator_message 
       WHERE recipient_id = ? AND standalone = TRUE AND (recipient_status & 2) = 2) + 
      (SELECT COUNT(*) FROM replicator_message 
       WHERE sender_id = ? AND standalone = TRUE AND (sender_status & 2) = 2)
    """
    else s"""
      (SELECT COUNT(*) FROM replicator_message 
       WHERE sender_id = ? AND recipient_id = ? AND standalone = TRUE AND (recipient_status & 2) = 2) + 
      (SELECT COUNT(*) FROM replicator_message 
       WHERE recipient_id = ? AND sender_id = ? AND standalone = TRUE AND (sender_status & 2) = 2)
    """
  }
  
  def trashedCountParams(pstmt: PreparedStatement, accountId: Long, otherAccountId: Long) = {
    if (otherAccountId == 0) {
      pstmt.setLong(1, accountId)
      pstmt.setLong(2, accountId)
    }
    else {
      pstmt.setLong(1, otherAccountId)
      pstmt.setLong(2, accountId)
      pstmt.setLong(3, otherAccountId)
      pstmt.setLong(4, accountId)
    }
  }
  
  def trashedCount(accountId: Long, otherAccountId: Long): Option[Int] = {
    val sql = s"""
      SELECT count FROM (
        SELECT (
          SELECT 
            ${trashedCountClause(otherAccountId)}
        ) AS count
      ) a
    """ 

    Logger.info(sql)
    
    DB.withConnection { conn =>
      try {
        val pstmt = conn.prepareStatement(sql)
        trashedCountParams(pstmt, accountId, otherAccountId)
        val result = pstmt.executeQuery()
        if (result.next()) {
          return Some(result.getInt("count"))
        }
      } catch {
        case e:SQLException => Logger.error("Messaging.trashedCount error", e)
        case e:Exception => Logger.error("Messaging.trashedCount error", e)
      }
    }
    None        
  }

  def trashedUnionClause(otherAccountId: Long): String = {
    if (otherAccountId == 0) s"""
    SELECT 
      a.transaction_id AS id,
      TRUE AS blockchain,
      a.unread,
      a.recipient_status,
      a.sender_status,
      a.recipient_id,
      a.sender_id,
      a.timestamp,
      a.is_text,
      a.data,
      a.nonce,
      FALSE AS has_payment,
      TRUE AS confirmed
    FROM
      replicator_message a
    WHERE a.sender_id = ? AND (a.sender_status & 2) = 2
    UNION
    SELECT 
      c.transaction_id AS id,
      TRUE AS blockchain,
      c.unread,
      c.recipient_status,
      c.sender_status,
      c.recipient_id,
      c.sender_id,
      c.timestamp,
      c.is_text,
      c.data,
      c.nonce,
      FALSE AS has_payment,
      c.confirmed
    FROM
      replicator_message c
    WHERE 
      c.recipient_id = ? AND c.standalone = TRUE AND (c.recipient_status & 2) = 2
    """
    else s"""
    SELECT 
      a.transaction_id AS id,
      TRUE AS blockchain,
      a.unread,
      a.recipient_status,
      a.sender_status,
      a.recipient_id,
      a.sender_id,
      a.timestamp,
      a.is_text,
      a.data,
      a.nonce,
      FALSE AS has_payment,
      TRUE AS confirmed
    FROM
      replicator_message a
    WHERE a.recipient_id = ? AND a.sender_id = ? AND c.standalone = TRUE AND (a.sender_status & 2) = 2
    UNION
    SELECT 
      c.transaction_id AS id,
      TRUE AS blockchain,
      c.unread,
      c.recipient_status,
      c.sender_status,
      c.recipient_id,
      c.sender_id,
      c.timestamp,
      c.is_text,
      c.data,
      c.nonce,
      FALSE AS has_payment,
      c.confirmed
    FROM
      replicator_message c
    WHERE 
      c.recipient_id = ? AND c.sender_id = ? AND c.standalone = TRUE AND (c.recipient_status & 2) = 2
    """
  }
  
  def trashedUnionParams(pstmt: PreparedStatement, accountId: Long, otherAccountId: Long): Int = {
    if (otherAccountId == 0) {
      pstmt.setLong(1, accountId)
      pstmt.setLong(2, accountId)
      3
    }
    else {
      pstmt.setLong(1, otherAccountId)
      pstmt.setLong(2, accountId)
      pstmt.setLong(3, accountId)
      pstmt.setLong(4, otherAccountId)
      5
    }
  }  
  
  def trashed(sortColumn: String, sortAsc: Boolean, firstIndex: Int, lastIndex: Int, 
            accountId: Long, otherAccountId: Long): Option[List[Model]] = {
    if (!sortColumns.contains(sortColumn)) {
      Logger.info("Weirdness, Messaging.trashed called with invalid sortColumn: "+sortColumn)
      return None
    }
    
    val sql = s"""
    SELECT 
      T.id,
      T.blockchain,
      T.unread,
      T.recipient_status,
      T.sender_status,
      T.sender_id,
      T.recipient_id,
      T.timestamp,
      T.is_text,
      T.data,
      T.nonce,
      e.public_key AS recipient_public_key,
      f.public_key AS sender_public_key,
      g.identifier AS recipient_identifier,
      h.identifier AS sender_identifier,
      i.name AS recipient_name,
      j.name AS sender_name,
      T.has_payment,
      T.confirmed
    FROM (
      ${trashedUnionClause(otherAccountId)}
    ) T
    LEFT OUTER JOIN replicator_public_key e 
      ON T.recipient_id = e.account_id
    LEFT OUTER JOIN replicator_public_key f 
      ON T.sender_id    = f.account_id
    LEFT OUTER JOIN replicator_identifier g
      ON T.recipient_id = g.account_id
      AND g.db_id = (
        SELECT g1.db_id FROM replicator_identifier g1
        WHERE T.recipient_id = g1.account_id
        ORDER BY g1.db_id DESC LIMIT 1
      )
    LEFT OUTER JOIN replicator_identifier h
      ON T.sender_id = h.account_id
      AND h.db_id = (
        SELECT h1.db_id FROM replicator_identifier h1
        WHERE T.sender_id = h1.account_id
        ORDER BY h1.db_id DESC LIMIT 1
      )
    LEFT OUTER JOIN replicator_user i
      ON T.recipient_id = i.account_id
    LEFT OUTER JOIN replicator_user j
      ON T.sender_id = j.account_id
    ORDER BY $sortColumn ${if (sortAsc) "ASC" else "DESC"}
    ${DBUtils.limitsClause(firstIndex, lastIndex)}
    """
    
    Logger.info(sql)

    DB.withConnection { conn =>
      try {
        val pstmt = conn.prepareStatement(sql)
        val i = trashedUnionParams(pstmt, accountId, otherAccountId)
        DBUtils.setLimits(i, pstmt, firstIndex, lastIndex)
        
        val result = pstmt.executeQuery()
        val iter = new Iterator[Model] {
          def hasNext = result.next()
          def next() = {            
            val payment = if (result.getBoolean("has_payment")) {
              ReplicatorPayment.find(conn, result.getLong("id"), false) match {
                case Some(payment) => payment
                case None => null
              }
            } else null
            
            Model(
              result.getLong("id"),   
              result.getBoolean("unread"), 
              result.getInt("recipient_status"), 
              result.getInt("sender_status"), 
              result.getBoolean("blockchain"),
              result.getLong("sender_id"),           
              result.getString("sender_name"),    
              result.getString("sender_identifier"),
              result.getBytes("sender_public_key"),
              result.getLong("recipient_id"),           
              result.getString("recipient_name"),    
              result.getString("recipient_identifier"),
              result.getBytes("recipient_public_key"), 
              result.getInt("timestamp"), 
              result.getBoolean("is_text"), 
              result.getBytes("data"), 
              result.getBytes("nonce"),
              payment,
              result.getBoolean("confirmed")
            )
          }
        }
        return Some(iter.toList)
      } catch {
        case e:SQLException => Logger.error("Messaging.outbox error", e)
        case e:Exception => Logger.error("Messaging.outbox error", e)
      }
    }
    None
  }   
}
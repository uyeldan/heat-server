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
import play.api.libs.functional.syntax._
import play.api.libs.ws.WSClient
import play.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import java.sql.SQLException
import play.api.db.DB
import java.sql.Statement
import java.sql.PreparedStatement
import java.sql.Connection
import play.api.Play.current

object FimkParticipants {

  val mysqlSchema = """
    CREATE TABLE IF NOT EXISTS fimk_ico_payment (
      timestamp BIGINT NOT NULL,
      amount BIGINT NOT NULL,
      recipient VARCHAR(66) NOT NULL,
      sender VARCHAR(66) NOT NULL,
      transaction VARCHAR(66) NOT NULL,
      PRIMARY KEY (transaction))
  """

  val sources = Array(
    "https://cloud.mofowallet.org:7886/nxt?requestType=getAccountTransactions&account=FIM-G9TG-6AWU-MKP7-38A6D&firstIndex=0",
    "https://cloud.mofowallet.org:7886/nxt?requestType=getAccountTransactions&account=FIM-G9TG-6AWU-MKP7-38A6D&firstIndex=100"
  )

  case class Transaction(
      timestamp: Long,
      amount: String,
      recipient: String,
      sender: String,
      transaction: String)

  implicit val transactionReads: Reads[Transaction] = (
    (JsPath \ "timestamp").read[Long] and
    (JsPath \ "amountNQT").read[String] and
    (JsPath \ "recipientRS").read[String] and
    (JsPath \ "senderRS").read[String] and
    (JsPath \ "transaction").read[String])(Transaction.apply _)

  def init(ws: WSClient) : Unit = {
    if (DB.withConnection { conn =>
         DBUtils.getRowCount(conn, "fimk_ico_payment") == 164
       })
      return

    for (url <- sources) {
      loadSource(ws, url).map { success =>
        if (!success) {
          System.exit(1)
        }
      }
      Thread.sleep(1001)
    }
  }

  def loadSource(ws: WSClient, url: String) : Future[Boolean] = {
    Logger.info("FimkParticipants.loadSource URL: " + url)
    ws.url(url).get().map { response =>
      (response.json \ "transactions").validate[List[Transaction]] match {
        case JsSuccess(transactions: List[Transaction], _) => {
          DB.withConnection { conn =>
            transactions.foreach { transaction =>
              if (!saveToDb(conn, transaction))
                System.exit(1)
              Logger.info("[FIM] - Add transaction " + transaction.transaction)
            }
          }
          true
        }
        case e: JsError => {
          Logger.info("Errors: " + JsError.toJson(e).toString())
          false
        }
      }
    }
  }

  def saveToDb(conn: Connection, transaction: Transaction): Boolean = {
    try {
      val pstmt = conn.prepareStatement("""
        INSERT IGNORE INTO fimk_ico_payment (
          timestamp,
          amount,
          recipient,
          sender,
          transaction)
        VALUES (?,?,?,?,?)
      """)
      pstmt.setLong(1, transaction.timestamp)
      pstmt.setLong(2, java.lang.Long.parseUnsignedLong(transaction.amount))
      pstmt.setString(3, transaction.recipient)
      pstmt.setString(4, transaction.sender)
      pstmt.setString(5, transaction.transaction)
      pstmt.executeUpdate()

      return true
    } catch {
      case e:SQLException => Logger.error("FimkParticipants.saveToDb error", e)
      case e:Exception => Logger.error("FimkParticipants.saveToDb error", e)
    }
    false
  }

  def count(addr: String) : Option[Int] = {
    if (addr == "FIM-G9TG-6AWU-MKP7-38A6D")
      return Some(0)
    DB.withConnection { conn =>
      try {
        val pstmt = conn.prepareStatement("SELECT COUNT(*) FROM fimk_ico_payment WHERE sender = ?")
        pstmt.setString(1, addr)
        val result = pstmt.executeQuery()
        if (result.next)
          return Some(result.getInt(1))
      } catch {
        case e:SQLException => Logger.error("FimkParticipants.saveToDb error", e)
        case e:Exception => Logger.error("FimkParticipants.saveToDb error", e)
      }
      None
    }
  }
}
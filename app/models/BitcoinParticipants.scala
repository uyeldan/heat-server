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
import java.sql.SQLException
import play.api.db.DB
import java.sql.Statement
import java.sql.PreparedStatement
import java.sql.Connection
import play.api.Play.current

object BitcoinParticipants {

  val mysqlSchema1 = """
    CREATE TABLE IF NOT EXISTS bitcoin_ico_transaction (
      time BIGINT NOT NULL,
      tx_index BIGINT NOT NULL,
      hash VARCHAR(66) NOT NULL,
      PRIMARY KEY (tx_index))
  """

  val mysqlSchema2 = """
    CREATE TABLE IF NOT EXISTS bitcoin_ico_input (
      tx_index BIGINT NOT NULL,
      value DECIMAL(40) NOT NULL,
      addr VARCHAR(66) NOT NULL,
      PRIMARY KEY (tx_index,value,addr))
  """

  val mysqlSchema3 = """
    CREATE TABLE IF NOT EXISTS bitcoin_ico_output (
      tx_index BIGINT NOT NULL,
      value DECIMAL(40) NOT NULL,
      addr VARCHAR(66) NOT NULL,
      PRIMARY KEY (tx_index,value,addr))
  """

  val sources = Array(
    "https://blockchain.info/rawaddr/1HEATQCfWJKPWb8612K2oGR7EE6XPqNYHj?format=json&limit=50&offset=0",
    "https://blockchain.info/rawaddr/1HEATQCfWJKPWb8612K2oGR7EE6XPqNYHj?format=json&limit=50&offset=50",
    "https://blockchain.info/rawaddr/1HEATQCfWJKPWb8612K2oGR7EE6XPqNYHj?format=json&limit=50&offset=100",
    "https://blockchain.info/rawaddr/1HEATQCfWJKPWb8612K2oGR7EE6XPqNYHj?format=json&limit=50&offset=150",
    "https://blockchain.info/rawaddr/1HEATQCfWJKPWb8612K2oGR7EE6XPqNYHj?format=json&limit=50&offset=200"
  )

  def init(ws: WSClient) : Unit = {
    if (DB.withConnection { conn =>
         DBUtils.getRowCount(conn, "bitcoin_ico_transaction") == 238 &&
         DBUtils.getRowCount(conn, "bitcoin_ico_input") == 531 &&
         DBUtils.getRowCount(conn, "bitcoin_ico_output") == 493
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
    Logger.info("BitcoinParticipants.loadSource URL: " + url)
    ws.url(url).get().map { response =>
      (response.json \ "txs").validate[List[Bitcoin.Transaction]] match {
        case JsSuccess(transactions: List[Bitcoin.Transaction], _) => {
          DB.withConnection { conn =>
            transactions.foreach { transaction =>
              if (!saveToDb(conn, transaction))
                System.exit(1)
              Logger.info("[BTC] - Add transaction " + transaction.hash)
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

  def saveToDb(conn: Connection, transaction: Bitcoin.Transaction): Boolean = {
    try {
      val pstmt1 = conn.prepareStatement("""
        INSERT IGNORE INTO bitcoin_ico_transaction (
          time,
          tx_index,
          hash)
        VALUES (?,?,?)
      """)
      pstmt1.setLong(1, transaction.time)
      pstmt1.setLong(2, transaction.txIndex)
      pstmt1.setString(3, transaction.hash)
      pstmt1.executeUpdate()

      val pstmt2 = conn.prepareStatement("""
        INSERT IGNORE INTO bitcoin_ico_input (
          tx_index,
          value,
          addr)
        VALUES (?,?,?)
      """)
      transaction.inputs.foreach { input =>
        pstmt2.setLong(1, transaction.txIndex)
        pstmt2.setString(2, input.value.toString())
        pstmt2.setString(3, input.addr)
        pstmt2.executeUpdate()
      }

      val pstmt3 = conn.prepareStatement("""
        INSERT IGNORE INTO bitcoin_ico_output (
          tx_index,
          value,
          addr)
        VALUES (?,?,?)
      """)
      transaction.outputs.foreach { output =>
        output.addr match {
          case Some(addr) => {
            pstmt3.setLong(1, transaction.txIndex)
            pstmt3.setString(2, output.value.toString())
            pstmt3.setString(3, addr)
            pstmt3.executeUpdate()
          }
          case None => 0
        }
      }
      return true
    } catch {
      case e:SQLException => Logger.error("BitcoinParticipants.saveToDb error", e)
      case e:Exception => Logger.error("BitcoinParticipants.saveToDb error", e)
    }
    false
  }

  def count(addr: String) : Option[Int] = {
    if (addr == "1HEATQCfWJKPWb8612K2oGR7EE6XPqNYHj")
      return Some(0)
    DB.withConnection { conn =>
      try {
        val pstmt = conn.prepareStatement("SELECT COUNT(*) FROM bitcoin_ico_input WHERE addr = ?")
        pstmt.setString(1, addr)
        val result = pstmt.executeQuery()
        if (result.next)
          return Some(result.getInt(1))
      } catch {
        case e:SQLException => Logger.error("BitcoinParticipants.saveToDb error", e)
        case e:Exception => Logger.error("BitcoinParticipants.saveToDb error", e)
      }
      None
    }
  }
}
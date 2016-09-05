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
import java.math.BigInteger
import play.api.libs.json.jackson.JsValueSerializer

object EthereumParticipants {

  val mysqlSchema = """
    CREATE TABLE IF NOT EXISTS ethereum_ico_payment (
      time VARCHAR(66) NOT NULL,
      sender VARCHAR(200) NOT NULL,
      amount DECIMAL(40) NOT NULL,
      hash VARCHAR(66) NOT NULL,
      PRIMARY KEY (hash));
  """

  val sources = Array(
    "https://etherchain.org/api/account/0x4ea79a8ff56d39f5cb045642d6ce9cb0653e5e47/tx/0",
    "https://etherchain.org/api/account/0x4ea79a8ff56d39f5cb045642d6ce9cb0653e5e47/tx/50",
    "https://etherchain.org/api/account/0x4ea79a8ff56d39f5cb045642d6ce9cb0653e5e47/tx/100"
  )

  def init(ws: WSClient):Unit = {
    if (DB.withConnection { conn =>
         DBUtils.getRowCount(conn, "ethereum_ico_payment") == 110
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
    ws.url(url).get().map { response =>
      (response.json \ "data").validate[List[Ethereum.Payment]] match {
        case JsSuccess(payments: List[Ethereum.Payment], _) => {
          DB.withConnection { conn =>
            payments.foreach { payment =>
              if (!saveToDb(conn, payment))
                System.exit(1)
            }
          }
          true
        }
        case e: JsError => {
          false
        }
      }
    }
  }

  def saveToDb(conn: Connection, payment: Ethereum.Payment): Boolean = {
    try {
      val pstmt = conn.prepareStatement("""
        INSERT IGNORE INTO ethereum_ico_payment (
          sender,
          amount,
          hash,
          time)
        VALUES (?,?,?,?)
      """)
      pstmt.setString(1, payment.sender)
      pstmt.setString(2, payment.amount.toString())
      pstmt.setString(3, payment.hash)
      pstmt.setString(4, payment.time)
      pstmt.executeUpdate()
      return true
    } catch {
      case e:SQLException => Logger.error("EthereumParticipants.saveToDb error", e)
      case e:Exception => Logger.error("EthereumParticipants.saveToDb error", e)
    }
    false
  }


  def count(addr: String) : Option[Int] = {
    if (addr == "0x4ea79a8ff56d39f5cb045642d6ce9cb0653e5e47")
      return Some(0)
    DB.withConnection { conn =>
      try {
        val pstmt = conn.prepareStatement("SELECT COUNT(*) FROM ethereum_ico_payment WHERE sender = ?")
        pstmt.setString(1, addr)
        val result = pstmt.executeQuery()
        if (result.next)
          return Some(result.getInt(1))
      } catch {
        case e:SQLException => Logger.error("EthereumParticipants.saveToDb error", e)
        case e:Exception => Logger.error("EthereumParticipants.saveToDb error", e)
      }
      None
    }
  }
}
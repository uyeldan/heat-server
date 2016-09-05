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
import java.io.FileInputStream
import java.io.File

object EthereumAddresses {

  val mysqlSchema = """
    CREATE TABLE IF NOT EXISTS ethereum_bulk_address (
      addr VARCHAR(66) NOT NULL,
      in_use BOOLEAN NOT NULL DEFAULT 0,
      PRIMARY KEY (addr))
  """

  def init : Unit = {
    DB.withConnection { conn =>
      val stream = new FileInputStream(new File(Config.ethAddressFile))
      val json = try {  Json.parse(stream) } finally { stream.close() }
      if (!saveToDb(conn, json.as[List[String]]))
        System.exit(1)
      Logger.info("EthereumAddresses.init DONE")
    }
  }

  def saveToDb(conn: Connection, addrList: List[String]): Boolean = {
    try {
      val sql = "INSERT IGNORE INTO ethereum_bulk_address (addr) VALUES " + ("(?), " * (addrList.size-1)) + "(?)"
      val pstmt = conn.prepareStatement(sql)
      var index = 0
      addrList.foreach { addr =>
        index = index + 1
        pstmt.setString(index, addr)
      }
      pstmt.executeUpdate()
      return true
    } catch {
      case e:SQLException => Logger.error("EthereumAddresses.saveToDb error", e)
      case e:Exception => Logger.error("EthereumAddresses.saveToDb error", e)
    }
    false
  }

  def claimAddress : Option[String] = {
    DB.withConnection { conn =>
      try {
        val pstmt1 = conn.prepareStatement("SELECT addr FROM ethereum_bulk_address WHERE in_use = 0 LIMIT 1")
        val pstmt2 = conn.prepareStatement("UPDATE ethereum_bulk_address SET in_use = 1 WHERE addr = ? AND in_use = 0")
        val result1 = pstmt1.executeQuery()
        if (result1.next()) {
          val addr = result1.getString(1)
          pstmt2.setString(1, addr)
          if (pstmt2.executeUpdate() == 1) {
            return Some(addr)
          }
        }
      } catch {
        case e:SQLException => Logger.error("EthereumAddresses.claimAddress error", e)
        case e:Exception => Logger.error("EthereumAddresses.claimAddress error", e)
      }
    }
    None
  }

  def getTransactions(ws: WSClient, addr: String, offset: Int) : Future[Option[List[Ethereum.Payment]]] = {
    val url = s"https://etherchain.org/api/account/$addr/tx/$offset"
    ws.url(url).get().map { response =>
      (response.json \ "data").validate[List[Ethereum.Payment]] match {
        case JsSuccess(payments: List[Ethereum.Payment], _) => Some(payments)
        case e: JsError => {
          Logger.info("Errors: " + JsError.toJson(e).toString())
          None
        }
      }
    }
  }
}
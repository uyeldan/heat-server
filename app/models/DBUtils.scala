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

import java.sql.PreparedStatement
import java.sql.Connection
import java.sql.SQLException
import play.Logger

object DBUtils {

  def limitsClause(from: Int, to: Int): String = {
    val limit = if (to >=0 && to >= from && to < Integer.MAX_VALUE)
      to - from + 1
    else
      0
    if (limit > 0 && from > 0) " LIMIT ? OFFSET ? "
    else if (limit > 0) " LIMIT ? "
    else if (from > 0)" LIMIT NULL OFFSET ? "
    else ""
  }

  def setLimits(indexx: Int, pstmt: PreparedStatement, from: Int, to: Int): Int = {
    val limit = if (to >=0 && to >= from && to < Integer.MAX_VALUE)
      to - from + 1
    else
      0
    var index = indexx
    if (limit > 0) {
      pstmt.setInt(index, limit)
      index += 1
    }
    if (from > 0) {
      pstmt.setInt(index, from)
      index += 1
    }
    index;
  }

  def getRowCount(conn: Connection, tableName: String) : Int = {
    try {
      val pstmt = conn.prepareStatement(s"SELECT COUNT(*) FROM $tableName")
      val result = pstmt.executeQuery()
      if (result.next()) {
        val output = result.getInt(1)
        Logger.info(s"$tableName count = "+output)
        return output
      }
    } catch {
      case e:SQLException => Logger.error("DBUtils.getRowCount error", e)
      case e:Exception => Logger.error("DBUtils.getRowCount error", e)
    }
    Logger.info(s"$tableName count = ZERO")
    0
  }
}
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

import play.api._
import play.api.Play.current
import play.api.db._
import java.sql.SQLException

trait DBVersion {
  
  def init = {
    DB.withConnection { con =>
      val stmt = con.createStatement();
      var nextUpdate = 0;
      try {
        val rs = stmt executeQuery "SELECT next_update FROM heatserver_version"
        if (!rs.next) throw new RuntimeException("Invalid heatserver_version table")
        nextUpdate = rs.getInt("next_update")
        if (!rs.isLast()) throw new RuntimeException("Invalid heatserver_version table")
      } catch {
        case _ : SQLException => {
          stmt executeUpdate "CREATE TABLE heatserver_version (next_update INT NOT NULL)"
          stmt executeUpdate "INSERT INTO heatserver_version VALUES (0)"
        }
      }
      update(nextUpdate)
    }
  }
  
  def apply(sql: String) = {
    DB.withConnection { con =>
      val stmt = con.createStatement()
      Logger.info(s"Apply sql:\n $sql")
      stmt executeUpdate sql
      stmt executeUpdate "UPDATE heatserver_version SET next_update = next_update + 1"
    }
  }
  
  def update(nextUpdate: Int)
}
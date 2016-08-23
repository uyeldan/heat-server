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

object MySQLDBVersion extends DBVersion {
  
  /* All timestamps use HEAT epoch timestamps. */
  
  override def update(nextUpdate: Int) = {
    val updates = List(
      """
      CREATE TABLE IF NOT EXISTS message (
        id BIGINT NOT NULL AUTO_INCREMENT,
        unread BOOLEAN NOT NULL DEFAULT TRUE,
        recipient_status INT NOT NULL DEFAULT 0,
        sender_status INT NOT NULL DEFAULT 0,
        is_text BOOLEAN NOT NULL DEFAULT TRUE,
        data VARBINARY(42000) NOT NULL,
        nonce BINARY(32) NOT NULL,
        recipient_id BIGINT NOT NULL,
        sender_id BIGINT NOT NULL,
        timestamp INT NOT NULL, 
        reply_to BIGINT NOT NULL DEFAULT 0,
        PRIMARY KEY (id))
      """,
      "CREATE INDEX message_timestamp_idx ON message (timestamp)",
      "CREATE INDEX message_sender_id_idx ON message (sender_id)",
      "CREATE INDEX message_recipient_id_idx ON message (recipient_id)",
      "CREATE INDEX message_reply_to_idx ON message (reply_to)"
    )
    updates.slice(nextUpdate, updates.size).foreach(apply(_))
  }
}


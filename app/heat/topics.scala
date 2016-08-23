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
package heat

import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.json.Json.toJsFieldJsValueWrapper

/**
 * 
 * TRANSACTION TOPICS
 * 
 * [
 *    # Topic type identifier. This first slot always contains the number 101
 *    101,
 *
 *    # The second slot contains an array of arrays, each sub array has the
 *    # transaction type as its first element and the subtype as the second element.
 *    # If no filtering should be performed on matched transaction types leave the
 *    # root array empty.
 *    [[1,1],[2,3],[3,4]],
 *
 *    # The third slot contains the account filter. An account filter will only
 *    # notify about transactions where EITHER sender AND/OR recipient matches
 *    # the provided account. The account can be in RS or numeric notation.
 *    # Pass an empty string to indicate there is no account.
 *    " FIM-Z38B-MAXH-ZHXC-DWXYX",
 *
 *    # The fourth slot contains the recipient filter. A recipient filter will
 *    # only notify about transactions where recipient EQUALS the recipient
 *    # you provided.
 *    # Pass an empty string to indicate there is no recipient.
 *    "",
 *
 *    # The fifth slot contains the sender filter. A sender filter will
 *    # only notify about transactions where sender EQUALS the sender
 *    # you provided.
 *    # Pass an empty string to indicate there is no sender.
 *    ""
 * ]   
 * 
 * EXCHANGE TOPICS 
 * 
 * [
 *    # Topic type identifier. This first slot always contains the number 102
 *    102,
 *
 *    # The second slot contains an asset identifier, if provided only events
 *    # matching this asset will match and will be forwarded.
 *    # Pass an empty string to indicate there is no asset and must match all assets
 *    "1234567890123456789",
 *
 *    # The third slot contains an account number, if provided events will be filtered
 *    # only if either affected buyer or seller is this account.
 *    # Pass an empty string to indicate there is no account.
 *    "FIM-Z38B-MAXH-ZHXC-DWXYX"
 * ]
 */
object topics {
  
  def transactions = {
    Json.arr(
      "subscribe", 
      Json.arr(101, 
        Json.arr(),
        "",
        "",
        ""
      ).toString()
    ).toString()
  }
  
  def exchange = {
    Json.arr(
      "subscribe", 
      Json.arr(102, 
        "",
        ""
      ).toString()
    ).toString()
  }
}
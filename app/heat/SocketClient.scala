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

import java.net.URI
import scala.concurrent.duration._
import io.backchat.hookup._
import models._

object SocketClient {
  
  def init = {
    new SocketClient("heat")
  }
  
  class SocketClient(name: String) extends HookupClient {
    
    val uri = URI.create(Config.constructSocketURL)
    
    val settings: HookupClientConfig = HookupClientConfig(
      uri = uri,
      throttle = IndefiniteThrottle(5 seconds, 30 minutes),
      buffer = None)
      
    def receive = {
      case Connected => {
        println("RECEICE::CONNECTED connected to: %s" format uri.toASCIIString)
        send(topics.exchange)
        send(topics.transactions)           
      }
      case TextMessage(text) => {
        println("%s: %s" format uri.toASCIIString, text);
      }
      case JsonMessage(s) => {
        println("JSON" + s);
      }
    }
    
    connect() onSuccess {
      case _ => {
        println("CONNECT:CONNECT connected to: %s" format uri.toASCIIString)     
        send("ping")
      }
    }      
  }
}

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

import play.api.Play.current

object Config {
  
  def apiURL = current.configuration.getString("heat.api.url") match {
    case Some(value) => value
    case None => "https://robots.mofowallet.org"
  }
  
  def apiPort = current.configuration.getInt("heat.api.port") match {
    case Some(value) => value
    case None => 6887
  }
  
  def socketURL = current.configuration.getString("heat.socket.url") match {
    case Some(value) => value
    case None => "ws://localhost"
  }
  
  def socketPort = current.configuration.getString("heat.socket.port") match {
    case Some(value) => value
    case None => 6986
  }  

  def constructRequestURL(requestType: String) = s"$apiURL:$apiPort/nxt?requestType=$requestType"  
  
  def constructSocketURL() = s"$socketURL:$socketPort/ws/"
  
}
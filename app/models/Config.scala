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

  def feeNQT = current.configuration.getString("heat.api.fee") match {
    case Some(value) => value
    case None => "10_000_000"
  }

  def deadline = current.configuration.getString("heat.api.deadline") match {
    case Some(value) => value
    case None => "1440"
  }

  def reCaptchaSecret = current.configuration.getString("heat.recaptcha.secret") match {
    case Some(value) => value
    case None => "KEPT IN SERVER DEPLOY CONFIG"
  }

  def faucetSecret = current.configuration.getString("heat.faucet.secret") match {
    case Some(value) => value
    case None => "KEPT IN SERVER DEPLOY CONFIG"
  }

  def faucetMessage = current.configuration.getString("heat.faucet.message") match {
    case Some(value) => value
    case None => "Welcome to HEAT testnet! You can use the testnet to create your HEAT initial ICO distribution account (which you obviously did already as you're reading this message) and test out private messaging & sending & receiving test-HEAT. Please be aware that testnet data may be reset at any time, so don't store anything important there."
  }

  def faucetAmount = current.configuration.getString("heat.faucet.amount") match {
    case Some(value) => value
    case None => "100_000_000"
  }

  def apiURL = current.configuration.getString("heat.api.url") match {
    case Some(value) => value
    case None => "https://zombies.mofowallet.org"
  }

  def apiPort = current.configuration.getInt("heat.api.port") match {
    case Some(value) => value
    case None => 8883
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

  // ***************************************************************************
  // ICO Redemption Process
  // ***************************************************************************

  def nxtVerifierSecret = current.configuration.getString("heat.verifier.nxt.secret") match {
    case Some(value) => value
    case None => "KEPT IN SERVER DEPLOY CONFIG"
  }

  def nxtVerifierPort = current.configuration.getInt("heat.verifier.nxt.port") match {
    case Some(value) => value
    case None => 9981
  }

  def nxtVerifierUrl = current.configuration.getString("heat.verifier.nxt.url") match {
    case Some(value) => value
    case None => "https://cloud.mofowallet.org"
  }

  def fimkVerifierSecret = current.configuration.getString("heat.verifier.fimk.secret") match {
    case Some(value) => value
    case None => "KEPT IN SERVER DEPLOY CONFIG"
  }

  def fimkVerifierPort = current.configuration.getInt("heat.verifier.fimk.port") match {
    case Some(value) => value
    case None => 7886
  }

  def fimkVerifierUrl = current.configuration.getString("heat.verifier.fimk.url") match {
    case Some(value) => value
    case None => "https://cloud.mofowallet.org"
  }

  def blockcypherToken = current.configuration.getString("heat.blockcypher.token") match {
    case Some(value) => value
    case None => "KEPT IN SERVER DEPLOY CONFIG"
  }

  def signerBin = current.configuration.getString("heat.signer.bin") match {
    case Some(value) => value
    case None => "/home/heat_server/signer"
  }

  def ethAddress = current.configuration.getString("heat.eth.address") match {
    case Some(value) => value
    case None => "KEPT IN SERVER DEPLOY CONFIG"
  }

  def ethSecret = current.configuration.getString("heat.eth.secret") match {
    case Some(value) => value
    case None => "KEPT IN SERVER DEPLOY CONFIG"
  }

  def ethAddressFile = current.configuration.getString("heat.eth.address.file") match {
    case Some(value) => value
    case None => "/home/dirk/Documents/ETHEREUM_BULK_ADDRESS.json"
  }

  def btcAddress = current.configuration.getString("heat.btc.address") match {
    case Some(value) => value
    case None => "KEPT IN SERVER DEPLOY CONFIG"
  }

  def btcSecret = current.configuration.getString("heat.btc.secret") match {
    case Some(value) => value
    case None => "KEPT IN SERVER DEPLOY CONFIG"
  }

  def btcPublic = current.configuration.getString("heat.btc.public") match {
    case Some(value) => value
    case None => "KEPT IN SERVER DEPLOY CONFIG"
  }

  def btcAddressFile = current.configuration.getString("heat.btc.address.file") match {
    case Some(value) => value
    case None => "/home/dirk/Documents/BITCOIN_BULK_ADDRESS.json"
  }

}
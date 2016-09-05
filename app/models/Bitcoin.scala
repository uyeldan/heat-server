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
import heat.util.Convert
import play.api.libs.functional.syntax._

object Bitcoin {

  /* JSON readers compatible with https://blockchain.info/rawaddr/1HEATQCfWJKPWb8612K2oGR7EE6XPqNYHj?format=json&limit=50&offset=0 */

  case class Transaction(
      time: Long,
      txIndex: Long,
      hash: String,
      inputs: Seq[Input],
      outputs: Seq[Output])

  case class Input(
      addr: String,
      value: JsValue)

  case class Output(
      addr: Option[String],
      value: JsValue)

  implicit val inputReads: Reads[Input] = (
    (JsPath \ "prev_out" \ "addr").read[String] and
    (JsPath \ "prev_out" \ "value").read[JsValue])(Input.apply _)

  implicit val outputReads: Reads[Output] = (
    (JsPath \ "addr").readNullable[String] and
    (JsPath \ "value").read[JsValue])(Output.apply _)

  implicit val transactionReads: Reads[Transaction] = (
    (JsPath \ "time").read[Long] and
    (JsPath \ "tx_index").read[Long] and
    (JsPath \ "hash").read[String] and
    (JsPath \ "inputs").read[Seq[Input]] and
    (JsPath \ "out").read[Seq[Output]])(Transaction.apply _)

  def sendSatoshi(ws: WSClient, recipient: String, amountSatoshi: String) : Future[Option[Boolean]] = {
    createTransaction(ws, recipient, amountSatoshi).flatMap { tx =>
      val obj = tx.as[JsObject]
      val signedTx = signTransaction(obj)
      broadcastTransaction(ws, signedTx.as[JsObject]).flatMap { response =>
        (tx \ "tx" \ "hash").asOpt[String] match {
          case Some(hash) => Future.successful(Some(!hash.isEmpty()))
          case None => Future.successful(None)
        }
      }
    }
  }

  private def createTransaction(ws: WSClient, recipient: String, amountSatoshi: String) : Future[JsValue] = {
    val tx = s"""{
    "inputs":[{
      "addresses": ["${ Config.btcAddress }"]
    }],
    "outputs":[{
      "addresses": ["$recipient"],
      "value": $amountSatoshi
    }]}
    """
    Logger.info("Bitcoin.createTransaction SEND: " + tx)
    val url = s"https://api.blockcypher.com/v1/btc/main/txs/new?token=${Config.blockcypherToken}"
    Logger.info("Bitcoin.createTransaction URL: " + url)
    ws.url(url).post(tx).map { response =>
      Logger.info("Bitcoin.createTransaction RECEIVED: " + response.json.toString())
      response.json
    }
  }

  private def signTransaction(tx: JsObject): JsValue = {
    Logger.info("Bitcoin.signTransaction tx=" + tx.toString())
    val tosign = (tx \ "tosign")(0).get.as[String]
    Logger.info("Bitcoin.signTransaction tosign=" + tosign)
    val command = s"${Config.signerBin} ${tosign} ${Config.btcSecret}"
    Logger.info("Bitcoin.signTransaction command=" + command)
    val signature = CmdExec.exec(command).trim()
    Logger.info("Bitcoin.signTransaction signature=" + signature)
    val result = tx + ("signatures" -> Json.arr(signature)) +
                      ("pubkeys" -> Json.arr(Config.btcPublic))

    Logger.info("Bitcoin.signTransaction result=" + result.toString())
    result
  }

  private def broadcastTransaction(ws: WSClient, tx: JsObject) : Future[JsValue] = {
    Logger.info("Bitcoin.broadcastTransaction SEND: " + tx.toString())
    val url = s"https://api.blockcypher.com/v1/btc/main/txs/send?token=${Config.blockcypherToken}"
    Logger.info("Bitcoin.broadcastTransaction URL: " + url)
    ws.url(url).post(tx).map { response =>
      Logger.info("Bitcoin.broadcastTransaction RECEIVED: " + response.json.toString())
      response.json
    }
  }
}
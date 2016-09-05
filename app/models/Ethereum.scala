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

object Ethereum {

  case class Payment(
      time: String,
      hash: String,
      sender: String,
      amount: JsValue)

  implicit val paymentReads: Reads[Payment] = (
    (JsPath \ "time").read[String] and
    (JsPath \ "hash").read[String] and
    (JsPath \ "sender").read[String] and
    (JsPath \ "amount").read[JsValue])(Payment.apply _)

  def sendWei(ws: WSClient, recipient: String, amountWei: String) : Future[Option[Boolean]] = {
    createTransaction(ws, recipient, amountWei).flatMap { tx =>
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

  private def createTransaction(ws: WSClient, recipient: String, amountWei: String) : Future[JsValue] = {
    val tx = s"""{
    "inputs":[{
      "addresses": ["${Config.ethAddress}"]
    }],
    "outputs":[{
      "addresses": ["$recipient"],
      "value": $amountWei
    }]}
    """
    val url = s"https://api.blockcypher.com/v1/eth/main/txs/new?token=${Config.blockcypherToken}"
    ws.url(url).post(tx).map { response =>
      response.json
    }
  }

  private def signTransaction(tx: JsObject): JsValue = {
    val tosign = (tx \ "tosign")(0).get.as[String]
    val command = s"${Config.signerBin} ${tosign} ${Config.ethSecret}"
    val signature = CmdExec.exec(command).trim()
    tx + ("signatures" -> Json.arr(signature))
  }

  private def broadcastTransaction(ws: WSClient, tx: JsObject) : Future[JsValue] = {
    val url = s"https://api.blockcypher.com/v1/eth/main/txs/send?token=${Config.blockcypherToken}"
    ws.url(url).post(tx).map { response =>
      response.json
    }
  }
}
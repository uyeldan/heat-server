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
package controllers

import play.api.mvc.Action
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.mvc.Action
import play.api.mvc.Controller
import play.api.libs.ws.WSClient
import javax.inject.Inject
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import models.Auth
import models.Ethereum
import models.Bitcoin
import models.BitcoinParticipants
import models.EthereumParticipants
import models.FimkParticipants
import models.NxtParticipants

class IcoClaimController @Inject() (ws: WSClient) extends Controller {

  case class IsICOParticipantRequest(
    sender: String,
    currency: String)

  implicit val isICOParticipantRequestReads: Reads[IsICOParticipantRequest] = (
    (JsPath \ "sender").read[String] and
    (JsPath \ "currency").read[String])(IsICOParticipantRequest.apply _)

  def icoPaymentCount = Action(parse.json) { request =>
    Auth.getAccountId(request.body) match {
      case Some(accountId) => {
        request.body.validate[IsICOParticipantRequest] match {
          case r: JsSuccess[IsICOParticipantRequest] => {
            val req = r.get
            val count = req.currency match {
              case "BTC" => BitcoinParticipants.count(req.sender)
              case "ETH" => EthereumParticipants.count(req.sender)
              case "FIMK" => FimkParticipants.count(req.sender)
              case "NXT" => NxtParticipants.count(req.sender)
              case _default => None
            }
            count match {
              case Some(c) => Ok(Json.obj("success" -> true, "count" -> c))
              case None => Ok(Json.obj("success" -> false, "message" -> "Could not get payments from database"))
            }
          }
          case e: JsError => Ok(Json.obj("success" -> false, "message" -> "Invalid JSON args"))
        }
      }
      case None => Ok(Json.obj("success" -> false, "message" -> "Access denied"))
    }
  }

}
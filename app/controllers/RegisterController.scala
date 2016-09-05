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

import scala.concurrent.Future

import javax.inject.Inject
import models.Config
import models.Auth
import models.Recaptcha
import models.PlayCrypto
import models.ReplicatorPublicKey
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.functional.syntax.functionalCanBuildApplicative
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.JsError
import play.api.libs.json.JsPath
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.Reads
import play.api.libs.ws.WSClient
import play.api.mvc.Action
import play.api.mvc.Controller
import play.api.Logger
import heat.util.Convert
import models.HEAT

class RegisterController @Inject() (ws: WSClient) extends Controller {

  case class RegisterRequest(
    publicKey: String,
    captcha: String)

  implicit val registerRequestReads: Reads[RegisterRequest] = (
    (JsPath \ "publicKey").read[String] and
    (JsPath \ "captcha").read[String])(RegisterRequest.apply _)

  def register = Action.async(parse.json) { implicit request =>
    request.body.validate[RegisterRequest] match {
      case r: JsSuccess[RegisterRequest] => {
        val req = r.get
        verifyCaptcha(req.captcha, request.remoteAddress).flatMap { captchaVerified =>
          if (captchaVerified) {
            val accountId = PlayCrypto.publicKeyToAccountId(Convert.parseHexString(req.publicKey))
            ReplicatorPublicKey.find(accountId) match {
              case Some(publicKey) => Future.successful(Ok(Json.obj("success" -> false, "message" -> "Public key already exists")))
              case None => {
                registerNewAccount(req).flatMap { success =>
                  if (success)
                    Future.successful(Ok(Json.obj("success" -> true)))
                  else
                    Future.successful(Ok(Json.obj("success" -> false, "message" -> "Registration failed")))
                }
              }
            }
          }
          else Future.successful(Ok(Json.obj("success" -> false, "message" -> "Invalid captcha challenge")))
        }
      }
      case e: JsError => Future.successful(Ok(Json.obj("success" -> false, "message" -> "Invalid JSON args")))
    }
  }

  def verifyCaptcha(captcha: String, remoteAddress: String): Future[Boolean] = {
    Recaptcha.verify(ws, captcha, remoteAddress).map { resp =>
      resp match {
        case e: JsError => false
        case s: JsSuccess[Recaptcha.Response] => s.get.success
      }
    }
  }

  def registerNewAccount(model: RegisterRequest): Future[Boolean] = {
    HEAT.sendMoney(ws, model.publicKey, Config.faucetAmount, Config.faucetMessage).map { response =>
      response match {
        case Some(transactionId) => true
        case None => false
      }
    }
  }
}
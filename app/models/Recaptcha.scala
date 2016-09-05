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

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.functional.syntax.functionalCanBuildApplicative
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.JsPath
import play.api.libs.json.JsResult
import play.api.libs.json.Reads
import play.api.libs.json.Reads.BooleanReads
import play.api.libs.json.Reads.StringReads
import play.api.libs.json.Reads.functorReads
import play.api.libs.ws.WSClient
import play.Logger

object Recaptcha {

  case class Response(
    success: Boolean,
    hostname: String
  )

  implicit val responceReads: Reads[Response] = (
    (JsPath \ "success").read[Boolean] and
    (JsPath \ "hostname").read[String]
  )(Response.apply _)

  def verify(ws: WSClient, captcha: String, remoteip: String): Future[JsResult[Response]] = {
    ws.url("https://www.google.com/recaptcha/api/siteverify").
       post(Map(
         "secret" -> Seq(Config.reCaptchaSecret),
         "response" -> Seq(captcha),
         "remoteip" -> Seq(remoteip)
       )).
       map({ response =>
         response.json.validate[Response]
       })
  }
}
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

import models.ReplicatorPayment
import heat.util.Convert
import play.api.libs.functional.syntax.functionalCanBuildApplicative
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.JsError
import play.api.libs.json.JsPath
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.Reads
import play.api.mvc.Action
import play.api.mvc.Controller

class PaymentController extends Controller {
  
  case class PaymentListRequest(
    accountRS: String,
    firstIndex: Long,
    lastIndex: Long,
    sortColumn: String,
    sortAsc: Boolean)

  implicit val paymentListRequestReads: Reads[PaymentListRequest] = (
    (JsPath \ "accountRS").read[String] and
    (JsPath \ "firstIndex").read[Long] and
    (JsPath \ "lastIndex").read[Long] and
    (JsPath \ "sortColumn").read[String] and
    (JsPath \ "sortAsc").read[Boolean])(PaymentListRequest.apply _)  
    
  case class PaymentCountRequest(
    accountRS: String)
  
  implicit val paymentCountRequestReads: Reads[PaymentCountRequest] = 
    (JsPath \ "accountRS").read[String].map { accountRS => PaymentCountRequest(accountRS) }

  def list = Action(parse.json) { request =>
    request.body.validate[PaymentListRequest] match {
      case r: JsSuccess[PaymentListRequest] => {
        val req = r.get
        val id = Convert.parseAccountId(req.accountRS)
        ReplicatorPayment.list(id, req.sortColumn, req.sortAsc, req.firstIndex.toInt, req.lastIndex.toInt) match {
          case Some(payments) => Ok(Json.obj("payments" -> payments.map { payment => ReplicatorPayment.toJson(payment) }))
          case None => Ok(Json.obj("success" -> false, "message" -> "Could not get payments from database"))
        }          
      }
      case e: JsError => Ok(Json.obj("success" -> false, "message" -> "Invalid JSON args"))
    }
  }
  
  def count = Action(parse.json) { request =>
    request.body.validate[PaymentCountRequest] match {
      case r: JsSuccess[PaymentCountRequest] => {
        val req = r.get
        val id = Convert.parseAccountId(req.accountRS)     
        ReplicatorPayment.count(id) match {
          case Some(count) => Ok(Json.obj("count" -> count))
          case None => Ok(Json.obj("success" -> false, "message" -> "Could not get payments count from database"))
        }
      }
      case e: JsError => Ok(Json.obj("success" -> false, "message" -> "Invalid JSON args"))
    }
  }
}
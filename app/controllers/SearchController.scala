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
import play.api.mvc.Controller
import play.api.libs.json._
import play.api.libs.functional.syntax._
import models.ReplicatorIdentifier
import heat.util.Convert
import models.ReplicatorPayment
import models.ReplicatorPublicKey
import models.ReplicatorAccount

class SearchController extends Controller {
  
  case class SearchIdentifierRequest(
    query: String,
    accountColorId: String,
    firstIndex: Long,
    lastIndex: Long,
    requirePublicKey: Boolean)

  implicit val searchIdentifierRequestReads: Reads[SearchIdentifierRequest] = (
    (JsPath \ "query").read[String] and
    (JsPath \ "accountColorId").read[String] and
    (JsPath \ "firstIndex").read[Long] and
    (JsPath \ "lastIndex").read[Long] and
    (JsPath \ "requirePublicKey").read[Boolean])(SearchIdentifierRequest.apply _)    
  
  def identifier = Action(parse.json) { request =>
    request.body.validate[SearchIdentifierRequest] match {
      case r: JsSuccess[SearchIdentifierRequest] => {
        val req = r.get
        val colorId = java.lang.Long.parseUnsignedLong(req.accountColorId)
        ReplicatorIdentifier.find(req.query, req.requirePublicKey, colorId, req.firstIndex.toInt, req.lastIndex.toInt) match {
          case Some(accounts) => Ok(Json.obj("accounts" -> accounts.map { account => ReplicatorIdentifier.toJson(account) }))
          case None => Ok(Json.obj("success" -> false, "message" -> "Could not get accounts from database"))
        }
      }
      case e: JsError => Ok(Json.obj("success" -> false, "message" -> "Invalid JSON args"))
    }
  }
  
  case class SearchPublickeyRequest(accountRS: String)
  implicit val searchPublickeyRequestReads = 
    (JsPath \ "accountRS").read[String].map( accountRS => SearchPublickeyRequest(accountRS) )    
  
  def publickey = Action(parse.json) { request =>
    request.body.validate[SearchPublickeyRequest] match {
      case r: JsSuccess[SearchPublickeyRequest] => {
        val req = r.get
        val accountId = Convert.parseAccountId(req.accountRS)
        ReplicatorPublicKey.find(accountId) match {
          case Some(publicKey) => Ok(Json.obj("publicKey" -> Convert.toHexString(publicKey)))
          case None => Ok(Json.obj("success" -> false, "message" -> "Could not get public key from database"))
        }
      }
      case e: JsError => Ok(Json.obj("success" -> false, "message" -> "Invalid JSON args"))
    }
  }  
  
  case class SearchAccountRequest(accountRS: String)
  implicit val searchAccountRequestReads = 
    (JsPath \ "accountRS").read[String].map( accountRS => SearchAccountRequest(accountRS) )    
  
  def account = Action(parse.json) { request =>
    request.body.validate[SearchAccountRequest] match {
      case r: JsSuccess[SearchAccountRequest] => {
        val req = r.get
        val accountId = Convert.parseAccountId(req.accountRS)
        ReplicatorAccount.find(accountId) match {
          case Some(model) => Ok(ReplicatorAccount.toJson(model))
          case None => Ok(Json.obj("success" -> false, "message" -> "Could not get account from database"))
        }
      }
      case e: JsError => Ok(Json.obj("success" -> false, "message" -> "Invalid JSON args"))
    }
  }  
}
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

import scala.concurrent.{ Future, Promise }

import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.Play.current
import play.api.db._
import play.api.libs.ws._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import javax.inject.Inject
import scala.concurrent.Future
import models._
import heat.util.Convert

class MessagingController extends Controller {
  
  case class MessagingListRequest(
    accountRS: String,
    firstIndex: Long,
    lastIndex: Long,
    sortColumn: String,
    sortAsc: Boolean)

  implicit val messagingListRequestReads: Reads[MessagingListRequest] = (
    (JsPath \ "accountRS").read[String] and
    (JsPath \ "firstIndex").read[Long] and
    (JsPath \ "lastIndex").read[Long] and
    (JsPath \ "sortColumn").read[String] and
    (JsPath \ "sortAsc").read[Boolean])(MessagingListRequest.apply _)    
  
  def list = Action(parse.json) { request =>
    Auth.getAccountId(request.body) match {
      case Some(accountId) => {
        request.body.validate[MessagingListRequest] match {
          case r: JsSuccess[MessagingListRequest] => {
            val req = r.get
            val otherAccountId = Convert.parseAccountId(req.accountRS)          
            if (!accountId.equals(0)) {
              ReplicatorMessage.list(req.sortColumn, req.sortAsc, req.firstIndex.toInt, req.lastIndex.toInt, accountId, otherAccountId) match {
                case Some(messages) => Ok(Json.obj("messages" -> messages.map { message => ReplicatorMessage.toJson(message) }))
                case None => Ok(Json.obj("success" -> false, "message" -> "Could not get messages from database"))
              }          
            }
            else Ok(Json.obj("success" -> false, "message" -> "Access denied"))
          }
          case e: JsError => Ok(Json.obj("success" -> false, "message" -> "Invalid JSON args"))
        }
      }
      case None => Ok(Json.obj("success" -> false, "message" -> "Access denied"))
    }
  }
  
  case class MessagingCountRequest(
    accountRS: String,
    otherAccountRS: String)
    
  implicit val messagingCountRequestReads: Reads[MessagingCountRequest] = (
    (JsPath \ "accountRS").read[String] and
    (JsPath \ "otherAccountRS").read[String])(MessagingCountRequest.apply _)        
  
  def count = Action(parse.json) { request =>
    Auth.getAccountId(request.body) match {
      case Some(accountId) => {
        request.body.validate[MessagingCountRequest] match {
          case r: JsSuccess[MessagingCountRequest] => {
            val req = r.get
            val id = Convert.parseAccountId(req.accountRS)          
            if (accountId.equals(id)) {              
              val otherAccountId = Convert.parseAccountId(req.otherAccountRS)              
              ReplicatorMessage.count(id, otherAccountId) match {
                case Some(count) => {
                  Ok(Json.obj(
                    "unread" -> count.unread,
                    "sent" -> count.sent,
                    "received" -> count.received,
                    "total" -> count.total
                  ))
                }
                case None => Ok(Json.obj("success" -> false, "message" -> "Could not get messaging count from database"))
              }
            }
            else Ok(Json.obj("success" -> false, "message" -> "Access denied"))
          }
          case e: JsError => Ok(Json.obj("success" -> false, "message" -> "Invalid JSON args"))
        } 
      }
      case None => Ok(Json.obj("success" -> false, "message" -> "Access denied"))
    }
  }
  
  case class MessagingFindRequest(
    id: String)
    
  implicit val messagingFindRequestReads: Reads[MessagingFindRequest] = 
    (JsPath \ "id").read[String].map { id => MessagingFindRequest(id) }    
    
  def find = Action(parse.json) { request =>
    Auth.getAccountId(request.body) match {
      case Some(accountId) => {
        request.body.validate[MessagingFindRequest] match {
          case r: JsSuccess[MessagingFindRequest] => {
            val req = r.get
            val id = java.lang.Long.parseUnsignedLong(req.id)
            ReplicatorMessage.find(id) match {
              case Some(message) => {
                if (message.recipientId == accountId || message.senderId == accountId) Ok(ReplicatorMessage.toJson(message))
                else Ok(Json.obj("success" -> false, "message" -> "Access denied"))
              }
              case None => Ok(Json.obj("success" -> false, "message" -> "Could not find message"))
            }
          }
          case e: JsError => Ok(Json.obj("success" -> false, "message" -> "Invalid JSON args"))
        }        
      }
      case None => Ok(Json.obj("success" -> false, "message" -> "Access denied"))
    }
  }  
  
  case class MessagingSaveRequest(
    senderRS: String,
    recipientRS: String,
    isText: Boolean,
    data: String,
    nonce: String)  
    
  implicit val messagingSaveRequestReads: Reads[MessagingSaveRequest] = (
    (JsPath \ "senderRS").read[String] and
    (JsPath \ "recipientRS").read[String] and
    (JsPath \ "isText").read[Boolean] and
    (JsPath \ "data").read[String] and
    (JsPath \ "nonce").read[String])(MessagingSaveRequest.apply _)  
  
  def save = Action(parse.json) { request =>
    Auth.getAccountId(request.body) match {
      case Some(accountId) => {
        request.body.validate[MessagingSaveRequest] match {
          case r: JsSuccess[MessagingSaveRequest] => {
            val req = r.get
            val senderId = Convert.parseAccountId(req.senderRS)
            if (accountId.equals(senderId)) {
              val recipientId = Convert.parseAccountId(req.recipientRS)
              val model = ReplicatorMessage.create(senderId, recipientId, req.isText, req.data, req.nonce)
              val autoIncr = ReplicatorMessage.save(model)
              if (autoIncr >= 0) {
                Ok(Json.obj("success" -> true, "id" -> java.lang.Long.toUnsignedString(autoIncr)))
              }
              else Ok(Json.obj("success" -> false, "message" -> "Could not save message to database"))
            }
            else Ok(Json.obj("success" -> false, "message" -> "Access denied"))
          }
          case e: JsError => Ok(Json.obj("success" -> false, "message" -> "Invalid JSON args"))
        }        
      }
      case None => Ok(Json.obj("success" -> false, "message" -> "Access denied"))
    }
  }
  
  case class MessagingUpdateUnreadRequest(
    id: String,
    unread: Boolean)
    
  implicit val messagingUpdateUnreadRequestReads: Reads[MessagingUpdateUnreadRequest] = (
    (JsPath \ "id").read[String] and
    (JsPath \ "unread").read[Boolean])(MessagingUpdateUnreadRequest.apply _)
  
  def updateUnread = Action(parse.json) { request =>
    Auth.getAccountId(request.body) match {
      case Some(accountId) => {
        request.body.validate[MessagingUpdateUnreadRequest] match {
          case r: JsSuccess[MessagingUpdateUnreadRequest] => {
            val req = r.get
            val id = java.lang.Long.parseUnsignedLong(req.id)
            ReplicatorMessage.find(id) match {
              case Some(model) => {
                //confirm the message was sent to accountId
                if (model.recipientId == accountId) {
                  val table = if (model.blockchain) "replicator_message" else "message"
                    val pkColumn = if (model.blockchain) "transaction_id" else "id"
                  if (ReplicatorMessage.update_unread(id, req.unread, table, pkColumn)) {
                    Ok(Json.obj("success" -> true))
                  }
                  else Ok(Json.obj("success" -> false, "message" -> "Could not update message in database"))
                }
                else Ok(Json.obj("success" -> false, "message" -> "Access denied, message does not belong to you"))
              }
              case None => Ok(Json.obj("success" -> false, "message" -> "Could not find message in database"))
            }
          }
          case e: JsError => Ok(Json.obj("success" -> false, "message" -> "Invalid JSON args"))
        }        
      }
      case None => Ok(Json.obj("success" -> false, "message" -> "Access denied"))
    }
  }  
  
  case class MessagingFlagRequest(
    id: String,
    flag: Long)
    
  implicit val messagingFlagRequestReads: Reads[MessagingFlagRequest] = (
    (JsPath \ "id").read[String] and
    (JsPath \ "flag").read[Long])(MessagingFlagRequest.apply _)
  
  def setFlag = Action(parse.json) { request =>
    Auth.getAccountId(request.body) match {
      case Some(accountId) => {
        request.body.validate[MessagingFlagRequest] match {
          case r: JsSuccess[MessagingFlagRequest] => {
            val req = r.get
            val id = java.lang.Long.parseUnsignedLong(req.id)
            ReplicatorMessage.find(id) match {
              case Some(model) => {
                val table = if (model.blockchain) "replicator_message" else "message"
                val pkColumn = if (model.blockchain) "transaction_id" else "id"
                if (model.recipientId == accountId) {
                  ReplicatorMessage.setFlag(id, req.flag.toInt, table, pkColumn, true) match {
                    case Some(status) => Ok(Json.obj("success" -> true, "status" -> status))
                    case None => Ok(Json.obj("success" -> false, "message" -> "Could not set flag"))
                  }
                }
                else if (model.senderId == accountId) {
                  ReplicatorMessage.setFlag(id, req.flag.toInt, table, pkColumn, false) match {
                    case Some(status) => Ok(Json.obj("success" -> true, "status" -> status))
                    case None => Ok(Json.obj("success" -> false, "message" -> "Could not set flag"))
                  }
                }
                else Ok(Json.obj("success" -> false, "message" -> "Access denied, message does not belong to you"))
              }
              case None => Ok(Json.obj("success" -> false, "message" -> "Could not find message in database"))
            }
          }
          case e: JsError => Ok(Json.obj("success" -> false, "message" -> "Invalid JSON args"))
        }        
      }
      case None => Ok(Json.obj("success" -> false, "message" -> "Access denied"))
    }
  }    
  
  def resetFlag = Action(parse.json) { request =>
    Auth.getAccountId(request.body) match {
      case Some(accountId) => {
        request.body.validate[MessagingFlagRequest] match {
          case r: JsSuccess[MessagingFlagRequest] => {
            val req = r.get
            val id = java.lang.Long.parseUnsignedLong(req.id)
            ReplicatorMessage.find(id) match {
              case Some(model) => {
                val table = if (model.blockchain) "replicator_message" else "message"
                val pkColumn = if (model.blockchain) "transaction_id" else "id"
                if (model.recipientId == accountId) {
                  ReplicatorMessage.resetFlag(id, req.flag.toInt, table, pkColumn, true) match {
                    case Some(status) => Ok(Json.obj("success" -> true, "status" -> status))
                    case None => Ok(Json.obj("success" -> false, "message" -> "Could not reset flag"))
                  }
                }
                else if (model.senderId == accountId) {
                  ReplicatorMessage.resetFlag(id, req.flag.toInt, table, pkColumn, false) match {
                    case Some(status) => Ok(Json.obj("success" -> true, "status" -> status))
                    case None => Ok(Json.obj("success" -> false, "message" -> "Could not reset flag"))
                  }
                }
                else Ok(Json.obj("success" -> false, "message" -> "Access denied, message does not belong to you"))
              }
              case None => Ok(Json.obj("success" -> false, "message" -> "Could not find message in database"))
            }
          }
          case e: JsError => Ok(Json.obj("success" -> false, "message" -> "Invalid JSON args"))
        }        
      }
      case None => Ok(Json.obj("success" -> false, "message" -> "Access denied"))
    }
  }    
  
  case class LatestListRequest(
    accountRS: String,
    firstIndex: Long,
    lastIndex: Long)

  implicit val latestListRequestReads: Reads[LatestListRequest] = (
    (JsPath \ "accountRS").read[String] and
    (JsPath \ "firstIndex").read[Long] and
    (JsPath \ "lastIndex").read[Long])(LatestListRequest.apply _)     
  
  def latest = Action(parse.json) { request =>
    Auth.getAccountId(request.body) match {
      case Some(accountId) => {
        request.body.validate[LatestListRequest] match {
          case r: JsSuccess[LatestListRequest] => {
            val req = r.get
            val id = Convert.parseAccountId(req.accountRS)
            if (accountId.equals(id)) {
              ReplicatorMessage.latest(id, req.firstIndex.toInt, req.lastIndex.toInt) match {
                case Some(latest) => Ok(
                  Json.obj(
                    "latest" -> latest.map { latest => 
                      Json.obj(
                        "accountRS" -> Convert.rsAccount(latest.accountId),
                        "accountName" -> latest.accountName,
                        "accountEmail" -> latest.accountEmail,
                        "accountPublicKey" -> Convert.toHexString(latest.accountPublicKey),
                        "unread" -> latest.unreadCount,
                        "latestTimestamp" -> latest.latestTimestamp                      
                      )
                    }
                  )
                )
                case None => Ok(Json.obj("success" -> false, "message" -> "Could not get latest from database"))
              }
            }
            else Ok(Json.obj("success" -> false, "message" -> "Access denied"))
          }
          case e: JsError => Ok(Json.obj("success" -> false, "message" -> "Invalid JSON args"))
        }
      }
      case None => Ok(Json.obj("success" -> false, "message" -> "Access denied"))
    }
  }
  
  case class MessagingInboxRequest(
    firstIndex: Long,
    lastIndex: Long,
    sortColumn: String,
    sortAsc: Boolean,
    accountRS: String)

  implicit val messagingInboxRequestReads: Reads[MessagingInboxRequest] = (
    (JsPath \ "firstIndex").read[Long] and
    (JsPath \ "lastIndex").read[Long] and
    (JsPath \ "sortColumn").read[String] and
    (JsPath \ "sortAsc").read[Boolean] and
    (JsPath \ "accountRS").read[String])(MessagingInboxRequest.apply _)    
  
  def inbox = Action(parse.json) { request =>
    Auth.getAccountId(request.body) match {
      case Some(accountId) => {
        request.body.validate[MessagingInboxRequest] match {
          case r: JsSuccess[MessagingInboxRequest] => {
            val req = r.get
            val otherAccountId = Convert.parseAccountId(req.accountRS)
            ReplicatorMessage.inbox(req.sortColumn, req.sortAsc, req.firstIndex.toInt, 
                req.lastIndex.toInt, accountId, otherAccountId) match {
              case Some(messages) => Ok(Json.obj("messages" -> messages.map { message => ReplicatorMessage.toJson(message) }))
              case None => Ok(Json.obj("success" -> false, "message" -> "Could not get inbox from database"))
            }          
          }
          case e: JsError => Ok(Json.obj("success" -> false, "message" -> "Invalid JSON args"))
        }
      }
      case None => Ok(Json.obj("success" -> false, "message" -> "Access denied"))
    }
  }  
  
  case class MessagingInboxCountRequest(
    accountRS: String)
    
  implicit val messagingInboxCountRequestReads: Reads[MessagingInboxCountRequest] = 
    (JsPath \ "accountRS").read[String].map { accountRS => MessagingInboxCountRequest(accountRS) }      
    
  def inboxCount = Action(parse.json) { request =>
    Auth.getAccountId(request.body) match {
      case Some(accountId) => {
        request.body.validate[MessagingInboxCountRequest] match {
          case r: JsSuccess[MessagingInboxCountRequest] => {
            val req = r.get
            val otherAccountId = Convert.parseAccountId(req.accountRS)
            ReplicatorMessage.inboxCount(accountId, otherAccountId) match {
              case Some(count) => Ok(Json.obj( "count" -> count ))
              case None => Ok(Json.obj("success" -> false, "message" -> "Could not get count"))
            }
          }
          case e: JsError => Ok(Json.obj("success" -> false, "message" -> "Invalid JSON args"))
        }        
      }
      case None => Ok(Json.obj("success" -> false, "message" -> "Access denied"))
    }
  }    
  
  case class MessagingOutboxRequest(
    firstIndex: Long,
    lastIndex: Long,
    sortColumn: String,
    sortAsc: Boolean,
    accountRS: String)

  implicit val messagingOutboxRequestReads: Reads[MessagingOutboxRequest] = (
    (JsPath \ "firstIndex").read[Long] and
    (JsPath \ "lastIndex").read[Long] and
    (JsPath \ "sortColumn").read[String] and
    (JsPath \ "sortAsc").read[Boolean] and
    (JsPath \ "accountRS").read[String])(MessagingOutboxRequest.apply _)    
  
  def outbox = Action(parse.json) { request =>
    Auth.getAccountId(request.body) match {
      case Some(accountId) => {
        request.body.validate[MessagingOutboxRequest] match {
          case r: JsSuccess[MessagingOutboxRequest] => {
            val req = r.get
            val otherAccountId = Convert.parseAccountId(req.accountRS)
            ReplicatorMessage.outbox(req.sortColumn, req.sortAsc, req.firstIndex.toInt, 
                req.lastIndex.toInt, accountId, otherAccountId) match {
              case Some(messages) => Ok(Json.obj("messages" -> messages.map { message => ReplicatorMessage.toJson(message) }))
              case None => Ok(Json.obj("success" -> false, "message" -> "Could not get outbox from database"))
            }          
          }
          case e: JsError => Ok(Json.obj("success" -> false, "message" -> "Invalid JSON args"))
        }
      }
      case None => Ok(Json.obj("success" -> false, "message" -> "Access denied"))
    }
  }  
  
  case class MessagingOutboxCountRequest(
    accountRS: String)
    
  implicit val messagingOutboxCountRequestReads: Reads[MessagingOutboxCountRequest] = 
    (JsPath \ "accountRS").read[String].map { accountRS => MessagingOutboxCountRequest(accountRS) }      
    
  def outboxCount = Action(parse.json) { request =>
    Auth.getAccountId(request.body) match {
      case Some(accountId) => {
        request.body.validate[MessagingOutboxCountRequest] match {
          case r: JsSuccess[MessagingOutboxCountRequest] => {
            val req = r.get
            val otherAccountId = Convert.parseAccountId(req.accountRS)
            ReplicatorMessage.outboxCount(accountId, otherAccountId) match {
              case Some(count) => Ok(Json.obj( "count" -> count ))
              case None => Ok(Json.obj("success" -> false, "message" -> "Could not get count"))
            }
          }
          case e: JsError => Ok(Json.obj("success" -> false, "message" -> "Invalid JSON args"))
        }        
      }
      case None => Ok(Json.obj("success" -> false, "message" -> "Access denied"))
    }
  }   
  
 case class MessagingTrashedRequest(
    firstIndex: Long,
    lastIndex: Long,
    sortColumn: String,
    sortAsc: Boolean,
    accountRS: String)

  implicit val messagingTrashedRequestReads: Reads[MessagingTrashedRequest] = (
    (JsPath \ "firstIndex").read[Long] and
    (JsPath \ "lastIndex").read[Long] and
    (JsPath \ "sortColumn").read[String] and
    (JsPath \ "sortAsc").read[Boolean] and
    (JsPath \ "accountRS").read[String])(MessagingTrashedRequest.apply _)    
  
  def trashed = Action(parse.json) { request =>
    Auth.getAccountId(request.body) match {
      case Some(accountId) => {
        request.body.validate[MessagingTrashedRequest] match {
          case r: JsSuccess[MessagingTrashedRequest] => {
            val req = r.get
            val otherAccountId = Convert.parseAccountId(req.accountRS)
            ReplicatorMessage.trashed(req.sortColumn, req.sortAsc, req.firstIndex.toInt, 
                req.lastIndex.toInt, accountId, otherAccountId) match {
              case Some(messages) => Ok(Json.obj("messages" -> messages.map { message => ReplicatorMessage.toJson(message) }))
              case None => Ok(Json.obj("success" -> false, "message" -> "Could not get trashed from database"))
            }          
          }
          case e: JsError => Ok(Json.obj("success" -> false, "message" -> "Invalid JSON args"))
        }
      }
      case None => Ok(Json.obj("success" -> false, "message" -> "Access denied"))
    }
  }  
  
  case class MessagingTrashedCountRequest(
    accountRS: String)
    
  implicit val messagingTrashedCountRequestReads: Reads[MessagingTrashedCountRequest] = 
    (JsPath \ "accountRS").read[String].map { accountRS => MessagingTrashedCountRequest(accountRS) }      
    
  def trashedCount = Action(parse.json) { request =>
    Auth.getAccountId(request.body) match {
      case Some(accountId) => {
        request.body.validate[MessagingTrashedCountRequest] match {
          case r: JsSuccess[MessagingTrashedCountRequest] => {
            val req = r.get
            val otherAccountId = Convert.parseAccountId(req.accountRS)
            ReplicatorMessage.trashedCount(accountId, otherAccountId) match {
              case Some(count) => Ok(Json.obj( "count" -> count ))
              case None => Ok(Json.obj("success" -> false, "message" -> "Could not get count"))
            }
          }
          case e: JsError => Ok(Json.obj("success" -> false, "message" -> "Invalid JSON args"))
        }        
      }
      case None => Ok(Json.obj("success" -> false, "message" -> "Access denied"))
    }
  }     
  
}
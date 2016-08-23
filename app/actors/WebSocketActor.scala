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
package actors

import akka.actor._
import play.Logger
import scala.concurrent.Future
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Play.current
import models._
import rpc._

/* 
 * Transport messages are always of the following form:
 * 
 * {
 *   op: String[subscribe,unsubscribe,call]
 *   data : {
 *   
 *     // in case of subscribe AND unsubscribe
 *     topic: String,
 *     params: { STRING, STRING }
 *   
 *   	 // in case of call
 *     method: String,
 *     callId: String,
 *     params: ANY (this depends on what the method expects)
 *   
 *   }
 * }
 */

/* Each topic an actor subscribes to is wrapped in a Subscription object */
case class WebSocketSubscription(
  topic: String,
  params: Map[String, String]
)

/* When pushing data to clients these are wrapped as WebSocketActorEvent and 
 * passed to clients through WebSocketActorEvent.push */
case class WebSocketActorEvent(
  accountId: Long,
  topic: String,
  params: Map[String, String],
  data: JsValue
)

case class WebSocketSubscribeMessage(
  topic: String,
  params: Map[String, String]
)

case class WebSocketUnsubscribeMessage(
  topic: String,
  params: Map[String, String]
)

case class WebSocketCallMessage(
  method: String,
  callId: String,
  params: JsValue
)

/* Singleton that holds all active actors, to push data to an actor create an 
 * TransportActorEvent and pass it to TransportActorManager.push */
object WebSocketActorManager {
  
  var actors: Array[WebSocketActor] = Array()
  
  def add(actor: WebSocketActor) = {
    actors = actors :+ actor
  }
  
  def remove(actor: WebSocketActor) = {
    actors = actors.filter( _.equals(actor) )
  }
  
  def push(event: WebSocketActorEvent): Unit = {
    actors.foreach({ actor =>
      actor.pushIfWanted(event)
    })
  }  
}

object WebSocketActor {
  def props(out: ActorRef, accountId: Long) = Props(new WebSocketActor(out, accountId))
}

class WebSocketActor(out: ActorRef, accountId: Long) extends Actor {

  /* Array of subscriptions that this actor is subscribed to */
  var subscriptions: Array[WebSocketSubscription] = Array()
  
  WebSocketActorManager.add(this)
  
  override def postStop = {
    WebSocketActorManager.remove(this)
  }  
  
  def readMessage(json: JsValue): Any = {
    val operation = (json \ "op").as[String]
    if ("subscribe".equals(operation))
      WebSocketSubscribeMessage(
        (json \ "data" \ "topic").as[String],
        (json \ "data" \ "params").as[Map[String, String]]
      )
    else if ("unsubscribe".equals(operation)) 
      WebSocketUnsubscribeMessage(
        (json \ "data" \ "topic").as[String],
        (json \ "data" \ "params").as[Map[String, String]]
      )
    else if ("call".equals(operation))
      WebSocketCallMessage(
        (json \ "data" \ "method").as[String],
        (json \ "data" \ "callId").as[String],
        (json \ "data" \ "params").get
      )
    else null
  }  
  
  def receive = {
    case msg: String => {
      Logger.info("Received STRING from client " + msg)
    }
    case json: JsValue => {
      
      Logger.info("Received JSON from client + json")
      
      readMessage(json) match {
        case m: WebSocketSubscribeMessage => {
          Logger.info("Received subscribe " + json.toString())
          subscribe(WebSocketSubscription(m.topic, m.params))
        }
        case m: WebSocketUnsubscribeMessage => {
          Logger.info("Received unsubscribe " + json.toString())
          unsubscribe(WebSocketSubscription(m.topic, m.params))
        }
        case m: WebSocketCallMessage => {
          Logger.info("Received call " + json.toString())
          RPCCallRegistry.invoke(m.method, m.params, accountId) match {
            /* RPC returns a future or Nothing */
            case Left(future) => {
              
              Logger.info("Got back a Future " + json.toString())
              
              future.map( _ match {
                case Some(v) => {
                  
                  Logger.info("Sending back data from call")
                  
                  out ! Json.obj(
                    "op" -> "response",
                    "callId" -> m.callId,
                    "data" -> v
                  )
                }
                case None => None
              })
            }
            /* RPC returns a jsval or Nothing */
            case Right(jsval) => {
              
              Logger.info("Got back a jsval " + json.toString())
              
              jsval match {
                case Some(v) => {
                  
                  Logger.info("Sending back data from call")
                  
                  out ! Json.obj(
                    "op" -> "response",
                    "callId" -> m.callId,
                    "data" -> v
                  )
                }
                case None => None
              }
            }
          }
        }
      }
    }
  }  
  
  def pushIfWanted(event: WebSocketActorEvent): Unit = {
    if (event.accountId == 0 || event.accountId == accountId) {
      if (subscriptions.exists(x => {
            x.topic.equals(event.topic) && matchMaps(x.params, event.params) 
          })) 
      {
        out ! Json.obj(
          "op" -> "event",
          "topic" -> event.topic,
          "params" -> event.params,
          "data" -> event.data
        )
      }
    }    
  }
  
  def subscribe(subscription: WebSocketSubscription) = {
    unsubscribe(subscription)
    subscriptions = subscriptions :+ subscription
  }
  
  def unsubscribe(subscription: WebSocketSubscription) = {
    subscriptions = subscriptions.filter({ sub =>
      sub.topic.equals(subscription.topic) && matchMaps(sub.params, subscription.params)
    })
  }
  
  def matchMaps(map1: Map[String, String], map2: Map[String, String]): Boolean = {
    if (map1.isEmpty && map2.isEmpty) true
    else if (map1.isEmpty != map2.isEmpty) false
    else 
      map1.forall { case (key, value) => 
        map2.get(key) match {
          case Some(propVal) => propVal == value
          case None => false
        }  
      }
  } 
}

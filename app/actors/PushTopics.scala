package actors

import play.api.libs.json._
import heat.util.Convert
import models.ReplicatorMessage

object PushTopics {
  
  def message(message: ReplicatorMessage.Model) = {
    val json = ReplicatorMessage.toJson(message)    
    WebSocketActorManager.push(WebSocketActorEvent(message.senderId, "MESSAGE", Map(), json))
    WebSocketActorManager.push(WebSocketActorEvent(message.recipientId, "MESSAGE", Map(), json))      
  }
  
  def messageUpdate(message: ReplicatorMessage.Model) = {
    val json = ReplicatorMessage.toJson(message)
    WebSocketActorManager.push(WebSocketActorEvent(message.senderId, "MESSAGE-UPDATE", Map(), json))
    WebSocketActorManager.push(WebSocketActorEvent(message.recipientId, "MESSAGE-UPDATE", Map(), json))    
  }
  
}
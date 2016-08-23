package rpc

import scala.Right
import scala.concurrent.Future

import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper

object Disconnect extends RPCCall {
  
  def call(params: JsValue, accountId: Long): Either[Future[Option[JsValue]],Option[JsValue]] = {
    Right(Some(Json.obj("diconnected" -> true)))
  }
}
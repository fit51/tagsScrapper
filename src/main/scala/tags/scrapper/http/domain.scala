package tags.scrapper.http

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpCredentials}
import com.typesafe.config.Config

object ProxyConfig {
  def apply(config: Config): ProxyConfig = ProxyConfig(
    config.getString("host"),
    config.getInt("port"),
    if (config.hasPath("credentials")) {
      Some(BasicHttpCredentials(config.getString("credentials.user"), config.getString("credentials.password")))
    } else {
      None
    }
  )
}
case class ProxyConfig(
                        host: String,
                        port: Int,
                        credentials: Option[HttpCredentials]
                      )

case class QueueException(message: String) extends Exception(message)

object HttpCodeException {
  def apply(code: StatusCode, body: String): HttpCodeException =
    new HttpCodeException(s"HttpCode $code, body $body")
}
case class HttpCodeException(message: String) extends Exception(message)

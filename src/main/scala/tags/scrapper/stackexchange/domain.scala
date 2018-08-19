package tags.scrapper.stackexchange

import com.typesafe.config.Config
import tags.scrapper.http.ProxyConfig


object ScrapperConfig {
  def apply(config: Config) =
    new ScrapperConfig(config.getInt("queue"),
      config.getInt("maxConnections"),
      config.getInt("maxParallelism"),
      if (config.hasPath("proxy"))
        Some(ProxyConfig(config.getConfig("proxy")))
      else
        None
    )
}

case class ScrapperConfig(
                         queue: Int,
                         maxConnections: Int,
                         maxParallelism: Int,
                         proxy: Option[ProxyConfig]
                         )

case class CommonResponse[T](
                              error_id: Option[Int],
                              error_message: Option[String],
                              error_name: Option[String],
                              items: Seq[T],
                              has_more: Boolean,
                              quota_max: Int,
                              quota_remaining: Int
                            )

case class SearchResponse(
                           tags: Seq[String],
                           is_answered: Boolean,
                         )

case class ResponseTag(name: String,
                       isAnswered: Boolean)

case class TagStatistics(total: Long,
                         answered: Long)

case class StackExchangeException(errorId: Int, errorMessage: String, errorName: String)
  extends Exception(s"StackExchange replied with errorId: $errorId errorName: $errorName errorMessage: $errorMessage")

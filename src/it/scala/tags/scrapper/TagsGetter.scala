package tags.scrapper

import akka.http.scaladsl.model.{HttpMethods, HttpRequest, StatusCodes, Uri}
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.unmarshalling.Unmarshal
import tags.scrapper.http.HttpCodeException
import tags.scrapper.stackexchange.{CommonResponse, TagsScrapperImpl}
import io.circe.generic.auto._

import scala.concurrent.Future

case class Badge(name: String)

trait TagsGetter {
  self: TagsScrapperImpl =>

  def get10Tags() = {
    val uri = Uri(stackURL + "badges/tags")
    val query = Query(Map(
      "sort" -> "rank",
      "site" -> "stackoverflow",
      "pagesize" -> "10"
    ))
    for {
      response <- http.request(HttpRequest(HttpMethods.GET, uri.withQuery(query)))
      badges <- response.status match {
        case StatusCodes.OK =>
          Unmarshal(response.entity).to[CommonResponse[Badge]]
        case code => response.entity.dataBytes.runReduce(_ ++ _).flatMap { body =>
          Future.failed(
            HttpCodeException(code, body.decodeString("UTF-8"))
          )
        }
      }
    } yield {
      badges.items.map(_.name)
    }
  }
}

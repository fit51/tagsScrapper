package tags.scrapper.stackexchange

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import tags.scrapper.http.{HttpClient, HttpClientQueueImplementation, HttpCodeException}
import tags.scrapper.json.AkkaHttpCirceSupport
import io.circe.generic.auto._
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable

import scala.concurrent.{ExecutionContext, Future}

trait TagsScrapper {
  def calculateStatistics(tags: Set[String]): Future[List[(String, TagStatistics)]]
}

object TagsScrapper {
  def apply(config: ScrapperConfig)
           (implicit sys: ActorSystem,
            mat: Materializer,
            ec: ExecutionContext): TagsScrapper = new TagsScrapperImpl(config)
}

class TagsScrapperImpl(config: ScrapperConfig)
                      (implicit val sys: ActorSystem,
                       val mat: Materializer,
                       val ec: ExecutionContext) extends TagsScrapper with LazyLogging with AkkaHttpCirceSupport {

  implicit val scheduler = Scheduler(ec)

  val http: HttpClient = new HttpClientQueueImplementation(config.queue, config.maxConnections, config.proxy)

  val stackURL = "https://api.stackexchange.com/2.2/"

  def formSearchRequest(tag: String, pageSize: Int = 100, page: Option[Int] = None): HttpRequest = {
    val uri = Uri(stackURL + "search")
    val query = Query(Map(
      "pagesize" -> pageSize.toString,
      "order" -> "desc",
      "sort" -> "creation",
      "site" -> "stackoverflow",
      "tagged" -> tag
    ) ++ page.map(p => "page" -> p.toString))
    HttpRequest(HttpMethods.GET, uri.withQuery(query))
  }

  def search(tag: String): Future[Seq[SearchResponse]] = {
    val request = formSearchRequest(tag)
    logger.debug(s"HTTP REQ ${request.method} ${request.uri}")
    for {
      resp <- http.request(request)
      _ = logger.debug(s"HTTP RESP ${resp.status} Content-Type: ${resp.entity.contentType}")
      commonResponse <- resp.status match {
        case StatusCodes.OK =>
          Unmarshal(resp.entity)
            .to[CommonResponse[SearchResponse]]
        case code =>
          resp.entity.dataBytes.runReduce(_ ++ _).flatMap { body =>
            Future.failed(
              HttpCodeException(code, body.decodeString("UTF-8"))
            )
          }
      }
      searchResponse <- commonResponse match {
        case CommonResponse(Some(errorId), Some(errorMessage), Some(errorName), _, _, _, _) =>
          Future.failed(StackExchangeException(errorId, errorMessage, errorName))
        case c =>
          Future.successful(c.items)
      }
    } yield {
      searchResponse
    }
  }

  def searchResponsesToTags(searchResponses: Observable[Seq[SearchResponse]]): Observable[ResponseTag] =
    searchResponses
      .flatMap { responses =>
        Observable.fromIterable(
          responses.flatMap(search =>
            search.tags.map(name =>
              ResponseTag(name, search.is_answered))
          ))
      }

  def gatherTagsStatistics(tags: Observable[ResponseTag]): Observable[(String, TagStatistics)] =
    tags
      //Does not allocate all ResponseTags in Memory (Streaming approach)
      .groupBy(_.name)
      .mergeMap { gr =>
        gr.foldLeftF((0L, 0L)) { (acc, tag) =>
          val answered = if (tag.isAnswered) 1 else 0
          (acc._1 + 1, acc._2 + answered)
        }.map {
          case (total, answered) =>
            gr.key -> TagStatistics(total, answered)
        }
      }

  def calculateStatistics(tags: Set[String]): Future[List[(String, TagStatistics)]] = {
    val searchResponses: Observable[Seq[SearchResponse]] = Observable
      .fromIterable(tags)
      .mapParallelUnordered(config.maxParallelism) { tag =>
        Task.deferFuture(search(tag))
      }

    // Calculate statistics
    val statistics = (searchResponsesToTags _ andThen gatherTagsStatistics _) (searchResponses)

    statistics
      .toListL
      .map(_.sortBy(_._2.total)(Ordering[Long].reverse))
      .runAsync
  }

}

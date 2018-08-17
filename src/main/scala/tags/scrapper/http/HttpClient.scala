package tags.scrapper.http

import akka.actor.ActorSystem
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.{Materializer, OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.typesafe.config.Config

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

class HttpClient {

}

case class QueueException(message: String) extends Exception(message)

class HttpClientQueueImplementation(config: Config, queueSize: Int, maxConnections: Int)
                                   (implicit sys: ActorSystem,
                                    mat: Materializer,
                                    ec: ExecutionContext) {

  val poolSettings = ConnectionPoolSettings(config)
    .withMaxConnections(maxConnections)

  protected def poolClientFlow = Http().superPool[Promise[HttpResponse]](settings = poolSettings)

  protected val queue =
    Source.queue[(HttpRequest, Promise[HttpResponse])](queueSize, OverflowStrategy.dropNew)
    .via(poolClientFlow)
    .toMat(Sink.foreach({
      case (Success(resp), promise) => promise.success(resp)
      case (Failure(e), promise) => promise.failure(e)
    }))(Keep.left)
    .run()

  def request(httpRequest: HttpRequest): Future[HttpResponse] = {
    val responsePromise = Promise[HttpResponse]
    queue.offer(httpRequest -> responsePromise).flatMap {
      case QueueOfferResult.Enqueued =>
        responsePromise.future
      case QueueOfferResult.Dropped =>
        Future.failed(QueueException(s"HTTP client queue overflowed for request to ${httpRequest.uri}"))
      case QueueOfferResult.Failure(ex) =>
        Future.failed(ex)
      case QueueOfferResult.QueueClosed =>
        Future.failed(QueueException(s"HTTP client queue closed while sending request to ${httpRequest.uri}"))
    }
  }

}

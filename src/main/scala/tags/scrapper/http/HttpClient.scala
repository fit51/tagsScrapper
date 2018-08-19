package tags.scrapper.http

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.http.scaladsl.{ClientTransport, Http}
import akka.http.scaladsl.model.headers.HttpEncodings
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.{Materializer, OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.http.scaladsl.coding.{Deflate, Gzip, NoCoding}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

trait HttpClient {
  def request(httpRequest: HttpRequest): Future[HttpResponse]
}

class HttpClientQueueImplementation(queueSize: Int, maxConnections: Int,
                                    proxy: Option[ProxyConfig] = None)
                                   (implicit system: ActorSystem,
                                    mat: Materializer,
                                    ec: ExecutionContext) extends HttpClient {

  val httpTransport = proxy match {
    case Some(pConfig) =>
      pConfig.credentials.fold(ClientTransport.httpsProxy(new InetSocketAddress(pConfig.host, pConfig.port))) { credentials =>
        ClientTransport.httpsProxy(new InetSocketAddress(pConfig.host, pConfig.port), credentials)
      }
    case None => ClientTransport.TCP
  }

  val poolSettings = ConnectionPoolSettings(system)
    .withConnectionSettings(
      ClientConnectionSettings(system).withTransport(httpTransport)
    )
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

  protected def decodeResponse(response: HttpResponse): HttpResponse = {
    val decoder = response.encoding match {
      case HttpEncodings.gzip ⇒
        Gzip
      case HttpEncodings.deflate ⇒
        Deflate
      case HttpEncodings.identity ⇒
        NoCoding
    }
    decoder.decodeMessage(response)
  }

  def request(httpRequest: HttpRequest): Future[HttpResponse] = {
    val responsePromise = Promise[HttpResponse]
    queue.offer(httpRequest -> responsePromise).flatMap {
      case QueueOfferResult.Enqueued =>
        responsePromise.future
          .map(decodeResponse)
      case QueueOfferResult.Dropped =>
        Future.failed(QueueException(s"HTTP client queue overflowed for request to ${httpRequest.uri}"))
      case QueueOfferResult.Failure(ex) =>
        Future.failed(ex)
      case QueueOfferResult.QueueClosed =>
        Future.failed(QueueException(s"HTTP client queue closed while sending request to ${httpRequest.uri}"))
    }
  }

}

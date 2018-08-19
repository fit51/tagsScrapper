package tags.scrapper.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import io.circe.Printer
import tags.scrapper.json.AkkaHttpCirceSupport
import tags.scrapper.stackexchange.{ScrapperConfig, TagStatistics, TagsScrapper}

import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class WebServer extends StrictLogging with AkkaHttpCirceSupport {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val printer = Printer.spaces2

  val config = ConfigFactory.load()

  val hostname = config.getString("server.host")
  val port =  config.getInt("server.port")

  val scrapper = TagsScrapper(ScrapperConfig(config.getConfig("scrapper")))

  val exceptionHandler = ExceptionHandler {
    case NonFatal(e) =>
      logger.error("Error occured", e)
      complete(HttpResponse(StatusCodes.InternalServerError, entity = e.getMessage))
  }

  val route =
    handleExceptions(exceptionHandler) {
      path("search") {
          parameters("tag".as[String].*) { tags =>
            onSuccess(scrapper.calculateStatistics(tags.toSet)) { result =>
              complete(StatisticsResponse(result))
            }
          }
      }
    }

  def run() = {
    Http()
      .bindAndHandle(route, hostname, port)
      .onComplete {
        case Success(_) => logger.info(s"Started Http Server $hostname:$port")
        case Failure(e) =>
          logger.error(s"Failed to start Http Server", e)
          throw e
      }
  }

}

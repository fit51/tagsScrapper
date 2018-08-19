package tags.scrapper

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import tags.scrapper.stackexchange.{CommonResponse, ScrapperConfig, TagsScrapperImpl}

import scala.util.{Failure, Success}

/**
  * Example.
  * Gets 10 tags (badges/tags) and performs search on them
  */
object RunningExample extends App {

  val config = ConfigFactory.parseString(
    """
      |scrapper {
      |  queue: 10000
      |  maxConnections: 10
      |  maxParallelism: 10
      |  proxy {
      |    host = "88.203.197.241"
      |    port = 8080
      |  }
      |}
    """.stripMargin)

  implicit val system = ActorSystem("test", config)
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher

  val scrapper = new TagsScrapperImpl(ScrapperConfig(config.getConfig("scrapper"))) with TagsGetter
  val task = for {
    tags <- scrapper.get10Tags()
    _ = println(s"Got ${tags.length} tags")
    start = System.nanoTime()
    stat <- scrapper.calculateStatistics(tags.toSet)
  } yield {
    val end = System.nanoTime()
    println(s"Elapsed ${(end - start) / 10e6} millis")
    println(s"Gathered ${stat.length} tags")
  }

  task.onComplete {
    case Success(_) => println("Done")
      system.terminate()
    case Failure(e) => e.printStackTrace()
      system.terminate()
  }


}



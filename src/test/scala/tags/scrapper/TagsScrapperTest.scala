package tags.scrapper

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import monix.execution.Scheduler
import monix.reactive.Observable
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import tags.scrapper.stackexchange._
import org.mockito.ArgumentMatchers.{eq => exact, _}
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class TagsScrapperTest extends TestKit(ActorSystem("Test")) with WordSpecLike with Matchers with MockitoSugar {

  implicit class FutureHelper[T](f: Future[T]) {
    def get = Await.result(f, 1 second)
  }

  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher

  trait mocks {

    val searchMock = mock[TagsScrapperImpl]

    val service = new TagsScrapperImpl(ScrapperConfig(1, 1, 1, None)) {

      override def search(tag: String): Future[Seq[SearchResponse]] = searchMock.search(tag)

      def calculateStatisticsNaive(tags: Set[String]): Future[Seq[(String, TagStatistics)]] = {
        val responses = Future.sequence(tags.toSeq.map(search)).map(_.flatten)
        (searchResultsToTagsNaive _ andThen gatherStatisticsNaive _) (responses)
      }

      def searchResultsToTagsNaive(responses: Future[Seq[SearchResponse]]): Future[Seq[ResponseTag]] = {
        responses.map(_.flatMap { response =>
          response.tags
            .map(ResponseTag(_, response.is_answered))
        })
      }

      def gatherStatisticsNaive(input: Future[Seq[ResponseTag]]): Future[Seq[(String, TagStatistics)]] = {
        input.map { responseTags =>
          val tagsStatistics = responseTags.groupBy(_.name)
            .mapValues { rTags =>
              TagStatistics(rTags.length, rTags.count(_.isAnswered))
            }
            .toSeq
          tagsStatistics.sortBy(_._2.total)(Ordering[Long].reverse)
        }
      }
    }

    val searchResponses1 = Seq(
      SearchResponse(Seq("1", "2", "5"), true),
      SearchResponse(Seq("1"), false),
      SearchResponse(Seq("1"), true)
    )
    val searchResponses2 = Seq(
      SearchResponse(Seq("4", "2"), true)
    )
    val searchResponses3 = Seq(
      SearchResponse(Seq("1", "3"), false)
    )

    when(searchMock.search("1")).thenReturn(Future.successful(searchResponses1))
    when(searchMock.search("2")).thenReturn(Future.successful(searchResponses2))
    when(searchMock.search("3")).thenReturn(Future.successful(searchResponses3))

    val searchResponses = searchResponses1 ++ searchResponses2 ++ searchResponses3

    val responseTags = Seq(
      ResponseTag("1", true),
      ResponseTag("1", true),
      ResponseTag("1", false),
      ResponseTag("2", true),
      ResponseTag("3", false),
      ResponseTag("2", true),
      ResponseTag("1", false),
      ResponseTag("4", true),
      ResponseTag("5", true),
    )

    val statistics = Seq(
      "1" -> TagStatistics(4, 2),
      "2" -> TagStatistics(2, 2),
      "3" -> TagStatistics(1, 0),
      "4" -> TagStatistics(1, 1),
      "5" -> TagStatistics(1, 1),
    )
  }

  "TagsScrapper Naive" should {
    "match searchResponses to responseTags" in new mocks {
      val tags = service.searchResultsToTagsNaive(Future.successful(searchResponses)).get
      tags should contain theSameElementsAs responseTags
    }
    "gather statistics" in new mocks {
      val stats = service.gatherStatisticsNaive(Future.successful(responseTags)).get
      stats should contain theSameElementsAs statistics
    }
    "calculate statistics" in new mocks {
      val stats = service.calculateStatisticsNaive(Set("1", "2", "3")).get
      stats should contain theSameElementsAs statistics
    }
  }

  "TagsScrapper" should {
    implicit val scheduler = Scheduler(ec)
    "match searchResponses to responseTags" in new mocks {
      val tags = service.searchResponsesToTags(Observable.now(searchResponses))
        .toListL.runAsync.get
      tags should contain theSameElementsAs responseTags
    }
    "gather statistics" in new mocks {
      val stats = service.gatherTagsStatistics(Observable.fromIterable(responseTags))
        .toListL.runAsync.get
      stats should contain theSameElementsAs statistics
    }
    "calculate statistics" in new mocks {
      val stats = service.calculateStatistics(Set("1", "2", "3")).get
      stats should contain theSameElementsAs statistics
    }
  }

}

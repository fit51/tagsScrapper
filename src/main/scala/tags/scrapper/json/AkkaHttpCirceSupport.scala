package tags.scrapper.json

import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.{HttpEntity, MediaTypes}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import akka.util.ByteString
import io.circe.{Decoder, Encoder, Json, Printer}
import io.circe.parser.decode
import tags.scrapper.server.StatisticsResponse
import io.circe.syntax._
import io.circe.generic.auto._

trait AkkaHttpCirceSupport {

  val mediaType = MediaTypes.`application/json`

  private val jsonStringUnmarshaller = Unmarshaller.byteStringUnmarshaller
    .forContentTypes(mediaType)
    .mapWithCharset {
      case (ByteString.empty, _) => throw Unmarshaller.NoContentException
      case (data, charset) => data.decodeString(charset.nioCharset.name)
    }

  private def jsonMarshaller(implicit printer: Printer): ToEntityMarshaller[Json] = Marshaller
    .withFixedContentType(mediaType) { json =>
      HttpEntity(mediaType, printer.pretty(json))
    }

  implicit def circeUnmarshaller[A: Decoder]: FromEntityUnmarshaller[A] =
    jsonStringUnmarshaller.map(decode(_).fold(throw _, identity))

  implicit def circeMarshaller[A: Encoder](implicit printer: Printer = Printer.noSpaces): ToEntityMarshaller[A] =
    jsonMarshaller(printer) compose Encoder[A].apply

  //Preserves Tags ordering by "total" in Json.obj fields
  implicit val encodeStatisticsResponse = new Encoder[StatisticsResponse] {
    override def apply(a: StatisticsResponse): Json = {
      val fields = a.payload.map { case (name, stat) =>
        (name, stat.asJson)
      }
      Json.obj(fields: _*)
    }
  }

}

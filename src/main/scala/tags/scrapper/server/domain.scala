package tags.scrapper.server

import tags.scrapper.stackexchange.TagStatistics

case class StatisticsResponse(payload: List[(String, TagStatistics)])

package tags.scrapper

import tags.scrapper.server.WebServer

object Run extends App {

  val webServer = new WebServer().run()

}

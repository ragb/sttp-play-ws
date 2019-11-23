package com.ruiandrebatista.sttp.play

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import sttp.client._
import sttp.client.testing._

import scala.concurrent.Future

class PlayWsStandaloneClientHttpTest extends HttpTest[Future] {
  implicit private val system = ActorSystem()
  implicit private val mat = ActorMaterializer()

  override implicit val backend: SttpBackend[Future, Nothing, NothingT] =
    PlayWSClientBackend(SttpBackendOptions.Default)
  override implicit val convertToFuture: ConvertToFuture[Future] =
    ConvertToFuture.future

  override def afterAll() = {
    system.terminate()
    super.afterAll()
  }
}

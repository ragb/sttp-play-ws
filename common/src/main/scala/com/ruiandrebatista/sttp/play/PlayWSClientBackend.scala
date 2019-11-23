/*
 * Copyright 2019 Rui Batista
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ruiandrebatista.sttp.play

import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Source, Sink}

import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
import java.io.File

import sttp.client._
import sttp.model._
import sttp.client.monad._
import sttp.client.ws.WebSocketResponse

import play.api.mvc.MultipartFormData
import play.core.formatters.{Multipart => PlayMultipart}
import play.api.libs.ws._
import play.api.libs.ws.ahc.AhcWSClient

import scala.concurrent.{ExecutionContext, Future}


final class PlayWSClientBackend private (wsClient: WSClient, mustCloseClient: Boolean, backendOptions: SttpBackendOptions)(
    implicit ec: ExecutionContext,
    mat: Materializer
) extends SttpBackend[Future, Source[ByteString, Any], NothingT] {


  private val maybeProxyServer = backendOptions.proxy.map { sttpProxy =>
    DefaultWSProxyServer(sttpProxy.host, sttpProxy.port, if (sttpProxy.port == 443) Some("https") else None)
  }

  private type S = Source[ByteString, Any]

  private def convertRequest[T](request: Request[T, S]): WSRequest = {
    val holder = wsClient.url(request.uri.toJavaUri.toASCIIString())

    val holderWithProxy =
      maybeProxyServer.fold(holder)(holder.withProxyServer _)

    val (maybeBody, maybeContentType) = requestBodyToWsBodyAndContentType(request.body)

    val contentType: String = request.headers
      .collectFirst {
        case h if h.name == HeaderNames.ContentType => h.value
      }
      .orElse(maybeContentType)
      .getOrElse(MediaType.ApplicationOctetStream.toString())

    // Compute our own BodyWritable, essentially bypassing
    // play BodyWritable infrastructure
    val w: BodyWritable[WSBody] = BodyWritable(identity, contentType)

    maybeBody
      .fold(holderWithProxy)(b => holderWithProxy.withBody(b)(w))
      .withFollowRedirects(false) // Wraper backend will handle this
      .withHttpHeaders(request.headers.map(h => (h.name, h.value)): _*)
      .withMethod(request.method.method)
      .withRequestTimeout(request.options.readTimeout)

  }

  private def requestBodyToWsBodyAndContentType[T](requestBody: RequestBody[S]): (Option[WSBody], Option[String]) = {

    requestBody match {
      case StringBody(s, encoding, ct) =>
        (Some(InMemoryBody(ByteString(s, encoding))), ct.map(_.toString()))
      case ByteArrayBody(a, ct) =>
        (Some(InMemoryBody(ByteString(a))), ct.map(_.toString()))
      case ByteBufferBody(b, ct) =>
        (Some(InMemoryBody(ByteString(b))), ct.map(_.toString()))
      case InputStreamBody(in, ct) =>
        (Some(SourceBody(StreamConverters.fromInputStream(() => in))), ct.map(_.toString()))
      case StreamBody(s: S) =>
        (Some(SourceBody(s)), None)
      case NoBody =>
        (None, None)
      case FileBody(file, ct) =>
        (Some(SourceBody(FileIO.fromPath(file.toPath))), ct.map(_.toString()))
      case MultipartBody(parts) =>
        val boundary = PlayMultipart.randomBoundary()
        val contentType = s"multipart/form-data; boundary=$boundary"
        val playParts = Source(parts.map(toPlayMultipart _))
        (Some(SourceBody(PlayMultipart.transform(playParts, boundary))), Some(contentType))
    }
  }

  private def toPlayMultipart(part: Part[BasicRequestBody]) = {

    def byteStringPart(bstr: ByteString, ct: Option[String]) =
      byteSourcePart(Source.single(bstr), ct)

    def byteSourcePart(source: Source[ByteString, _], ct: Option[String]) = {
      MultipartFormData.FilePart(part.name, part.fileName.getOrElse(""), part.contentType orElse ct, source)
    }

    def nameWithFilename = part.fileName.fold(part.name) { fn =>
      s"""${part.name}"; filename="$fn"""
    }

    part.body match {
      case StringBody(s, _, _) =>
        MultipartFormData.DataPart(nameWithFilename, s)

      case ByteArrayBody(a, ct) =>
        byteStringPart(ByteString(a), ct.map(_.toString()))
      case ByteBufferBody(b, ct) =>
        byteStringPart(ByteString(b), ct.map(_.toString()))
      case InputStreamBody(in, ct) =>
        byteSourcePart(StreamConverters.fromInputStream(() => in), ct.map(_.toString()))
      case FileBody(file, ct) =>
        MultipartFormData.FilePart(
          part.name,
          part.fileName.getOrElse(file.name),
          part.contentType orElse ct.map(_.toString()),
          FileIO.fromPath(file.toPath)
        )
    }
  }

  def send[T](r: Request[T, S]): Future[Response[T]] = {
    val request = convertRequest(r)

    val execute = r.response match {
      case ResponseAsStream() => request.stream _
      case _                  => request.execute _
    }

    execute()
      .flatMap(readResponse(_, r.response))
  }

  private def readResponse[T](response: WSResponse, responseAs: ResponseAs[T, S]) = {

    val headers = response.headers.toList.flatMap {
      case (name, values) => values.map(v => Header.unsafeApply(name, v))
    }

    val metadata =
      ResponseMetadata(headers, StatusCode.unsafeApply(response.status), response.statusText)

    val body =
      readBody(response, metadata, responseAs)

    body.map(b => Response(b, metadata.code, metadata.statusText, metadata.headers, Nil))
  }

  private def readBody[T](
      response: StandaloneWSResponse,
      metadata: ResponseMetadata,
      responseAs: ResponseAs[T, S]
  ): Future[T] =
    responseAs match {
      case MappedResponseAs(raw, g) =>
        readBody(response, metadata, raw)
          .map(r => g(r, metadata))

      case ResponseAsFromMetadata(f) => readBody(response, metadata, f(metadata))
      case ResponseAsByteArray       => Future { response.bodyAsBytes.toArray }
      case r @ ResponseAsStream() =>
        Future.successful(r.responseIsStream(response.bodyAsSource))
      case ResponseAsFile(file) =>
        saveFile(file.toFile, response).map(_ => file)
      case IgnoreResponse =>
        response.bodyAsSource.runWith(Sink.ignore).map(_ => ())

    }

  def close(): Future[Unit] =
    if (mustCloseClient)
      Future(wsClient.close())
    else Future.unit


  private def saveFile(file: File, response: StandaloneWSResponse) = {

    if (!file.exists()) {
      file.getParentFile.mkdirs()
      file.createNewFile()
    }

    response.bodyAsSource.runWith(FileIO.toPath(file.toPath))
  }

  override val responseMonad: MonadError[Future] = new FutureMonad


 
  override def openWebsocket[T, WS_RESULT](
    request: Request[T,Source[ByteString,Any]],
    handler: NothingT[WS_RESULT]
  ): Future[WebSocketResponse[WS_RESULT]] = ???

}

object PlayWSClientBackend {
  private def defaultClient(implicit mat: Materializer) =
    AhcWSClient()
  def apply(backendOptions: SttpBackendOptions)(implicit ec: ExecutionContext, mat: Materializer) =
    new FollowRedirectsBackend[Future, Source[ByteString, Any], NothingT](
      new PlayWSClientBackend(defaultClient, true, backendOptions)
    )

  def apply(client: WSClient, backendOptions: SttpBackendOptions)(implicit ec: ExecutionContext, mat: Materializer) =
    new FollowRedirectsBackend[Future, Source[ByteString, Any], NothingT](
      new PlayWSClientBackend(client, false, backendOptions)
    )
}

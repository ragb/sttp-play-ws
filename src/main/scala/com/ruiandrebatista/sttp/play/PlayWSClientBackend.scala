package com.ruiandrebatista.sttp.play

import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Source, Sink}

import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
import java.io.{File, IOException}

import com.softwaremill.sttp._

import play.api.mvc.MultipartFormData
import play.core.formatters.{Multipart => PlayMultipart}
import play.api.libs.ws._
import play.api.libs.ws.ahc.AhcWSClient

import scala.concurrent.{ExecutionContext, Future}

class PlayWSClientBackend(wsClient: WSClient, mustCloseClient: Boolean, backendOptions: SttpBackendOptions)(
    implicit ec: ExecutionContext,
    mat: Materializer)
    extends SttpBackend[Future, Source[ByteString, Any]] {

  private val maybeProxyServer = backendOptions.proxy.map { sttpProxy =>
    DefaultWSProxyServer(sttpProxy.host, sttpProxy.port, if (sttpProxy.port == 443) Some("https") else None)
  }

  private type S = Source[ByteString, Any]

  private def convertRequest[T](request: Request[T, S]): WSRequest = {
    val holder = wsClient.url(request.uri.toJavaUri.toASCIIString())

    val holderWithProxy =
      maybeProxyServer.fold(holder)(holder.withProxyServer _)

    val (maybeBody, maybeContentType) = requestBodyToWsBodyAndContentType(request.body)

    val contentType = request.headers.toMap
      .get(HeaderNames.ContentType) orElse maybeContentType getOrElse "application/octect-stream"

    // Compute our own BodyWritable, essentially bypassing
    // play BodyWritable infrastructure
    val w: BodyWritable[WSBody] = BodyWritable(identity, contentType)

    maybeBody
      .fold(holderWithProxy)(b => holderWithProxy.withBody(b)(w))
      .withFollowRedirects(false) // Wraper backend will handle this
      .withHttpHeaders(request.headers: _*)
      .withMethod(request.method.m)
      .withRequestTimeout(request.options.readTimeout)

  }

  private def requestBodyToWsBodyAndContentType[T](requestBody: RequestBody[S]): (Option[WSBody], Option[String]) = {

    requestBody match {
      case StringBody(s, encoding, ct) =>
        (Some(InMemoryBody(ByteString(s, encoding))), ct)
      case ByteArrayBody(a, ct) =>
        (Some(InMemoryBody(ByteString(a))), ct)
      case ByteBufferBody(b, ct) =>
        (Some(InMemoryBody(ByteString(b))), ct)
      case InputStreamBody(in, ct) =>
        (Some(SourceBody(StreamConverters.fromInputStream(() => in))), ct)
      case StreamBody(s: S) =>
        (Some(SourceBody(s)), None)
      case NoBody =>
        (None, None)
      case FileBody(file, ct) =>
        (Some(SourceBody(FileIO.fromPath(file.toPath))), ct)
      case MultipartBody(parts) =>
        val boundary = PlayMultipart.randomBoundary()
        val contentType = s"multipart/form-data; boundary=$boundary"
        val playParts = Source(parts.map(toPlayMultipart _))
        (Some(SourceBody(PlayMultipart.transform(playParts, boundary))), Some(contentType))
    }
  }

  private def toPlayMultipart(part: Multipart) = {

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
        byteStringPart(ByteString(a), ct)
      case ByteBufferBody(b, ct) =>
        byteStringPart(ByteString(b), ct)
      case InputStreamBody(in, ct) =>
        byteSourcePart(StreamConverters.fromInputStream(() => in), ct)
      case FileBody(file, ct) =>
        MultipartFormData.FilePart(part.name,
                                   part.fileName.getOrElse(file.name),
                                   part.contentType orElse ct,
                                   FileIO.fromPath(file.toPath))
    }
  }

  def send[T](r: Request[T, S]): Future[Response[T]] = {
    val request = convertRequest(r)

    val execute = r.response match {
      case ResponseAsStream() => request.stream _
      case _ => request.execute _
    }

    execute()
      .flatMap(readResponse(_, r.options.parseResponseIf, r.response))
  }

  private def readResponse[T](response: WSResponse,
                              parseIfCondition: (ResponseMetadata) => Boolean,
                              responseAs: ResponseAs[T, S]) = {

    val headers = response.headers.toList.flatMap {
      case (name, values) => values.map((name, _))
    }

    val metadata =
      ResponseMetadata(headers, response.status, response.statusText)

    val body =
      if (parseIfCondition(metadata))
        readBody(response, metadata, responseAs).map(Right.apply _)
      else readBody(response, metadata, ResponseAsByteArray).map(Left.apply _)
    body.map(b => Response(b, metadata.code, metadata.statusText, metadata.headers, Nil))
  }

  private def readBody[T](response: StandaloneWSResponse,
                          metadata: ResponseMetadata,
                          responseAs: ResponseAs[T, S]): Future[T] =
    responseAs match {
      case MappedResponseAs(raw, g) =>
        readBody(response, metadata, raw)
          .map(r => g(r, metadata))
      case ResponseAsString(encoding) =>
        Future {
          response.bodyAsBytes.decodeString(
            metadata
              .header(HeaderNames.ContentType)
              .flatMap(encodingFromContentType)
              .getOrElse(encoding))
        }
      case ResponseAsByteArray => Future { response.bodyAsBytes.toArray }
      case r @ ResponseAsStream() =>
        Future.successful(r.responseIsStream(response.bodyAsSource))
      case ResponseAsFile(file, overwrite) =>
        saveFile(file.toFile, overwrite, response).map(_ => file)
      case IgnoreResponse =>
        response.bodyAsSource.runWith(Sink.ignore).map(_ => ())

    }

// shamefully copyed from sttps internals.
  private def encodingFromContentType(ct: String): Option[String] =
    ct.split(";").map(_.trim.toLowerCase).collectFirst {
      case s if s.startsWith("charset=") && s.substring(8).trim != "" =>
        s.substring(8).trim
    }
  def close(): Unit =
    if (mustCloseClient)
      wsClient.close()

  private def saveFile(file: File, overwrite: Boolean, response: StandaloneWSResponse) = {

    if (!file.exists()) {
      file.getParentFile.mkdirs()
      file.createNewFile()
    } else if (!overwrite) {
      throw new IOException(s"File ${file.getAbsolutePath} exists - overwriting prohibited")
    }

    response.bodyAsSource.runWith(FileIO.toPath(file.toPath))
  }

  override val responseMonad: MonadError[Future] = new FutureMonad
}

object PlayWSClientBackend {
  private def defaultClient(implicit mat: Materializer) =
    AhcWSClient()
  def apply(backendOptions: SttpBackendOptions)(implicit ec: ExecutionContext, mat: Materializer) =
    new FollowRedirectsBackend(new PlayWSClientBackend(defaultClient, true, backendOptions))

  def apply(client: WSClient, backendOptions: SttpBackendOptions)(implicit ec: ExecutionContext, mat: Materializer) =
    new FollowRedirectsBackend(new PlayWSClientBackend(client, false, backendOptions))
}

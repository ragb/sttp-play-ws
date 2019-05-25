## sttp-play-ws ##

[![Build Status](https://travis-ci.org/ragb/sttp-play-ws.svg?branch=master)](https://travis-ci.org/ragb/sttp-play-ws)

[sttp][sttp] backend for [play-ws][playws].

### Goals ###

The main goal of this library is to allow the usage of sttp machinery in the context of an already existing Play app, avoiding inclusion of a different HTTP client.
If you develop HTTP clients with sttp to be used, e.g. on an Akka based micro Service, and you need some of the same logic in your Play Frontend / backoffice application writen in play, this library may be helpful.


### Getting Started ###
 
Include the following on your build.sbt or similar:
 
 
```scala
libraryDependencies += "com.ruiandrebatista" %% "sttp-play-ws-<playVersion>" % "<latest>"
```

This library is published for both play 2.6 and 2.7.
Check the following table for artifact names, versions and associated play versions you might use for the artifact name.

| Artifact Name   | Play Version | Latest Version                                                                                                                                                                                                     |
|-----------------|--------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| sttp-play-ws-26 | 2.6.23       | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.ruiandrebatista/sttp-play-ws-26_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.ruiandrebatista/sttp-play-ws-26_2.12) |
| sttp-play-ws-27 | 2.7.2        |                                                                                                                                                                                                                    [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.ruiandrebatista/sttp-play-ws-27_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.ruiandrebatista/sttp-play-ws-27_2.12)|

### Features ###

Supports all *tested* features of sttp backends. Uses sttp own tests(pooled automatically) for unit testing of this backend.

Main supported features:

* Streaming (using `akka.stream.Source[ByteString, _]`
* Proxy support
* Multipart uploads.



### Usage with Guice ###

TBD


### Notes ###

This library depends on `play-ws` (ense many play dependencies) and not on the play-ws-standalone project. This is due to the missing multipart support on the standalone artifact.
When this gets sorted library will probably change to depend solely on play-ws-standalone.






[sttp]: https://github.com/softwaremill/sttp
[playws]: https://github.com/playframework/play-ws


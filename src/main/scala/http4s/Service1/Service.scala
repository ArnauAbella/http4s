package http4s.Service1

import cats.effect._, org.http4s._, org.http4s.dsl.io._
import org.http4s.server.blaze._
import fs2.{Stream, StreamApp}
import fs2.StreamApp.ExitCode
import cats.implicits._ // combineK (<+>)
import org.http4s.implicits._

// Every ServerBuilder[F] has a .serve method that returns a Stream[F, ExitCode].
// This stream runs forever without emitting any output. When this process is
// run with .unsafeRunSync on the main thread, it blocks forever, keeping the JVM (and your server)
// alive until the JVM is killed.
//
//As a convenience, fs2 provides an fs2.StreamApp[F[_]] trait with an abstract main method that returns a Stream.
// A StreamApp runs the process and adds a JVM shutdown hook to interrupt the infinite process and gracefully
// shut down your server when a SIGTERM is received.
object Service extends StreamApp[IO] {

  // HttpService == Klesli[F[_],Request,Response]
  val helloWorldService = HttpService[IO] {
    case GET -> Root / "hello" / name =>
      Ok(s"Hello, $name.")
  }

  case class Tweet(id: Int, message: String)
  implicit def tweetEncoder: EntityEncoder[IO, Tweet] = ???
  implicit def tweetsEncoder: EntityEncoder[IO, Seq[Tweet]] = ???
  def getTweet(tweetId: Int): IO[Tweet] = ???
  def getPopularTweets(): IO[Seq[Tweet]] = ???

  val tweetService = HttpService[IO] {
    case GET -> Root / "tweets" / "popular" =>
      Ok(getPopularTweets())
    case GET -> Root / "tweets" / IntVar(tweetId) =>
      getTweet(tweetId).flatMap(Ok(_))
  }

  val services = tweetService <+> helloWorldService //combineK

  import scala.concurrent.ExecutionContext.Implicits.global

  override def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] =
    BlazeBuilder[IO]
      .bindHttp(8080, "localhost")
      .mountService(helloWorldService, "/")
      .mountService(services, "/api")
      .serve
  // .start
  // server.shutdown.unsafeRunSync()

}

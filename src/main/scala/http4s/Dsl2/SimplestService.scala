package http4s.Dsl2

import org.http4s._, org.http4s.dsl.io._
// import org.http4s.implicits._ // todo: conflict with org.http4s.dsl.io._
import cats.effect._
import cats.effect.implicits._
import cats.implicits._

object SimplestService extends App {
  val service = HttpService[IO] {
    case _ =>
      IO(Response(Status.Ok)) // Ok(...)
  }

  // status code
  Ok()
  NoContent()

  // Headers
  Ok("Ok response.").unsafeRunSync.headers

  import org.http4s.headers.`Cache-Control`
  import org.http4s.CacheDirective.`no-cache`
  import cats.data._

  Ok("Ok response.", `Cache-Control`(NonEmptyList(`no-cache`(), Nil))).unsafeRunSync.headers
  Ok("Ok response.", Header("X-Auth-Token", "value")).unsafeRunSync.headers

  // Cookies
  Ok("Ok response.").map(_.addCookie(Cookie("foo", "bar", expires = Some(HttpDate.now), httpOnly = true, secure = true))).unsafeRunSync.headers
  Ok("Ok response.").map(_.removeCookie("foo")).unsafeRunSync.headers

  // Responding with a body - EntityEncoder
  Ok("Received request.").unsafeRunSync
  import java.nio.charset.StandardCharsets.UTF_8
  Ok("binary".getBytes(UTF_8)).unsafeRunSync
  //NoContent("does not compile")

  // Asynchronous responses
  import scala.concurrent.Future
  import scala.concurrent.ExecutionContext.Implicits.global
  // It runs when the response is create ....
  Ok(IO.fromFuture(IO(Future {
    println("I run when the future is constructed.")
    "Greetings from the future!"
  })))
  Ok(IO {
    println("I run when the IO is run.")
    "Im pure af!"
  })

  // Streaming bodies
  val drip: fs2.Stream[IO, String] =
    fs2.Scheduler[IO](2).flatMap { s =>
      import scala.concurrent.duration._
      s.awakeEvery[IO](100.millis).map(_.toString()).take(10)
    }

  val dripOutIO = drip.through(fs2.text.lines).through(_.evalMap(s => {IO {println(s); s}})).compile.drain
  Ok(drip).unsafeRunSync()

  /** Matching and extracting requests */
  // The -> object
  HttpService[IO] {
    case GET -> Root / "hello" => Ok("hello")
  }
  // Path info: Request.pathInfo (dont use request.uri.path)

  // Matching paths
  HttpService[IO] {
    case GET -> Root => Ok("root")
    case GET -> Root / "hello" / name => Ok(s"Hello $name!")
    case GET -> "hello" /: rest => Ok(s"Hello ${rest.toList.mkString(" and ")}!")
    case GET -> Root / file ~ "json" => Ok(s"response: You asked for $file")
  }

  // Handling path parameters
  def getUserName(userId: Int): IO[String] = ???
  val usersService = HttpService[IO] {
    case GET -> Root / "users" / IntVar(userId) => Ok(getUserName(userId))
  }

  // Custom extractor (IntVar impl have a look)
  // object which implement def unapply(str: String): Option[T]

  import java.time.LocalDate
  import scala.util.Try
  import org.http4s.client.dsl.io._

  object LocalDateVar {
    def unapply(str: String): Option[LocalDate] = {
      if (!str.isEmpty)
        Try(LocalDate.parse(str)).toOption
      else
        None
    }
  }
  def getTemperatureForecast(date: LocalDate): IO[Double] = IO(42.23)
  val dailyWeatherService = HttpService[IO] {
    case GET -> Root / "weather" / "temperature" / LocalDateVar(localDate) =>
      Ok(getTemperatureForecast(localDate).map(s"The temperature on $localDate will be: " + _))
  }
  println(GET(Uri.uri("/weather/temperature/2016-11-05")).flatMap(dailyWeatherService.orNotFound(_)).unsafeRunSync)
}

object Testing extends App {

  import cats.effect._
  import org.http4s._, org.http4s.implicits._
  // org.http4s.dsl.io._ // this is importing http4sKleisliResponseSyntax ... wtf
  val service = HttpService[IO] {
    case _ =>
      IO(Response(Status.Ok))
  }

  val getRoot = Request[IO](Method.GET, uri("/"))
  val io = service.orNotFound.apply(getRoot)
  io.unsafeRunSync()
}

object Unapply extends App {
  case class Tuple1(x: String, y: String)
  object <=> {
    def unapply(t: Tuple1): Option[(String, String)] =
      Some(t.x -> t.y)
  }

  Tuple1("Arnau", "Abella") match {
    case name <=> surname => println(s"$surname, $name")
    case _ => "Pattern failed"
  }
}

package database.stmt.app

import database.stmt.DatabaseSTM
import database.stmt.DatabaseSTM._
import zio._

object DemoApp extends ZIOAppDefault {

  // Now demo only depends on DatabaseSTM
  private val demo: ZIO[DatabaseSTM, Nothing, Unit] = for {
    // initial writes
    _   <- set("a", 10)
    _   <- set("a", 20)
    _   <- set("h", 20)

    // fetch & println
    a   <- get("a")
    _   <- ZIO.succeed(println(s"a == $a"))

    cnt <- count(20)
    _   <- ZIO.succeed(println(s"how many 20s? $cnt"))

    _   <- set("b", 30)

    // nested transactions
    _   <- begin
    _   <- set("c", 40)

    _   <- begin
    _   <- set("f", 11)

    _   <- begin
    _   <- set("g", 80)

    // undo only the innermost
    _   <- rollback

    // inspect
    g   <- get("g"); _ <- ZIO.succeed(println(s"g == $g"))
    f   <- get("f"); _ <- ZIO.succeed(println(s"f == $f"))

    // pop the next two
    _   <- rollback
    _   <- rollback

    // test delete/rollback
    _   <- begin
    _   <- delete("b")
    _   <- rollback
    b   <- get("b")
    _   <- ZIO.succeed(println(s"b == $b"))

    // final commit
    _   <- commit

    // confirm final state
    a  <- get("a"); _ <- ZIO.succeed(println(s"a == $a"))
    b  <- get("b"); _ <- ZIO.succeed(println(s"b == $b"))
    c2  <- get("c"); _ <- ZIO.succeed(println(s"c == $c2"))
    f2  <- get("f"); _ <- ZIO.succeed(println(s"f == $f2"))
    g  <- get("g"); _ <- ZIO.succeed(println(s"g == $g"))
    h   <- get("h"); _ <- ZIO.succeed(println(s"h == $h"))

  } yield ()

  // ONLY provide the DatabaseSTM layer; no Console needed any more
  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Unit] =
    demo.provideLayer(DatabaseSTM.layer)
}

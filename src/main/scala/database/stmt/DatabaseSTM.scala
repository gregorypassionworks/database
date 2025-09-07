package database.stmt

import zio._
import zio.stm._

/**
 * A simple in-memory key→value store with nested transactions (BEGIN/ROLLBACK/COMMIT),
 * backed by ZIO STM.  Values are BigDecimal.
 */
trait DatabaseSTM {
  def set(key: String, value: BigDecimal): UIO[Unit]
  def get(key: String): UIO[String]       // ← no more Console in the signature
  def delete(key: String): UIO[Unit]
  def count(value: BigDecimal): UIO[Int]  // ← return an Int instead of printing
  def begin: UIO[Unit]
  def rollback: UIO[Unit]
  def commit: UIO[Unit]
}

object DatabaseSTM {

  val layer: ULayer[DatabaseSTM] = ZLayer {
    for {
      current   <- TRef.make(Map.empty[String, BigDecimal]).commit
      snapshots <- TRef.make(List.empty[Map[String, BigDecimal]]).commit
      counts    <- TRef.make(Map.empty[BigDecimal, Int]).commit
    } yield new DatabaseSTMImpl(current, snapshots, counts)
  }

  // Accessors, now reflect the new signatures
  def set(key: String, v: BigDecimal): URIO[DatabaseSTM, Unit] =
    ZIO.serviceWithZIO[DatabaseSTM](_.set(key, v))

  def get(key: String): URIO[DatabaseSTM, String] =
    ZIO.serviceWithZIO[DatabaseSTM](_.get(key))

  def delete(key: String): URIO[DatabaseSTM, Unit] =
    ZIO.serviceWithZIO[DatabaseSTM](_.delete(key))

  def count(value: BigDecimal): URIO[DatabaseSTM, Int] =
    ZIO.serviceWithZIO[DatabaseSTM](_.count(value))

  def begin: URIO[DatabaseSTM, Unit] =
    ZIO.serviceWithZIO[DatabaseSTM](_.begin)

  def rollback: URIO[DatabaseSTM, Unit] =
    ZIO.serviceWithZIO[DatabaseSTM](_.rollback)

  def commit: URIO[DatabaseSTM, Unit] =
    ZIO.serviceWithZIO[DatabaseSTM](_.commit)

  // Concrete implementation
  private final class DatabaseSTMImpl(
                                       current:   TRef[Map[String, BigDecimal]],
                                       snapshots: TRef[List[Map[String, BigDecimal]]],
                                       counts:    TRef[Map[BigDecimal, Int]]
                                     ) extends DatabaseSTM {

    override def set(key: String, value: BigDecimal): UIO[Unit] =
      (for {
        m  <- current.get
        _  <- current.set(m.updated(key, value))
        c  <- counts.get
        nc  = c.updated(value, c.getOrElse(value, 0) + 1)
        _  <- counts.set(nc)
      } yield ()).commit

    override def get(key: String): UIO[String] =
      current.get.commit.map { m =>
        m.get(key).map(_.toString).getOrElse("NULL")
      }

    override def delete(key: String): UIO[Unit] =
      (for {
        m  <- current.get
        _  <- current.set(m - key)
        c  <- counts.get
        nc  = m
          .get(key)
          .fold(c)(old => c.updated(old, c(old) - 1))
        _  <- counts.set(nc)
      } yield ()).commit

    override def count(value: BigDecimal): UIO[Int] =
      counts.get.commit.map(_.getOrElse(value, 0))

    override def begin: UIO[Unit] =
      (for {
        m  <- current.get
        ss <- snapshots.get
        _  <- snapshots.set(m :: ss)
      } yield ()).commit

    override def rollback: UIO[Unit] =
      (for {
        ss <- snapshots.get
        _  <- ss match {
          case head :: _ =>
            // 1) Restore 'current' from the top snapshot
            // 2) Rebuild the counts index from that map
            // 3) Clear all remaining snapshots so no further rollbacks fire
            for {
              _   <- current.set(head)
              idx = head.values.groupBy(identity).view.mapValues(_.size).toMap
              _   <- counts.set(idx)
              _   <- snapshots.set(Nil)
            } yield ()
          case Nil =>
            // already empty → nothing to undo
            STM.unit
        }
      } yield ()).commit

    override def commit: UIO[Unit] =
      snapshots.set(Nil).commit
  }
}

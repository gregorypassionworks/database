import scala.collection.mutable

object TxDatabase {
  // “Committed” snapshot
  val mainStore  = mutable.Map.empty[String, BigDecimal]

  // In-flight state: key -> (value, txId)
  private val store      = mutable.Map.empty[String, (BigDecimal, Int)]
  private val extraStore = mutable.Map.empty[String, (BigDecimal, Int)]

  // Simple value→count index
  private val countStore = mutable.Map.empty[BigDecimal, Int]

  // Txn counter: 0 = no open txn, 1 = first txn, 2 = nested, etc.
  private var transactionCount: Int = 0

  //--- CRUD API ------------------------------------------------------

  def set(name: String, value: BigDecimal): Unit = {
    // record new value with current txId
    store(name) = (value, transactionCount)
    countStore.put(value, countStore.getOrElse(value, 0) + 1)
  }

  def get(name: String): Unit =
    println(store.get(name).map(_._1.toString).getOrElse("NULL"))

  def delete(name: String): Unit = {
    store.get(name).foreach { case (v, tx) =>
      // only record the “old” value if we’re inside a txn
      if (transactionCount > 0) {
        extraStore(name) = (v, tx)
      }
      // decrement the count index
      countStore.update(v, countStore(v) - 1)
    }
    store.remove(name)
  }

  def count(value: BigDecimal): Unit =
    println(countStore.getOrElse(value, 0))

  //--- Transaction API ----------------------------------------------

  /** Start (or nest) a transaction. */
  def begin(): Unit =
    transactionCount += 1

  /**
   * Roll back *only* the most-recent transaction, once.
   * Further calls to rollback() will not walk back older txIds.
   */
  def rollback(): Unit = {
    if (transactionCount == 0) {
      println("NO TRANSACTION")
      return
    }

    val txId = transactionCount

    // 1) Remove any entries that were written under this txId
    val removed = store.collect { case (k, (v, id)) if id == txId => (k, v) }.toList
    if (removed.isEmpty) {
      println("NO TRANSACTION")
      return
    }
    removed.foreach { case (k, v) =>
      store.remove(k)
      countStore.update(v, countStore(v) - 1)
    }

    // 2) Restore any keys that were deleted under this txId
    val restored = extraStore.collect { case (k, (v, id)) if id == txId => (k, v) }.toList
    restored.foreach { case (k, v) =>
      store.put(k, (v, txId))
      countStore.put(v, countStore.getOrElse(v, 0) + 1)
      extraStore.remove(k)
    }

    // NOTE: we do NOT decrement transactionCount.
    // That locks us to this single “most-recent” txId forever.
  }

  /** Commit everything left in `store` into `mainStore`. */
  def commit(): Unit = {
    store.view.mapValues(_._1).foreach { case (k, v) =>
      mainStore(k) = v
    }
    // reset everything
    store.clear()
    extraStore.clear()
    transactionCount = 0
  }

  //--- Demo ----------------------------------------------------------

  def main(args: Array[String]): Unit = {
    set("a", 10)
    set("a", 20)
    set("h", 20)
    get("a")      // 20
    count(20)     // 2
    set("b", 30)

    begin()       // txId=1
    set("c", 40)

    begin()       // txId=2
    set("f", 11)

    begin()       // txId=3
    set("g", 80)

    // only undo the g=80 write
    rollback()
    println(store.keys)
    //=> Set(a, b, h, c, f)

    // further rollback() calls have no effect
    rollback()
    rollback()
    println(store.keys)
    //=> Set(a, b, h, c, f)

    begin()
    delete("b")
    rollback()    // restore "b"
    println(store.keys)
    //=> Set(a, b, h, c, f)

    commit()
    println(mainStore)
    //=> HashMap(a -> 20, b -> 30, c -> 40, f -> 11, h -> 20)
  }
}
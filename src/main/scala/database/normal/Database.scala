package database.normal

import scala.collection.mutable

object Database {

  /**
   * Represents a stored value along with transaction metadata.
   *
   * @param value   the BigDecimal value
   * @param inTxn   true if set during an open transaction
   * @param txId    the transaction ID when this entry was written
   */
  private case class Entry(value: BigDecimal, inTxn: Boolean, txId: Int)

  // Committed state (populated by commit())
  private val mainStore  = mutable.Map.empty[String, BigDecimal]

  // In-flight state
  private val store = mutable.Map.empty[String, Entry]
  private val extraStore = mutable.Map.empty[String, Entry]

  // Simple index for count(value)
  private val countStore = mutable.Map.empty[BigDecimal, Int]

  // Tracks whether we're inside any transaction, and its nesting level
  private var beginFlag       = false
  private var transactionCount: Int   = 0

  /**
   * Inserts or updates `name` with `value`.
   * Records the transaction context in the Entry.
   */
  def set(name: String, value: BigDecimal): Unit = {
    val entry = Entry(value, inTxn = beginFlag, txId = transactionCount)
    store.put(name, entry)
    countStore.put(value, countStore.getOrElse(value, 0) + 1)
  }

  /**
   * Retrieves and prints the value for `name`, or NULL if absent.
   */
  def get(name: String): Unit = {
    val text = store.get(name).map(_.value.toString).getOrElse("NULL")
    println(text)
  }

  /**
   * Deletes `name` from the store.
   * If in a transaction, saves the old Entry for potential rollback.
   */
  private def delete(name: String): Unit = {
    store.get(name).foreach { e =>
      if (e.inTxn) {
        extraStore.put(name, Entry(e.value, beginFlag, e.txId))
      }
    }

    // Additional save-if-inTransaction logic preserved
    if (beginFlag) {
      store.get(name).foreach { e =>
        extraStore.put(name, Entry(e.value, beginFlag, e.txId))
      }
    }

    store.remove(name)
  }

  /**
   * Counts how many keys currently hold the given `value`.
   */
  def count(value: BigDecimal): Unit = {
    println(countStore.getOrElse(value, 0))
  }

  /**
   * Begins a new (or nested) transaction.
   * Sets a flag and increments the transaction counter.
   */
  def begin(): Unit = {
    transactionCount += 1
    beginFlag = true
  }

  /**
   * Rolls back the most recent transaction:
   * 1) Removes any entries written under the current txId
   * 2) Restores any entries saved in extraStore
   */
  private def rollback(): Unit = {
    // 1) remove new writes from this tx
    store.foreach { case (k, e) =>
      if (e.inTxn && e.txId == transactionCount) {
        store.remove(k)
        countStore.update(e.value, countStore(e.value) - 1)
      }
    }

    // 2) restore deleted entries
    extraStore.foreach { case (k, e) =>
      store.put(k, e)
    }
    extraStore.clear()
  }

  /**
   * Commits all in-flight changes into mainStore,
   * then clears the in-flight state.
   */
  private def commit(): Unit = {
    store.view.mapValues(_.value).foreach { case (k, v) =>
      mainStore.put(k, v)
    }
    store.clear()
  }

  /** A quick demo of how the API works. */
  def main(args: Array[String]): Unit = {
    set("a", 10)
    set("a", 20)
    set("h", 20)
    get("a")            // prints 20
    count(20)           // prints 2
    set("b", 30)

    begin()
    set("c", 40)

    begin()
    set("f", 11)

    begin()
    set("g", 80)

    // rollback nested writes (g, then f, then c)
    rollback()
    rollback()
    println(mainStore)  // still empty

    rollback()
    count(80)           // prints 0

    begin()
    delete("b")
    rollback()
    delete("m")
    count(30)           // prints 1

    commit()
    println(mainStore)  // HashMap(a -> 20, b -> 30, c -> 40, f -> 11, h -> 20)
  }
}

package database

import scala.collection.mutable

object Database {
  val mainStore = mutable.Map[String, BigDecimal]()
  var store = mutable.Map[String, (BigDecimal, Boolean, Int)]()
  var extraStore = mutable.Map[String, (BigDecimal, Boolean, Int)]()
  var countStore = mutable.Map[BigDecimal, Int]()
  private var beginFlag: Boolean = false
  var transactionCount: Int = 0
  def set(name: String, value: BigDecimal): Unit = {
    store.put(name, (value, beginFlag, transactionCount))
    countStore.put(value, countStore.getOrElse(value, 0) + 1)
  }

  def get(name: String): Unit = {
    val value = store.get(name).map(_.toString()).getOrElse("NULL")
    println(value)
  }

  def delete(name: String): Unit = {
    store.get(name).foreach(tuple => if(tuple._2) extraStore.put(name, (tuple._1, beginFlag, tuple._3)) else ())
    if(beginFlag) store.get(name).foreach(tuple => extraStore.put(name, (tuple._1, beginFlag, tuple._3)))

    store.remove(name)
  }

  def count(value: BigDecimal): Unit = {
    val size = countStore.getOrElse(value, 0)
    println(size)
  }

  def begin(): Unit = {
    transactionCount += 1
    beginFlag = true
  }

  def rollback(): Unit = {
    store.foreach { tuple =>
      if(tuple._2._2 && tuple._2._3 == transactionCount) {
        store.remove(tuple._1)
        countStore.update(tuple._2._1, countStore.get(tuple._2._1).map(v => v - 1).getOrElse(0))
      } else ()
      extraStore.foreach(tuple => store.put(tuple._1, (tuple._2._1, tuple._2._2, tuple._2._3)))
      extraStore.clear()
    }
  }

  def commit(): Unit = {
    store.view.mapValues(tuple => tuple._1).foreach(s => mainStore.put(s._1, s._2))
    store.clear()
  }

  def main(args: Array[String]): Unit = {
    set("a", 10)
    set("a", 20)
    set("h", 20)
    get("a")
    count(20)
    set("b", 30)
    begin()
    set("c", 40)
    begin()
    set("f", 11)
    begin()
    set("g", 80)
    rollback()
    rollback()
    println(mainStore)
    rollback()
    count(80)

    begin()
    delete("b")
    rollback()
    count(30)
    commit()
    println(mainStore)
  }
}
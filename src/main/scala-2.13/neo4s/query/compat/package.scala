package neo4s.query

package object compat {
  type LazyList[A] = scala.LazyList[A]
  val LazyList = scala.LazyList
}

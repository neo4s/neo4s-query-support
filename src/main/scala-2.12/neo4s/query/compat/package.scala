package neo4s.query

package object compat {
  type LazyList[A] = Stream[A]
  val LazyList = scala.Stream
}

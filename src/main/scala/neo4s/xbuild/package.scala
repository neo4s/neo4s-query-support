package neo4s

import scala.collection.immutable.Stream

package object xbuild {
  type LazyList[T]=Stream[T]

}

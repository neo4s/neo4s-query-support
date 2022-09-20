package neo4s.common.syntax

import neo4s.query.compat._

import scala.collection.mutable.ArrayBuffer

object LazyListSyntax {
  implicit class LazyListOps[A](self: LazyList[A]) {

    def uniq(implicit lt: (A, A) => Boolean): LazyList[A] = {
      def ordering(implicit lt: (A, A) => Boolean) = Ordering fromLessThan lt

      if (self.isEmpty) LazyList.empty[A]
      else {
        var rest = self.tail
        while (rest.nonEmpty && ordering.equiv(self.head,rest.head)) rest = rest.tail
        LazyList.cons(self.head, rest.uniq)
      }
    }

    def lazyGroupBy[K,V](splitFn: A => (K,Option[V])): LazyList[(K,Seq[V])] = {
      if (self.isEmpty) LazyList.empty[(K,Seq[V])]
      else {
        val collection = new ArrayBuffer[V]()

        val (headKey,headValue) = splitFn(self.head)
        headValue.foreach(collection.append(_))

        var rest = self.tail
        while (rest.nonEmpty && headKey == splitFn(rest.head)._1) {
          splitFn(rest.head)._2.foreach(collection.append(_))
          rest = rest.tail
        }
        LazyList.cons((headKey,collection.toList),rest.lazyGroupBy(splitFn))
      }
    }

    def on(fn: A => Unit): LazyList[A] = fireWhen(_ => true)(fn)

    def fireWhen(predicate: A => Boolean)(fn: A => Unit): LazyList[A] = {
      if (self.isEmpty) LazyList.empty[A]
      else {
        if (predicate(self.head)) fn(self.head)
        LazyList.cons(self.head, self.tail.fireWhen(predicate)(fn))
      }
    }

    def eol(fn: => Unit): LazyList[A] = {
      if (self.isEmpty) {
        fn
        LazyList.empty[A]
      } else LazyList.cons(self.head, self.tail.eol(fn))
    }

  }

}

package neo4s.common.syntax

import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

import neo4s.query.compat._

class LazyListSyntaxSpec extends AnyWordSpec with should.Matchers {

  "LazyListSyntax" should {
    "provide conditional callback on traversal" in {
      import LazyListSyntax._
      val ints = 1 #:: 2 #:: 3 #:: 3 #:: 5 #:: 25 #:: 25 #:: 25 #:: LazyList.empty[Int]
      var odds = 0
      val oddsCounter = ints.fireWhen(_ % 2 == 1)(x => odds += x)
      oddsCounter.force
      odds shouldBe 87
    }

    "provide a callback on traversal" in {
      import LazyListSyntax._
      val ints = 1 #:: 2 #:: 3 #:: 3 #:: 5 #:: 25 #:: 25 #:: 25 #:: LazyList.empty[Int]
      var size = 0
      val counter = ints.on(x => size += 1)
      counter.force
      size shouldBe 8
    }

    "provide a filter for removing duplicate values" in {
      import LazyListSyntax._

      implicit val lt: (Int, Int) => Boolean = (_ < _)

      val ints = 1 #:: 2 #:: 3 #:: 3 #:: 5 #:: 25 #:: 25 #:: 25 #:: LazyList.empty[Int]
      val deduped = ints.uniq

      deduped.size shouldBe 5
    }

    "provide a callback for end of list" in {
      import LazyListSyntax._

      val ints = 1 #:: 2 #:: 3 #:: 3 #:: 5 #:: 25 #:: 25 #:: 25 #:: 26 #:: LazyList.empty[Int]
      var atEnd = false
      val closer = ints.eol {
        atEnd = true
      }
      closer.force
      atEnd shouldBe true
    }

    "provide a mechanism for a lazy groupBy" in {
      import LazyListSyntax._

      val pairs = (1, "for") #:: (1, "the") #:: (1, "money") #::
        (2, "for") #:: (2, "the") #:: (2, "show") #::
        (3, "to") #:: (3, "get") #:: (3, "ready") #::
        (4, "to") #:: (4, "go!") #::
        LazyList.empty

      val grouped = pairs.lazyGroupBy {
        case (k, v) => (k, Option(v))
      }

      grouped.size shouldBe 4
      grouped.head._1 shouldBe 1
      grouped.head._2 shouldBe List("for", "the", "money")

      grouped.last._1 shouldBe 4
      grouped.last._2 shouldBe List("to", "go!")
    }
  }
}

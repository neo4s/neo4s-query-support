package neo4s.query

import neo4s.query.QueryTools.ExtractionBehavior
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class ExtractionProviderSpec extends AnyWordSpec with should.Matchers {

  "ExrtractionPovider" should {
    "extract a simple case class" in {
      import ExtractionProvider._
      case class Person(firstName: String, lastName: String, id: Int)

      val norris = Person("Chuck","Norris", 1)
      val norrisValues = Map("firstName"->"Chuck", "lastName"->"Norris", "id"->1)

      val banderas = Person("Antonio","Banderas",2)
      val banderasValues = Map("firstName"->"Antonio", "lastName"->"Banderas","id"->2)

      implicit val personExtractor = extractorFor[Person]

      val norrisFromValues = personExtractor.from(norrisValues)
      norrisFromValues shouldBe Some(norris)

      val banderasFromValues = personExtractor.from(banderasValues)
      banderasFromValues shouldBe Some(banderas)
    }

    "extract a case class with non-scalar values" in {
      import ExtractionProvider._
      case class Person(firstName: String, lastName: String, id: UUID)

      val norris = Person("Chuck","Norris", UUID.randomUUID())
      val norrisValues = Map("firstName"->"Chuck", "lastName"->"Norris", "id"->norris.id)

      val banderas = Person("Antonio","Banderas",UUID.randomUUID())
      val banderasValues = Map("firstName"->"Antonio", "lastName"->"Banderas","id"->banderas.id)

      implicit val extractionBehavior: ExtractionBehavior = ExtractionBehavior(typeMapper = {
        case tuple@("id", idString: String) => (tuple._1,UUID.fromString(idString))
      })

      implicit val personExtractor = extractorFor[Person]

      val norrisFromValues = personExtractor.from(norrisValues)
      norrisFromValues shouldBe Some(norris)

      val banderasFromValues = personExtractor.from(banderasValues)
      banderasFromValues shouldBe Some(banderas)
    }

  }

}

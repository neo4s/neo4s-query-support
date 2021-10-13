package neo4s.query

import neo4s.query.ExtractionProvider.extractorFor
import org.neo4j.driver.internal.value.NullValue
import org.neo4j.driver.util.Resource
import org.neo4j.driver.{Record, Result, Value}
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec
import org.scalamock.scalatest.MockFactory

import java.util.UUID
import scala.jdk.CollectionConverters._

class QueryToolsSpec  extends AnyWordSpec with MockFactory with should.Matchers {

  def mockValueFor(map: Map[String,AnyRef]): Value = {
    val valueMock = mock[Value]
    (valueMock.asMap _ : () => java.util.Map[String,AnyRef]).expects().returning(map.asJava)
    valueMock
  }

  def mockResultsFor(resultsMap: List[Map[String,Map[String,AnyRef]]]): Result = {
    val resultsValueMap = resultsMap.map(_.view.mapValues(mockValueFor))
    val resultsIterator = resultsValueMap.iterator

    val resultMock = mock[Result]
    (resultMock.hasNext _).expects().onCall( _ => resultsIterator.hasNext).repeat(resultsMap.size + 1)
    (resultMock.next _)
    .expects()
    .onCall { _ =>
      val nextRecord = resultsIterator.next()
      val recordMock = mock[Record]
      (recordMock.get(_:String))
        .expects(*)
        .onCall { id: String =>nextRecord.getOrElse(id,NullValue.NULL) }
        .repeat(nextRecord.size)
      recordMock
    }
    .repeat(resultsMap.size)

    resultMock
  }

  case class Person(firstName: String, lastName: String, id: UUID)

  val chuckId = UUID.randomUUID()
  val chuck = Person("Chuck", "Norris", chuckId)
  val chuckValues = Map("person" -> Map("firstName" -> "Chuck", "lastName" -> "Norris", "id" -> chuckId))

  val vinId = UUID.randomUUID()
  val vin = Person("Vin", "Diesel", vinId)
  val vinValues = Map("person" -> Map("firstName" -> "Vin", "lastName" -> "Diesel", "id" -> vinId))

  val expectedPersons = Set(
    chuck,
    vin
  )

  case class Article(name: String, descriptor: String)
  case class Wears(consistency: String)

  val hat = Article("hat", "black")
  val hatValues = Map("article" -> Map("name" -> "hat", "descriptor" -> "black"))

  val shirt = Article("shirt", "tight")
  val shirtValues = Map("article" -> Map("name" -> "shirt", "descriptor" -> "tight"))

  val boots = Article("boots", "dusty")
  val bootsValues = Map("article" -> Map("name" -> "boots", "descriptor" -> "dusty"))

  val expectedEdgeNodes = Set(
    (chuck, Some(hat)),
    (chuck, Some(boots)),
    (vin, Some(shirt))
  )

  val always = Wears("always")
  val alwaysValues = Map("wears" -> Map("consistency" -> "always"))

  val usually = Wears("usually")
  val usuallyValues = Map("wears" -> Map("consistency" -> "usually"))

  val sometimes = Wears("sometimes")
  val sometimesValues = Map("wears" -> Map("consistency" -> "sometimes"))

  val expectedEdges = Set(
    (chuck, Some((sometimes,hat))),
    (chuck, Some((usually,boots))),
    (vin, Some((always,shirt)))
  )

  def mockedNodesQuery(): Result = {
    val resultMap = chuckValues ::vinValues :: Nil
    mockResultsFor(resultMap)
  }

  def mockedEdgeNodesQuery(): Result = {
    val resultMap = (chuckValues ++ hatValues) :: (chuckValues ++ bootsValues) :: (vinValues ++ shirtValues) :: Nil
    mockResultsFor(resultMap)
  }

  def mockedEdgesQuery(): Result = {
    val resultMap = (chuckValues ++ sometimesValues ++ hatValues) :: (chuckValues ++ usuallyValues ++ bootsValues) :: (vinValues ++ alwaysValues ++ shirtValues) :: Nil
    mockResultsFor(resultMap)
  }

  "QueryTools" should {
    "derive a lazy list of records from a query result" in {
      import QueryTools._

      val results = mockedNodesQuery()
      val resource = stub[Resource]
      val resultStream = results.recordList(resource)

      resultStream.foreach { record =>
        val valueMap = record.get("person").asMap().asScala
        valueMap.keySet shouldBe Set("firstName", "lastName", "id")
        Set("Chuck", "Vin") should contain(valueMap("firstName"))
      }
    }

    "extract a lazy list of nodes from a query result" in {
      import QueryTools._

      implicit val extractionBehavior: ExtractionBehavior = ExtractionBehavior(typeMapper = {
        case tuple@("id", idString: String) => (tuple._1,UUID.fromString(idString))
      })

      implicit val personExtractor = extractorFor[Person]

      val results = mockedNodesQuery()
      val resource = stub[Resource]

      results.valueList(resource,"person").foreach { person =>
        expectedPersons should contain (person)
      }
    }

    "extract a lazy list of edge nodes from a query result" in {
      import QueryTools._

      implicit val extractionBehavior: ExtractionBehavior = ExtractionBehavior(typeMapper = {
        case tuple@("id", idString: String) => (tuple._1,UUID.fromString(idString))
      })

      implicit val personExtractor = extractorFor[Person]
      implicit val articleExtractor = extractorFor[Article]

      val results = mockedEdgeNodesQuery()
      val resource = stub[Resource]

      results.edges[Person,Article](resource, "person", "article").foreach { edgeNodes =>
        expectedEdgeNodes should contain (edgeNodes)
      }
    }

    "extract a lazy list of edges from a query result" in {
      import QueryTools._

      implicit val extractionBehavior: ExtractionBehavior = ExtractionBehavior(typeMapper = {
        case tuple@("id", idString: String) => (tuple._1,UUID.fromString(idString))
      })

      implicit val personExtractor = extractorFor[Person]
      implicit val wearsExtractor = extractorFor[Wears]
      implicit val articleExtractor = extractorFor[Article]

      val results = mockedEdgesQuery()
      val resource = stub[Resource]

      results.edges[Person,Wears,Article](resource, "person", "wears", "article").foreach { edge =>
        println(edge)
        expectedEdges should contain (edge)
      }
    }
  }


}

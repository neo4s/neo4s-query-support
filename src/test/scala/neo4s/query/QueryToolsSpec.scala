package neo4s.query

import neo4s.query.ExtractionProvider.extractorFor
import org.neo4j.driver.util.Resource
import org.neo4j.driver.{Record, Result, Value}
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec
import org.scalamock.scalatest.MockFactory

import java.util.UUID
import scala.jdk.CollectionConverters._

class QueryToolsSpec  extends AnyWordSpec with MockFactory with should.Matchers {

  case class Person(firstName: String, lastName: String, id: UUID)

  def mockValueFor(map: Map[String,AnyRef]): Value = {
    val valueMock = mock[Value]
    (valueMock.asMap _ : () => java.util.Map[String,AnyRef]).expects().returning(map.asJava)
    valueMock
  }

  def mockResultsFor(resultsMap: List[Map[String,Map[String,AnyRef]]]): Result = {
    val resultsValueMap = resultsMap.map(_.view.mapValues(mockValueFor))
    val resultsIterator = resultsValueMap.iterator

    val resultMock = mock[Result]
    (resultMock.hasNext _).expects().onCall( _ => resultsIterator.hasNext)
    (resultMock.next _).expects().onCall { _ =>
      val nextRecord = resultsIterator.next()
      val recordMock = mock[Record]
      (recordMock.get(_:String)).expects(*).onCall { id: String => nextRecord(id) }
      recordMock
    }

    resultMock
  }

  def mockedPersonQuery(): Result = {
    val chuckValues = Map("person" -> Map("firstName" -> "Chuck", "lastName" -> "Norris", "id" -> UUID.randomUUID()))
    val vinValues = Map("person" -> Map("firstName" -> "Vin", "lastName" -> "Deisel", "id" -> UUID.randomUUID()))

    val resultMap = Map("person" -> chuckValues) :: Map("person" -> vinValues) :: Nil
    mockResultsFor(resultMap)

    val chuckMock = mockValueFor(chuckValues)
    val vinMock =   mockValueFor(vinValues)

    val resultMock = mock[Result]
    val recordMock = mock[Record]

    (recordMock.get(_:String)).expects("person").returning(chuckMock)
    (recordMock.get(_:String)).expects("person").returning(vinMock)

    // Set up mock for Result api...
    (resultMock.hasNext _).expects().returning(true)
    (resultMock.next _).expects().returning(recordMock)
    (resultMock.hasNext _).expects().returning(true)
    (resultMock.next _).expects().returning(recordMock)
    (resultMock.hasNext _).expects().returning(false)

    resultMock
  }

  "QueryTools" should {
    "derive a lazy list of records from a query result" in {
      import QueryTools._

      val results = mockedPersonQuery()
      val resource = stub[Resource]
      val resultStream = results.recordList(resource)

      resultStream.foreach { record =>
        val valueMap = record.get("person").asMap().asScala
        valueMap.keySet shouldBe Set("firstName", "lastName", "id")
        Set("Chuck", "Vin") should contain(valueMap("firstName"))
      }
    }

    "extract a lazy list of extracted values from a query result" in {
      import QueryTools._

      implicit val extractionBehavior: ExtractionBehavior = ExtractionBehavior(typeMapper = {
        case tuple@("id", idString: String) => (tuple._1,UUID.fromString(idString))
      })

      implicit val personExtractor = extractorFor[Person]

      val results = mockedPersonQuery()
      val resource = stub[Resource]

      results.valueList(resource,"person").foreach(v => println(v))
    }
  }


}

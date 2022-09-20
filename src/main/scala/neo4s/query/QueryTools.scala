package neo4s.query

import ExtractionProvider.defaultTypeMapper
import org.neo4j.driver.util.Resource
import org.neo4j.driver._
import org.neo4j.driver.internal.value.NullValue

import neo4s.query.compat._
import scala.collection.JavaConverters._

/**
 * Implicits and types for helping with query parameter specifications, result handling and streaming, and other nice
 * scala wrappers for java-based driver primitives and semantics.
 */
object QueryTools {

  case class ExtractionBehavior(typeMapper: PartialFunction[(String, Any), (String, Any)] = defaultTypeMapper)

  implicit class ResultTools(result: Result) {
    import neo4s.common.syntax.LazyListSyntax._

    private def recordList: LazyList[Record] = {
      def nextRecord: LazyList[Record] =
        if (result.hasNext) result.next() #:: nextRecord
        else LazyList.empty[Record]

      nextRecord
    }

    def recordList(resource: Resource): LazyList[Record] = recordList.eol(resource.close())

    def single[T <: Product](id: String)(implicit extractor: ExtractionProvider[T]): Option[T] = {
      val record = result.list().asScala.headOption
      record.flatMap(_.getValues(id)).flatMap(values => extractor.from(values))
    }

    def valueList[T <: Product](resource: Resource, id: String)(implicit extractor: ExtractionProvider[T]): LazyList[T] = {
      val valueList = recordList.flatMap(_.getValues(id))
      valueList.flatMap(values => extractor.from(values)).eol(resource.close())
    }

    def edge[L <: Product, R <: Product](idLeft: String, idRight: String)(implicit lExtractor: ExtractionProvider[L], rExtractor: ExtractionProvider[R]): Option[(L, Option[R])] = {
      val record = result.list().asScala.headOption
      record.flatMap(_.getEdge[L,R](idLeft,idRight))
    }

    def edge[L <: Product, O <: Product, R <: Product](idLeft: String, idRelationship: String, idRight: String)
                                                      (implicit lExtractor: ExtractionProvider[L], oExtractor: ExtractionProvider[O], rExtractor: ExtractionProvider[R]): Option[(L, Option[(O,R)])] = {
      val record = result.list().asScala.headOption
      record.flatMap(_.getEdge[L,O,R](idLeft,idRelationship,idRight))
    }

    def edges[L <: Product, R <: Product](resource: Resource, idLeft: String, idRight: String)(implicit lExtractor: ExtractionProvider[L], rExtractor: ExtractionProvider[R]): LazyList[(L, Option[R])] = {
      recordList.flatMap(_.getEdge[L,R](idLeft,idRight)).eol(resource.close())
    }

    def edges[L <: Product, O <: Product, R <: Product](resource: Resource, idLeft: String, idRelationship: String, idRight: String)
                                         (implicit lExtractor: ExtractionProvider[L], oExtractor: ExtractionProvider[O], rExtractor: ExtractionProvider[R]): LazyList[(L, Option[(O,R)])] = {
      recordList.flatMap(_.getEdge[L,O,R](idLeft,idRelationship,idRight)).eol(resource.close())
    }
  }

  implicit class RecordTools(record: Record) {
    def getValues(id: String): Option[Map[String,AnyRef]] = {
      record.get(id) match {
        case _: NullValue => None
        case value: Value => Some(value.asMap().asScala.toMap)
      }
    }

    def getEdge[L <: Product, R <: Product](idLeft: String, idRight: String)
                                           (implicit lExtractor: ExtractionProvider[L], rExtractor: ExtractionProvider[R]): Option[(L, Option[R])] = {
      val maybeLeft = getValues(idLeft).flatMap(values => lExtractor.from(values))
      val maybeRight = getValues(idRight).flatMap(values => rExtractor.from(values))

      maybeLeft.map(left => (left,maybeRight))
    }

    def getEdge[L <: Product, O <: Product, R <: Product](idLeft: String, idRelationship: String, idRight: String)
                                                         (implicit lExtractor: ExtractionProvider[L], oExtractor: ExtractionProvider[O], rExtractor: ExtractionProvider[R]): Option[(L, Option[(O,R)])] = {
      val maybeLeft = getValues(idLeft).flatMap(value => lExtractor.from(value))
      val maybeRelationship = getValues(idRelationship).flatMap(value => oExtractor.from(value))
      val maybeRight = getValues(idRight).flatMap(value => rExtractor.from(value))

      val edge = maybeRelationship.flatMap(relationship => maybeRight.map(right => (relationship,right)))
      maybeLeft.map(left => (left,edge))
    }
  }

  implicit class SessionTools(session: Session) {
    def withDurableTransaction[T](fn: Transaction => T): T = {
      val transaction = session.beginTransaction()
      fn(transaction)
    }

    def withWriteTransaction[T](fn: Transaction => T): T = {
      session.writeTransaction(transactionWork(fn))
    }

    def withReadTransaction[T](fn: Transaction => T): T = {
      session.readTransaction(transactionWork(fn))
    }

    private def transactionWork[T](fn: Transaction => T): TransactionWork[T] = (tx: Transaction) => fn(tx)
  }

  import scala.language.implicitConversions
  implicit def scalaParam2Java(parameters: Map[String,Any]): java.util.Map[String,AnyRef] =
    parameters.mapValues(_.asInstanceOf[AnyRef]).toMap.asJava
}
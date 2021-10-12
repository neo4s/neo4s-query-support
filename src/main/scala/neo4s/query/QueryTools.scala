package neo4s.query

import ExtractionProvider.defaultTypeMapper
import org.neo4j.driver.util.Resource
import org.neo4j.driver._
import org.neo4j.driver.internal.value.NullValue

import scala.jdk.CollectionConverters._
/**
 * Implicits and types for helping with query parameter specifications, result handling and streaming, and other nice
 * scala wrappers for java-based driver primitives and semantics.
 *
 * Conversion of result set to a self-closing lazy list:
 * {{{
 *    import aif.dna.neo4j.QueryTools._
 *    import aif.dna.cypher.dsl.syntax._
 *
 *    val query = cypher.MATCH(anyNode).toQuery()
 *
 *    // A durable session is not closed when we leave the `withDriver` scope
 *    val resultList = withDriver(_.withDurableSession(AccessMode.READ) { session =>
 *      // Attaches the durabale session to the list
 *      session.run(query.query,query.queryMap).recordList(session)
 *    })
 *
 *    // The durable session is closed when the lazy list is at end. Forcing the lazy list will close it.
 *    resultList.force()
 * }}}
 *
 * Implicit lifting of result records to case classes.
 * {{{
 *    case class Person(id: UUID, firstName: String, ts: LocalDateTime)
 *
 *    // Extraction behavior. Overrides for type conversion that is one post-read and pre-lift to node case classes
 *    implicit val extractionBehavior = ExtractionBehavior(typeMapper = {
 *      case tuple@("id", idString: String) => (tuple._1,UUID.fromString(idString))
 *    })
 *
 *    // Generates a typeclass extractor for the Person case class
 *    implicit val personExtractor = extractorFor[Person]
 *
 *    val query = cyper.MATCH(any[Person]).toQuery()
 *
 *    // The foregoing is all we need to define to get some really nice help from QueryTools...
 *    val people: LazyList[Person] = withDriver(_withDurableSession(AccessMode.READ) { session =>
 *      // The person node will be labelled "person" in the generated query
 *      session.run(query.query,query.queryMap).stream(session,"person")
 *    }
 *
 *    people.force() // consume the lazy list to close the session
 * }}}
 *
 * We can also lift edges. Edges are `(node) -[:relation]-> (node)` patterns. The principle is exactly the same,
 * but we need more extractors...
 * {{{
 *    case class Department(id: UUID, name: String)
 *    case class Person(id: UUID, firstName: String)
 *    case class WorksIn(status: String)
 *
 *    // Extraction behavior. Overrides for type conversion that is one post-read and pre-lift to node case classes
 *    implicit val extractionBehavior = ExtractionBehavior(typeMapper = {
 *      case tuple@("id", idString: String) => (tuple._1,UUID.fromString(idString))
 *    })
 *
 *    implicit val PersonExtractor = extractorFor[Person]
 *    implicit val DepartmentExtractor = extractorFor[Department]
 *    implicit val WorksForExtractor = extractorFor[WorksFor]
 *
 *    val queryAllPeople = cypher
 *      .MATCH(anyPerson -| anyWorksFor |-> anyDepartment)
 *      .RETURN(anyPerson,anyWorksFor,anyDepartment)
 *    val query = queryAllPeople.toQuery()
 *
 *    // Query returns a list of edges in the records. For instance a person who reports to two departments would be
 *    // LazyList((p) -[:WORKS_FOR]-> (d1), (p) -[:WORKS_FOR]-> (d2))
 *
 *    val people = withDriver(_.withDurableSession { session =>
 *      // Edges extracts the record items labelled "person", "works_for", and "department", which are leftnode, relationship, rightnode, respectively
 *      // The type for the edge with these extractors is `(Person,Option[WorksFor,Department]))
 *      val edges = session.run(query.query,query.queryMap).edges[Department,WorksFor,Department](session,"person","works_for","department")
 *
 *      // We can map that to the [[model.Person]] model object like:
 *      edges.map { case (person, Some(worksFor, department)) => model.Person(person.id, person.firstName, department = department.name, ... status = worksFor.status)
 *    })
 *
 *    // Remember to exhaust the lazy list to close the session.. .
 *    people.force()
 *
 * }}}
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
  implicit def scalaParam2Java(parameters: Map[String,Any]): java.util.Map[String,AnyRef] = parameters.view.mapValues(_.asInstanceOf[AnyRef]).toMap.asJava
}
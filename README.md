# neo4s-query-support

Implicits and types for helping with query parameter specifications, result handling and streaming, and other nice
scala wrappers for java-based driver primitives and semantics.

## Motivation
Neo4j is a natural fit for storing knowledge graphs and representing and querying large scale data based upon its internal
relationships. Scala is a very nice fit for representing linked information in a scalable and type-safe way. Moving data from 
Neo4j query results to scala types in a safe and idiomatic way makes the mariage of these two technologies a safer and easier. 
That is what the [neo4s-query-support](#) project does.

Specifically, this package:
* Supports lifting Neo4j query results to Scala case classes.
* Supports memory-efficient streaming of results to scala collections

## Installation

#### SBT

```scala
libraryDependencies += "io.github.neo4s" %% "neo4s-query-support" % "0.1.2"
```

## Examples

**NOTE** - [neo4s-query-support](#) supports the lifing of results into the `Stream` class, which is deprecated in scala 2.13. To address
this we've created type alias for the new `LazyList` class so that, when the target project utilizes scala 2.12, `LazyList` is a `Stream`
class, otherwise it is `LazyList`

### Conversion of result set to a self-closing lazy list
The following example makes use of the neo4s cypher dsl available at (TODO).
``` scala 
import aif.dna.neo4j.QueryTools._
import aif.dna.cypher.dsl.syntax._

import java.util.UUID

case class Person(firstName: String, lastName: String, id: UUID)

val anyPerson = any[Person]
val query = cypher.MATCH(anyPerson).toQuery()

// A durable session is not closed when we leave the `withDriver` scope
val resultList = withDriver(_.withDurableSession(AccessMode.READ) { session =>
     // Attaches the durabale session to the list
     session.run(query.query,query.queryMap).recordList(session)
})
 
// The durable session is closed when the lazy list is at end. Forcing the lazy list will close it.
resultList.foreach { record =>
    println(record.get("person").asMap().asScala) 
}
```
For a database that has two Person nodes present the example above yields:
``` text
Map(firstName -> Chuck, lastName -> Norris, id -> 614a23a5-00e2-4951-8689-b6fa1ca54f28)
Map(firstName -> Vin, lastName -> Diesel, id -> 4a707a16-aba2-4ab7-aec4-e29b05efcf1d)
```

### Implicit lifting of result records to case classes
``` scala 
import aif.dna.neo4j.QueryTools._
import aif.dna.cypher.dsl.syntax._

import java.util.UUID

case class Person(id: UUID, firstName: String, lastName: String)

// Extraction behavior. Overrides for type conversion that is one post-read and pre-lift to node case classes
implicit val extractionBehavior = ExtractionBehavior(typeMapper = {
     case tuple@("id", idString: String) => (tuple._1,UUID.fromString(idString))
})

// Generates a typeclass extractor for the Person case class
implicit val personExtractor = extractorFor[Person]

val query = cyper.MATCH(any[Person]).toQuery()

// The foregoing is all we need to define to get some really nice help from QueryTools...
val people: LazyList[Person] = withDriver(_withDurableSession(AccessMode.READ) { session =>
     // The person node will be labelled "person" in the generated query
     session.run(query.query,query.queryMap).stream(session,"person")
}

people.foreach(println) // consume the lazy list to close the session
```
The foregoing yields the following output: 
``` text
Person(Chuck,Norris,a50d1008-889f-40e3-bfda-5719703331c1)
Person(Vin,Diesel,5f1c7234-b81c-4089-b582-dc75d69ab780)
```
### We can also lift edge nodes and edges 
Edges are `(node) -[:relation]-> (node)` patterns. The principle is exactly the same,
but we need more extractors. There are two patterns that are supported; either including 
the relationship for the edge or just the nodes. The following example
provides for the latter.

``` scala 
case class Person(firstName: String, lastName: String, id: UUID)
case class Article(name: String, descriptor: String)
case class Wears(consistency: String)

// Extraction behavior. Overrides for type conversion that is one post-read and pre-lift to node case classes
implicit val extractionBehavior = ExtractionBehavior(typeMapper = {
     case tuple@("id", idString: String) => (tuple._1,UUID.fromString(idString))
})

implicit val PersonExtractor = extractorFor[Person]
implicit val DepartmentExtractor = extractorFor[Department]
implicit val WorksForExtractor = extractorFor[WorksFor]

val anyPerson = any[Person]
val anyWears = any[Wears]
val anyArticle = any[Article]

val queryAllPeople = cypher
     .MATCH(anyPerson -| anyWears |-> anyArticle)
     .RETURN(anyPerson,anyWears,anyArticle)
val query = queryAllPeople.toQuery()

// Query returns a list of edges in the records. For instance a person who reports to two departments would be
// LazyList((person: Person) -[wears:WEARS]-> (article: Article), (person: Person) -[wears:WEARS]-> (article: Article))

val people = withDriver(_.withDurableSession { session =>
     // Edges extracts the record items labelled "person", "works_for", and "department", which are leftnode, relationship, rightnode, respectively
     // The type for the edge with these extractors is `(Person,Option[Wears,Article]))
     session.run(query.query,query.queryMap).edges[Department,Wears,Article](session,"person","wears","article")
})

// Remember to exhaust the lazy list to close the session.. .
people.foreach(println)
```
This example produces: 
``` text
(Person(Chuck,Norris,2f374677-4dae-47d9-9e50-2e69c75abe3c),Some((Wears(sometimes),Article(hat,black))))
(Person(Chuck,Norris,2f374677-4dae-47d9-9e50-2e69c75abe3c),Some((Wears(usually),Article(boots,dusty))))
(Person(Vin,Diesel,e8f5ba47-93d9-45a5-9597-3b848293defa),Some((Wears(always),Article(shirt,tight))))
```

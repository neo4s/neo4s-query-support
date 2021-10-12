package neo4s.query

import com.typesafe.scalalogging.LazyLogging
import neo4s.query.auth.Authenticator
import org.neo4j.driver.{AccessMode, AuthToken, Driver, GraphDatabase, Session, SessionConfig}

object Neo4jDriver {
  import Authenticator._

  def apply(settings: Neo4jConfig): Neo4jDriver = {
    val token = settings.authenticator.authenticate(settings.credentials)
    new Neo4jDriver(settings.uri,token)
  }

  def apply(uri: String, token: AuthToken): Neo4jDriver = new Neo4jDriver(uri,token)
  def apply(uri: String, authenticator: Authenticator.Value, credentials: Map[String,String]): Neo4jDriver =
    new Neo4jDriver(uri,authenticator.authenticate(credentials))
}

class Neo4jDriver(serverUri: String, authToken: AuthToken) extends LazyLogging {
  lazy val driver: Driver = GraphDatabase.driver(serverUri,authToken)

  def withSession[T](am: AccessMode)(fn: Session => T): T =
    wrapWith(driver.session(SessionConfig.builder().withDefaultAccessMode(am).build))(fn)

  def withSession[T](fn: Session => T): T = wrapWith(driver.session)(fn)

  def withAutoClosingSession[T](am: AccessMode)(fn: Session => T): T =
    wrapWithClosingSession(driver.session(SessionConfig.builder().withDefaultAccessMode(am).build))(fn)

  def withAutoClosingSession[T](fn: Session => T): T = wrapWithClosingSession(driver.session)(fn)

  def close(): Unit = driver.close

  private def wrapWith[T](session: Session)(fn: Session => T): T = fn(session)

  private def wrapWithClosingSession[T](session: Session)(fn: Session => T): T = {
    val results = fn(session)
    session.close()
    results
  }
}

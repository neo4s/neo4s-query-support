package neo4s.query

import neo4s.query.auth.Authenticator

case class Neo4jConfig(uri: String, authenticator: Authenticator.Value, credentials: Map[String,String])

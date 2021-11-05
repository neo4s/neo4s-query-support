package neo4s.query.auth

import org.neo4j.driver.{AuthToken, AuthTokens}
import scala.collection.JavaConverters._

object Authenticator extends Enumeration {

  protected case class AuthMethod(name: String, authenticate: Map[String,String] => AuthToken) extends super.Val

  import scala.language.implicitConversions
  implicit def valueToAuthMethod(v: Value): AuthMethod = v.asInstanceOf[AuthMethod]

  val Basic: AuthMethod = AuthMethod("basic", credentials => {
    if (credentials.isDefinedAt("realm")) AuthTokens.basic(credentials("username"), credentials("password"), credentials("realm"))
    else AuthTokens.basic(credentials("username"), credentials("password"))
  })

  val Kerberos: AuthMethod = AuthMethod("kerberos", credentials => {
    AuthTokens.kerberos(credentials("ticket"))
  })

  val Custom: AuthMethod = AuthMethod("custom", credentials => {
    val jcreds = credentials.mapValues(_.asInstanceOf[AnyRef]).toMap.asJava
    AuthTokens.custom(credentials("principal"), credentials("credentials"), credentials("realm"), credentials("scheme"), jcreds)
  })

  val None: AuthMethod = AuthMethod("none", _ => {
    AuthTokens.none
  })

}

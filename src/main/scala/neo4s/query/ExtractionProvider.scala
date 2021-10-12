package neo4s.query

import neo4s.query.QueryTools.ExtractionBehavior
import shapeless.labelled.{FieldType, field}
import shapeless.{::, HList, HNil, LabelledGeneric, Lazy, Typeable, Witness}

trait ExtractionProvider[T <: Product] {
  def from[R <: HList](values: Map[String, Any]): Option[T]
}

/** Factory for [[ExtractionProvider]] instances */
object ExtractionProvider {

  trait FromMap[L <: HList] {
    def apply(m: Map[String, Any]): Option[L]
  }

  trait LowPriorityFromMap {
    implicit def hconsFromMap1[K <: Symbol, V, T <: HList](implicit
      witness: Witness.Aux[K],
      typeable: Typeable[V],
      fromMapT: Lazy[FromMap[T]]
    ): FromMap[FieldType[K, V] :: T] = new FromMap[FieldType[K, V] :: T] {
      def apply(m: Map[String, Any]): Option[FieldType[K, V] :: T] = {

        def getOption(m: Map[String, Any], key: String): Option[Any] = {
          if (typeable.describe.take(6) == "Option") {
            if (m.contains(key)) m(key) match {
              case some@Some(_) => Some(some)
              case None => Some(None)
              case v => Some(Option(v))
            } else Some(None)
          } else m.get(key)
        }

        for {
          v <- getOption(m,witness.value.name)
          h <- typeable.cast(v)
          t <- fromMapT.value(m)
        } yield field[K](h) :: t
      }
    }
  }

  object FromMap extends LowPriorityFromMap {
    implicit val hnilFromMap: FromMap[HNil] = new FromMap[HNil] {
      def apply(m: Map[String, Any]): Option[HNil] = Some(HNil)
    }

    implicit def hconsFromMap0[K <: Symbol, V, R <: HList, T <: HList](implicit
      witness: Witness.Aux[K],
      gen: LabelledGeneric.Aux[V, R],
      fromMapH: FromMap[R],
      fromMapT: FromMap[T]
    ): FromMap[FieldType[K, V] :: T] = (m: Map[String, Any]) => {

      for {
        v <- m.get(witness.value.name)
        r <- Typeable[Map[String, Any]].cast(v)
        h <- fromMapH(r)
        t <- fromMapT(m)
      } yield field[K](gen.from(h)) :: t
    }
  }

  private [query] def defaultTypeMapper: PartialFunction[(String,Any),(String,Any)] = {
    case (name,value) => name->value
  }

  def apply[T <: Product](implicit extractionProvider: ExtractionProvider[T]): ExtractionProvider[T] = extractionProvider

  /** Default [[ExtractionProvider]] implementation
   * this is served implicitly whenever an implicit instance of [[ExtractionProvider]] is required but unavailable
   *
   * @param lGen implicit [[LabelledGeneric]] to extract [[HList]] from case class.
   * @tparam T type of element instance
   * @tparam R expected type of HList
   * @return [[ExtractionProvider]] instance
   */
  implicit def makeExtractionProvider[T <: Product, R <: HList](implicit lGen: LabelledGeneric.Aux[T, R], fromMap: FromMap[R], extractionBehavior: ExtractionBehavior = ExtractionBehavior()): ExtractionProvider[T] =
    new ExtractionProvider[T] {
      private def mapType: PartialFunction[(String,Any),(String,Any)] = extractionBehavior.typeMapper orElse defaultTypeMapper

      override def from[R <: HList](values: Map[String, Any]): Option[T] = {
        val mapped = values.map(mapType)
        fromMap(mapped).map(lGen.from)
      }

    }

  def extractorFor[T <: Product](implicit extractionProvider: ExtractionProvider[T]): ExtractionProvider[T] = extractionProvider

}



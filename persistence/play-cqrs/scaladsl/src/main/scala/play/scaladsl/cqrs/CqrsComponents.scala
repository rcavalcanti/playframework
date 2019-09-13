/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.scaladsl.cqrs
import akka.annotation.ApiMayChange
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, EntityContext }
import akka.persistence.typed.ExpectingReply
import akka.persistence.typed.scaladsl.EventSourcedBehavior

import scala.reflect.ClassTag

@ApiMayChange
trait CqrsComponents {

  def clusterSharding: ClusterSharding

  final def createEntityFactory[Command <: ExpectingReply[_]: ClassTag, Event, State](
      name: String,
      behaviorFunc: EntityContext => EventSourcedBehavior[Command, Event, State],
      tagger: Tagger[Event]
  ): EntityFactory[Command, Event, State] =
    new EntityFactory(name, behaviorFunc, tagger, clusterSharding)

  final def createEntityFactory[Command <: ExpectingReply[_]: ClassTag, Event, State](
      name: String,
      entityDef: EntityDef[Command, Event, State]
  ): EntityFactory[Command, Event, State] =
    new EntityFactory(name, entityDef, clusterSharding)

}

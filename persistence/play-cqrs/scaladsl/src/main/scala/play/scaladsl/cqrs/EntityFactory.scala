/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.scaladsl.cqrs

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.journal.Tagged
import akka.persistence.typed.ExpectingReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.scaladsl.ReplyEffect
import scala.reflect.ClassTag
import akka.annotation.ApiMayChange
import akka.cluster.sharding.typed.ShardingEnvelope

@ApiMayChange
class EntityFactory[Command: ClassTag, Event, State](
    name: String,
    behaviorFunc: EntityContext => EventSourcedBehavior[Command, Event, State],
    tagger: Tagger[Event],
    clusterSharding: ClusterSharding
) {

  def this(
    name: String,
    emptyState: State,
    commandHandler: (State, Command) => ReplyEffect[Event, State],
    eventHandler: (State, Event) => State,
    tagger: Tagger[Event],
    clusterSharding: ClusterSharding
  ) = this(
    name,
    ctx => EventSourcedEntity.withEnforcedReplies[Command, Event, State](
      typeKey,
      ctx.entityId,
      emptyState,
      commandHandler,
      eventHandler
    ),
    tagger,
    clusterSharding
  )

  private val typeKey: EntityTypeKey[Command] = EntityTypeKey[Command](name)

  def configureEntity(entity: Entity[Command, ShardingEnvelope[Command]]): Entity[Command, ShardingEnvelope[Command]] =
    entity

  final def entityRefFor(entityId: String): EntityRef[Command] = {
    // this will generate persistence Id compatible with Lagom's Ids, eg: 'ModelName|entityId'
    val persistenceId = typeKey.persistenceIdFrom(entityId)
    clusterSharding.entityRefFor(typeKey, persistenceId.id)
  }

  clusterSharding.init(
    configureEntity(
      Entity(
        typeKey,
        ctx => {
          behaviorFunc(ctx).withTagger(tagger.tagFunction(ctx.entityId))
        }
      )
    )
  )
}

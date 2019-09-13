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

trait EntityDef[Command, Event, State] {
  protected type CommandHandler = (State, Command) => ReplyEffect[Event, State]
  protected type EventHandler = (State, Event) => State

  def emptyState: State
  def commandHandler: CommandHandler
  def eventHandler: EventHandler
  def tagger: Tagger[Event]
}

@ApiMayChange
class EntityFactory[Command <: ExpectingReply[_]: ClassTag, Event, State](
    typeKey: EntityTypeKey[Command],
    behaviorFunc: EntityContext => EventSourcedBehavior[Command, Event, State],
    tagger: Tagger[Event],
    clusterSharding: ClusterSharding
) {

  private def this(
    typeKey: EntityTypeKey[Command],
    emptyState: State,
    commandHandler: (State, Command) => ReplyEffect[Event, State],
    eventHandler: (State, Event) => State,
    tagger: Tagger[Event],
    clusterSharding: ClusterSharding
  ) = this(
    typeKey,
    (ctx: EntityContext) => EventSourcedEntity.withEnforcedReplies[Command, Event, State](
      typeKey,
      ctx.entityId,
      emptyState,
      commandHandler,
      eventHandler
    ),
    tagger,
    clusterSharding
  )

  def this(
    name: String,
    behaviorFunc: EntityContext => EventSourcedBehavior[Command, Event, State],
    tagger: Tagger[Event],
    clusterSharding: ClusterSharding
  ) = this(EntityTypeKey[Command](name), behaviorFunc, tagger, clusterSharding)

  def this(name: String, entityDef: EntityDef[Command, Event, State], clusterSharding: ClusterSharding) =
    this(
      EntityTypeKey[Command](name),
      entityDef.emptyState,
      entityDef.commandHandler,
      entityDef.eventHandler,
      entityDef.tagger,
      clusterSharding
    )

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

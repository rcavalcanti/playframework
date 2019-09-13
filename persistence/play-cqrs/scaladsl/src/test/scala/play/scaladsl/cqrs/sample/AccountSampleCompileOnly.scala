/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.scaladsl.cqrs.sample

import play.scaladsl.cqrs._
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
import play.scaladsl.cqrs.CqrsComponents
import akka.cluster.sharding.typed.ShardingEnvelope

/**
 * That's just an example. Not sure if we need such a wrapping thing,
 * but there are a few things that need to be done correctly and well aligned before using an entity.
 *
 * Here we inject the cluster sharding, wire the behavior, adding a tagger and making it easier to retrieve an instance
 */
abstract class AccountComponent extends CqrsComponents {
  lazy val accountFactory: EntityFactory[AccountCommand, AccountEvent, Account] =
    createEntityFactory("AccountEntity", AccountEntity)
}

/**
 * The current state held by the persistent entity.
 */
case class Account(balance: Double) {

  def canWithdraw(amount: Double): Boolean = {
    balance - amount < 0
  }

  def withDeposit(amount: Double) = {
    copy(balance = balance + amount)
  }

  def withWithdrawal(amount: Double) = {
    copy(balance = balance - amount)
  }

}

object AccountEntity extends EntityDef[AccountCommand, AccountEvent, Account] {

  override val emptyState = Account(balance = 0)

  override val commandHandler = (account, cmd) => cmd match {
    case Deposit(amount, _) =>
      Effect
        .persist(Deposited(amount))
        .thenReply(cmd) { _ =>
          Accepted
        }

    case Withdraw(amount, _) if account.canWithdraw(amount) =>
      Effect.reply(cmd)(Rejected("Insufficient balance!"))

    case Withdraw(amount, _) =>
      Effect
        .persist(Withdrawn(amount))
        .thenReply(cmd) { _ =>
          Accepted
        }
  }

  override val eventHandler = (account, evt) => evt match {
    case Deposited(amount) => account.withDeposit(amount)
    case Withdrawn(amount) => account.withWithdrawal(amount)
  }

  override val tagger = Tagger[AccountEvent].addTagGroup("AccountEvent", 10)

}

sealed trait AccountEvent
case class Deposited(amount: Double) extends AccountEvent
case class Withdrawn(amount: Double) extends AccountEvent

sealed trait AccountReply
case object Accepted                extends AccountReply
case class Rejected(reason: String) extends AccountReply

sealed trait AccountCommand                                          extends ExpectingReply[AccountReply]
case class Deposit(amount: Double, replyTo: ActorRef[AccountReply])  extends AccountCommand
case class Withdraw(amount: Double, replyTo: ActorRef[AccountReply]) extends AccountCommand

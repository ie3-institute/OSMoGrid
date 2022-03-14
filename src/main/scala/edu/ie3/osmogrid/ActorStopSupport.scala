/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import org.slf4j.Logger

/** Support trait for executing clean up tasks with stopping an actor
  *
  * @tparam T
  *   Behavior type
  * @tparam S
  *   StateData type. Optional, use [[Any]] if not applicable
  */
trait ActorStopSupport[T, S] {

  /** Partial function to perform cleanup tasks while shutting down
    */
  protected def cleanUp(stateData: S): Unit

  /** Specific stop state with clean up actions issued
    */
  protected def stopBehavior(stateData: S): Behavior[T] =
    Behaviors.stopped(() => cleanUp(stateData))

  final protected def terminate(log: Logger, stateData: S): Behavior[T] = {
    log.info("Got request to terminate.")
    stopBehavior(stateData)
  }

  final protected def postStopCleanUp(
      log: Logger,
      stateData: S
  ): Behavior[T] = {
    log.info("Got terminated by ActorSystem.")
    stopBehavior(stateData)
  }
}

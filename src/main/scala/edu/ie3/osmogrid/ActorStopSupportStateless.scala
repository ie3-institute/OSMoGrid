/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */
package edu.ie3.osmogrid

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.slf4j.Logger

/** Support trait for executing clean up tasks with stopping an actor
  */
trait ActorStopSupportStateless {

  /** Function to perform cleanup tasks while shutting down
    */
  protected def cleanUp(): Unit

  /** Specific stop state with clean up actions issued
    * @tparam T
    *   Behavior type
    */
  protected def stopBehavior[T]: Behavior[T] = Behaviors.stopped(cleanUp)

  final protected def terminate[T](log: Logger): Behavior[T] = {
    log.info("Got request to terminate.")
    stopBehavior
  }

  final protected def postStopCleanUp[T](log: Logger): Behavior[T] = {
    log.info("Got terminated by ActorSystem.")
    stopBehavior
  }
}

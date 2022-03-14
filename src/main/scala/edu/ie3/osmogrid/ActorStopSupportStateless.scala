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
  * @tparam T
  *   Behavior type
  */
trait ActorStopSupportStateless[T] {

  /** Partial function to perform cleanup tasks while shutting down
    */
  protected val cleanUp: () => Unit

  /** Specific stop state with clean up actions issued
   */
  protected val stopBehavior: Behavior[T] = Behaviors.stopped(cleanUp)

  final protected def terminate(log: Logger): Behavior[T] = {
    log.info("Got request to terminate.")
    stopBehavior
  }

  final protected def postStopCleanUp(log: Logger): Behavior[T] = {
    log.info("Got terminated by ActorSystem.")
    stopBehavior
  }
}

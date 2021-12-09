/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.util

object CollectionUtils {
  implicit class RichList[T](list: List[T]) {

    /** Rotate the elements in the list
      * @param positions
      *   Rotate entries by this amount
      * @return
      *   The rotated list
      */
    def rotate(positions: Int): List[T] = {
      val shift = positions % list.length
      val (head, tail) =
        if (shift >= 0) list.splitAt(shift)
        else list.splitAt(list.length + shift)

      tail.appendedAll(head)
    }
  }
}

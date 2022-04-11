package edu.ie3.osmogrid.exception

case class MissingOsmDataException(
   msg: String = "",
   cause: Throwable = None.orNull
 ) extends Exception(msg, cause)

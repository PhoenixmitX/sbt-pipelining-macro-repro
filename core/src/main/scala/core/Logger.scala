package core

import scala.quoted.*

trait Logger:
  def info(msg: => String): Unit

object Logger:
  def getFor(name: String): Logger = new Logger:
    def info(msg: => String): Unit = println(s"[$name] $msg")

  // core defines a macro (this is what makes the clean build wait for core's full output)
  inline def get: Logger = ${ getMacro }

  private def getMacro(using Quotes): Expr[Logger] =
    import quotes.reflect.*
    '{ getFor(${ Expr(Symbol.spliceOwner.owner.owner.fullName) }) }

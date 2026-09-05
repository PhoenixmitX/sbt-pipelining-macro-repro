package app

import core.Logger as CoreLogger
import scala.quoted.*

// A class in app that extends a trait from core ...
class Logger private (name: String) extends CoreLogger:
  def info(msg: => String): Unit = println(s"[$name] $msg")

// ... and a macro in app whose implementation classes reference it.
// Evaluating the macro loads app.Logger$ / app.Logger, whose supertype core.Logger
// must then be available as a *class file* on the compiler's classpath.
object Logger:
  def getFor(name: String): Logger = new Logger(name)

  inline def get: Logger = ${ getMacro }

  private def getMacro(using Quotes): Expr[Logger] =
    import quotes.reflect.*
    '{ getFor(${ Expr(Symbol.spliceOwner.owner.owner.fullName) }) }

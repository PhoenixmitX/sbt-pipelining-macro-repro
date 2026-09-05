package app

import scala.quoted.*

// A class in app that extends a trait from core ...
class Impl(val name: String) extends core.Base

// ... whose companion constructs it and also defines a macro.
// Evaluating the macro loads app.Impl$ (and, through `create`, app.Impl), whose
// supertype core.Base must then exist as a *class file* on the compiler's classpath.
object Impl:
  def create(name: String): Impl = new Impl(name)

  inline def make: Impl = ${ makeImpl }

  private def makeImpl(using Quotes): Expr[Impl] =
    import quotes.reflect.*
    '{ create(${ Expr(Symbol.spliceOwner.owner.owner.fullName) }) }

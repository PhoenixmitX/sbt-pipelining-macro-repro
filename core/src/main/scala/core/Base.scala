package core

import scala.quoted.*

trait Base:
  def name: String

object Base:
  // core defines a macro: this is what makes the *clean* build compile downstream
  // modules against core's full class output instead of its TASTy-only early jar.
  inline def enclosingName: String = ${ enclosingNameImpl }

  private def enclosingNameImpl(using Quotes): Expr[String] =
    import quotes.reflect.*
    Expr(Symbol.spliceOwner.owner.owner.fullName)

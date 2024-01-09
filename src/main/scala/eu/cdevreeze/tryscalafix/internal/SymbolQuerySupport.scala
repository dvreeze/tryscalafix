/*
 * Copyright 2024-2024 Chris de Vreeze
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.cdevreeze.tryscalafix.internal

import eu.cdevreeze.tryscalafix.internal.QuerySupport.WithQueryMethods
import scalafix.v1._

import scala.meta._

/**
 * Support for querying Symbols of classes on the class path and of their members.
 *
 * @author
 *   Chris de Vreeze
 */
object SymbolQuerySupport {

  // See https://scalameta.org/docs/semanticdb/specification.html for more information
  // Also see the cookbook at https://scalacenter.github.io/scalafix/docs/developers/symbol-information.html

  def isClassSymbol(symbol: Symbol)(implicit doc: SemanticDocument): Boolean =
    symbol.info.exists(info => info.isClass || info.isTrait || info.isInterface)

  def isPublicClassSymbol(symbol: Symbol)(implicit doc: SemanticDocument): Boolean =
    isClassSymbol(symbol) && symbol.info.exists(_.isPublic)

  def isObjectSymbol(symbol: Symbol)(implicit doc: SemanticDocument): Boolean =
    symbol.info.exists(info => info.isObject)

  def isPublicObjectSymbol(symbol: Symbol)(implicit doc: SemanticDocument): Boolean =
    isObjectSymbol(symbol) && symbol.info.exists(_.isPublic)

  def isClassOrObjectSymbol(symbol: Symbol)(implicit doc: SemanticDocument): Boolean =
    isClassSymbol(symbol) || isObjectSymbol(symbol)

  def isPublicClassOrObjectSymbol(symbol: Symbol)(implicit doc: SemanticDocument): Boolean =
    isClassOrObjectSymbol(symbol) && symbol.info.exists(_.isPublic)

  def isMethodSymbol(symbol: Symbol)(implicit doc: SemanticDocument): Boolean =
    symbol.info.exists(info => info.isMethod)

  def isPublicMethodSymbol(symbol: Symbol)(implicit doc: SemanticDocument): Boolean =
    isMethodSymbol(symbol) && symbol.info.exists(_.isPublic)

  // See https://github.com/scalameta/scalameta/issues/467
  def isAbstract(mod: Mod): Boolean = mod match {
    case mod"abstract" => true
    case _             => false
  }

  // See https://github.com/scalameta/scalameta/issues/467
  def isAnnot(mod: Mod, symbolMatcher: SymbolMatcher)(implicit doc: SemanticDocument): Boolean = mod match {
    case mod"@$annot" =>
      annot match {
        case Mod.Annot(Init.After_4_6_0(tpe, _, _)) => symbolMatcher.matches(tpe.symbol)
        case _                                      => false
      }
    case _ => false
  }

  /**
   * Returns true if the parameter Symbol is a Java or Scala method, but not a Scala val or var.
   */
  def isRealMethod(symbol: Symbol)(implicit doc: SemanticDocument): Boolean = {
    symbol.info.exists { info =>
      // isDef means: isMethod && isScala && !isVal && !isVar
      info.isDef || (info.isMethod && info.isJava)
    }
  }

  /**
   * Gets ancestor-or-self symbols of the given class/trait/interface/object symbol.
   */
  def getParentSymbolsOrSelf(symbol: Symbol)(implicit doc: SemanticDocument): Seq[Symbol] = {
    symbol.info match {
      case None => List(symbol)
      case Some(symbolInfo) =>
        symbolInfo.signature match {
          case ClassSignature(_, parents, _, _) =>
            List(symbol).appendedAll {
              parents
                .collect { case TypeRef(_, parentSymbol, _) =>
                  // Recursive call
                  getParentSymbolsOrSelf(parentSymbol)(doc)
                }
                .flatten
                .distinct
            }
          case _ => List(symbol)
        }
    }
  }

  def getOptionalPrimaryConstructor(classSymbol: Symbol)(implicit doc: SemanticDocument): Option[SymbolInformation] = {
    (for {
      classSignature <- classSymbol.info.map(_.signature).toSeq.collect { case signature: ClassSignature => signature }
      decl <- classSignature.declarations
      if decl.isPrimary
    } yield {
      decl
    }).headOption
  }

  def getOptionalPrimaryConstructorAsSignature(
      classSymbol: Symbol
  )(implicit doc: SemanticDocument): Option[MethodSignature] = {
    getOptionalPrimaryConstructor(classSymbol)
      .map(_.signature)
      .collect { case signature: MethodSignature => signature }
  }

  def getDeclaredConstructors(
      classSymbol: Symbol
  )(implicit doc: SemanticDocument): Seq[SymbolInformation] = {
    for {
      classSignature <- classSymbol.info.map(_.signature).toSeq.collect { case signature: ClassSignature => signature }
      decl <- classSignature.declarations
      if decl.isConstructor
    } yield {
      decl
    }
  }

  def getDeclaredConstructorsAsSignatures(
      classSymbol: Symbol
  )(implicit doc: SemanticDocument): Seq[MethodSignature] = {
    getDeclaredConstructors(classSymbol)
      .map(_.signature)
      .collect { case signature: MethodSignature => signature }
  }

  def getDeclaredMethodsOfClass(classSymbol: Symbol)(implicit doc: SemanticDocument): Seq[SymbolInformation] = {
    for {
      classSignature <- classSymbol.info.map(_.signature).toSeq.collect { case signature: ClassSignature => signature }
      decl <- classSignature.declarations
      if decl.isMethod // What about isDef?
    } yield {
      decl
    }
  }

  def getDeclaredMethodsOfClassAsSignatures(
      classSymbol: Symbol
  )(implicit doc: SemanticDocument): Seq[MethodSignature] = {
    getDeclaredMethodsOfClass(classSymbol)
      .map(_.signature)
      .collect { case signature: MethodSignature => signature }
  }

  def getIntroducingStatementOfLocalSymbol(symbol: Symbol)(implicit doc: SemanticDocument): Stat = {
    require(symbol.isLocal, s"Not a local symbol")
    // Term.ParamClause child of Term.Function, Defn.Val etc.
    doc.tree
      .findFirstDescendant[Stat] { (stat: Stat) => stat.symbol.value == symbol.value }
      .getOrElse(sys.error(s"No 'defining' statement found for symbol $symbol"))
  }

}

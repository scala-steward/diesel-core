/*
 * Copyright 2018 The Diesel Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package diesel

import diesel.Bnf.Constraints.Feature
import diesel.Bnf.{Constraints, DslElement, Token}

import scala.collection.mutable

case class CompletionProposal(
  element: Option[DslElement],
  text: String,
  replace: Option[(Int, Int)] = None,
  userData: Option[Any] = None,
  documentation: Option[String] = None,
  predictorPaths: Seq[Seq[DslElement]] = Seq.empty
)

trait CompletionFilter {
  def filterProposals(
    tree: GenericTree,
    offset: Int,
    node: Option[GenericNode],
    proposals: Seq[CompletionProposal]
  ): Seq[CompletionProposal]
}

trait CompletionProvider {
  def getProposals(
    element: Option[DslElement],
    tree: GenericTree,
    offset: Int,
    node: Option[GenericNode]
  ): Seq[CompletionProposal]
}

// TODO CompletionComputeFilter[T]
trait CompletionComputeFilter {
  def beginVisit(): Unit
  def continueVisit(element: DslElement): Boolean
  def endVisit(candidates: Seq[CompletionProposal]): Seq[CompletionProposal]
}

object CompletionConfiguration {
  val defaultDelimiters: Set[Char] = ":(){}.,+-*/[];".toSet

  trait CompletionProposalFactory {
    def createProposal(result: Result, state: State, proto: CompletionProposal): CompletionProposal
  }
}

class CompletionConfiguration {

  private val providers: mutable.Map[DslElement, CompletionProvider] = mutable.Map()
  private var filter: Option[CompletionFilter]                       = None
  private var delimiters: Set[Char]                                  = CompletionConfiguration.defaultDelimiters
  private var includePaths: Boolean                                  = false
  private var computeFilter: Option[CompletionComputeFilter]         = None

  def setProvider(dslElement: DslElement, p: CompletionProvider): Unit = {
    providers(dslElement) = p
  }

  def getProvider(dslElement: DslElement): Option[CompletionProvider] = providers.get(dslElement)

  def setFilter(f: CompletionFilter): Unit = {
    filter = Some(f)
  }

  def getFilter: Option[CompletionFilter] = filter

  def setDelimiters(delimiters: Set[Char]): Unit = {
    this.delimiters = delimiters
  }

  def getDelimiters: Set[Char] = delimiters

  def setIncludePaths(include: Boolean): Unit = {
    this.includePaths = include
  }

  def isIncludePaths: Boolean = this.includePaths

  def setComputeFilter[T](filter: CompletionComputeFilter): Unit = {
    this.computeFilter = Some(filter)
  }

  def getComputeFilter: Option[CompletionComputeFilter] =
    this.computeFilter.map(_.asInstanceOf[CompletionComputeFilter])
}

class CompletionProcessor(
  val result: Result,
  val text: String,
  val navigatorFactory: Result => Navigator,
  val config: Option[CompletionConfiguration] = None
) {

  def computeCompletionProposal(offset: Int): Seq[CompletionProposal] = {

    val delimiters =
      config.map(_.getDelimiters).getOrElse(CompletionConfiguration.defaultDelimiters)

    val c              =
      if (offset >= 1 && offset <= text.length)
        Some(text.charAt(offset - 1))
      else
        None
    val afterDelimiter = c.exists(delimiters.contains)

    def hasProvider(state: State): Boolean =
      state.dot == 0 && hasProviderFor(state.production)

    def hasProviderFor(production: Bnf.Production): Boolean =
      production.getElement
        .flatMap { elem => config.flatMap(_.getProvider(elem)) }.isDefined

    def computeProposalFor(production: Bnf.Production): Boolean = {
      val continueVisit = for {
        element       <- production.element
        c             <- config
        computeFilter <- c.getComputeFilter
      } yield computeFilter.continueVisit(element)
      continueVisit.getOrElse(true)
    }

    def beginCompute(): Unit = {
      config.flatMap(_.getComputeFilter).foreach(_.beginVisit())
    }

    def endCompute(candidates: Seq[CompletionProposal]): Seq[CompletionProposal] = {
      config.flatMap(_.getComputeFilter).map(_.endVisit(candidates)).getOrElse(candidates)
    }

    def findTokenTextForProduction(production: Bnf.Production, dot: Int): CompletionProposal = {
      val text = production.symbols
        .drop(dot)
        .takeWhile(_.isToken)
        .map(_.asInstanceOf[Token])
        .map(_.defaultValue)
        .filterNot(_.isEmpty)
        .mkString(" ")
      CompletionProposal(
        production.getElement,
        text
      )
    }

    def computeAllProposals(
      production: Bnf.Production,
      dot: Int,
      visited: Set[Bnf.NonTerminal],
      stack: Seq[Bnf.NonTerminal],
      from: Int,
      feature: Feature,
      tree: GenericTree,
      offset: Int,
      node: Option[GenericNode]
    ): Seq[CompletionProposal] = {
      if (dot < production.length) {
        production.symbols(dot) match {
          case _: Token       => Seq(findTokenTextForProduction(production, dot))
          case _: Bnf.Axiom   => Seq.empty // not possible
          case rule: Bnf.Rule =>
            if (!visited.contains(rule)) {
              val newVisited = visited + rule
              rule.productions.flatMap { p =>
                val newFeature = feature.merge(from, p.feature)
                if (newFeature != Constraints.Incompatible) {
                  val continueVisit = computeProposalFor(p)
                  if (continueVisit) {
                    val provided =
                      (for {
                        element  <- p.element
                        c        <- config
                        provider <- c.getProvider(element)
                      } yield provider.getProposals(Some(element), tree, offset, node))

                    provided.getOrElse(computeAllProposals(
                      p,
                      0,
                      newVisited,
                      stack :+ rule,
                      from,
                      newFeature,
                      tree,
                      offset,
                      node
                    ))
                  } else Seq.empty
                } else Seq.empty
              }
            } else Seq.empty
        }
      } else Seq.empty
    }

    def isPredictionState(s: State): Boolean = {
      (s.dot == 0 && s.rule.isAxiom) || s.dot > 0
    }

    val navigator = navigatorFactory(result)
    navigator.toIterator
      .toSeq
      .foldLeft(Seq.empty[CompletionProposal]) { case (acc, tree) =>
        var node: Option[GenericNode]          = None
        var defaultReplace: Option[(Int, Int)] = None
        val treeProposals                      = result.chartAndPrefixAtOffset(offset, afterDelimiter)
          .map({ case (chart, prefix) =>
            defaultReplace = prefix.map(p => (offset - p.length, p.length))
            node = tree.root.findNodeAtIndex(chart.index)
            chart.notCompletedStates
              .filterNot(_.kind(result) == StateKind.ErrorRecovery)
              .filter(isPredictionState)
              .flatMap { s =>
                beginCompute();
                val candidates = computeAllProposals(
                  s.production,
                  s.dot,
                  Set.empty,
                  Seq.empty,
                  s.dot,
                  s.feature,
                  tree,
                  offset,
                  node
                )
                endCompute(candidates)
              }
          })
          .getOrElse(Seq.empty)
        acc ++ config
          .flatMap(c => c.getFilter)
          .map(f => f.filterProposals(tree, offset, node, treeProposals))
          .getOrElse(treeProposals)
          .map(proposal => proposal.copy(replace = proposal.replace.orElse(defaultReplace)))
      }
      .distinct
  }

}

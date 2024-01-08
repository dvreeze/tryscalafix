===========================================
Presentation on querying code with Scalafix
===========================================

This project, try-scalafix_, plays with the idea of using Scalafix_ semantic rules
for querying code bases, in order to help understand them and to see how they "hang together".

This presentation is about that: how can Scalafix_ help us understand potentially large code bases
written in Scala (and maybe partly even in Java)?

Purpose of this presentation
============================

Problem: understanding large (Scala/Java) code bases consisting of multiple github repositories
(each one implementing some "service") is hard. It is challenging to:

* understand flows that span multiple services
* have a mental picture of all (synchronous and asynchronous) inputs and outputs of individual services
* grasp which main "application objects" make up a service and how they "hang together"

Obviously, there are many different ways of helping ourselves with this.

Good *documentation* is needed (e.g. in readme file of a service), but cannot be too specific in
implementation details, or else it easily gets out of date.

Looking at the *runtime behaviour* of a service (logs, metrics, instrumentation, runtime service overviews) is
also valuable in understanding services and their interaction, but some of it may easily get forgotten
(in my opinion).

Using tooling or software libraries to *statically* look at source code or compiler output (e.g. Java
class files, or Scala compiler-generated "semanticdb" files) may also be quite helpful. There is plenty
of choice in tooling and libraries to help "query" code bases.

For example, `Java Reflection`_ can get us quite far, both for Scala and Java (the Scala or Java compiler
both emit "class files", after all). But Java reflection cannot peek inside method bodies, for example.

Scalameta_ and SemanticDB_, exposed via Scalafix_, can also get us quite far, with the advantage that
the entire Scala source trees can be queried. (And even for Java we query "class files", but like is the case
for the Java reflection API introspection ends at method signatures.) This combination of Scalameta_,
SemanticDB_ and Scalafix_ is what is presented here.

Scalafix, Scalameta and SemanticDB
==================================

Let's first briefly recap Scalameta_, SemanticDB_ and Scalafix_. The good news is that this stack is
quite accessible to Scala programmers, requiring no knowledge about compiler internals.

With Scalameta_ we can obtain *syntax trees* parsed from Scala source code. See the `Tree Guide`_ for
an explanation of Scalameta trees. These syntax trees can be queried for tree nodes that we might be
interested in.

Syntax trees are similar to the output of the first phase of the Scala compiler. They show the syntactic
structure of some source code, but they contain *no semantics* yet. In particular, by themselves these
trees do not help us to resolve references to classes on the one hand to the corresponding class definitions
on the other hand. Analogous remarks apply to methods, fields etc.

So, for any useful queries on syntax trees, we need those trees to be annotated with semantics.

That's where SemanticDB_ comes in. Unlike the Scalameta Trees API mentioned above, it's not a Scala API
that is currently practical to use, though.

It's foremost a *specification* (for Scala and Java) and "semanticdb" file format. The latter is emitted
by the Scala compiler with the help of the *semanticdb-scalac* compiler plugin. Build tools (such as sbt_
and Maven_) make generation of "semanticdb" files relatively easy.

What these "semanticdb" files offer are *symbols* and *types*, among other things. Symbols link references
(for methods, classes etc.) to definitions.

Besides the Scalameta_ Scala API and compiler-generated "semanticdb" files, we need a Scala API to access
the semantical information contained in the "semanticdb" files. That's where Scalafix_ comes in.

Scalafix_ is meant to be used for linting and automatic code rewriting, but it also contains the SemanticDB
data model in its `Scalafix Scala API`_, which makes it useful even beyond linting and automatic code rewriting.

That's what we are using here. Scalafix *semantic rules* have (at least) the following structure:

.. code-block:: scala

    class MyRule extends SemanticRule("MyRule") {
      override def fix(implicit doc: SemanticDocument): Patch = {
        ???
      }
    }

The implicit *SemanticDocument* is also a "symbol table", which helps resolve symbols for (compiled)
Java/Scala code anywhere on the class path.

Given an implicit "symbol table" (so typically, *SemanticDocument*), and given a Scalameta_ Tree node,
we can get the tree node's *Symbol*, the symbol's *SymbolInformation* and the symbol information's
*Signature*:

.. code-block:: scala

    // tree.symbol is obtained using an implicit SemanticDocument (or any Symtab)
    // tree.symbol.info is an optional SymbolInformation, but it is always non-empty
    // tree.symbol.info.get.signature is typically a class/method/value/type signature

    tree.symbol.info.get.signature

For any other Java/Scala *Symbol* on the class path, we can also obtain *SymbolInformation* and its
*Signature*:

.. code-block:: scala

    // Symbols (Java or Scala) on the class path, obtained indirectly (from signatures, typically)

    symbol.info.get.signature

See `Scalafix Scala API`_.

But what do SemanticDB *symbols* look like exactly, as strings? For that (in the case of Scala), see
`Scala symbols`_.

Using Scalafix for querying code bases
======================================

Like mentioned above, "querying code bases" is in a way "abusing" the Scalafix library, but this way
we can query "semantic trees" representing parsed Scala code. Let's say we want to find all Scalatra
servlets in a Scala code base, and obtain mappings of HTTP methods (handled by the servlet) to URI paths.
This is certainly useful in terms of helping understand code bases. A template for such a Scalafix "rule"
could be as follows:

.. code-block:: scala

    class MyRule extends SemanticRule("MyRule") {

      private var results: Seq[Json] = Seq.empty

      override def beforeStart(): Unit = {
        ???
      }

      override def afterComplete(): Unit = {
        // Print results, e.g. to System.out
        ???
      }

      override def fix(implicit doc: SemanticDocument): Patch = {
        // Find Scalatra servlets in doc; if any found, add them to results
        // ...
        Patch.empty // no linting or refactoring Patch
      }
    }

This template is followed in for example ScalatraServletFindingRule_. Have a closer look at it.
Output could look like this:

.. code-block:: json

    {
      "scalatraServlets": [
        [
          {
            "fileName": "OrderServlet.scala",
            "classSymbol": "com/test/order/OrderServlet#",
            "httpFunctionCalls": [
              {
                "termClassName": "TermApplyImpl",
                "symbol": "org/scalatra/ScalatraBase#get().",
                "uriPathOption": "/:orderId"
              },
              {
                "termClassName": "TermApplyImpl",
                "symbol": "org/scalatra/ScalatraBase#post().",
                "uriPathOption": "/:orderId"
              }
            ]
          }
        ]
      ]
    }

So the output shows Scalatra servlets, their HTTP-related operations, and the corresponding URI paths.
This is an example of how Scalafix can help us understand code bases. Besides Scalatra servlets, we
could search for Kafka consumers (and related event types), Kafka producers (and related event types),
and much much more. This is exactly what I would like to achieve with these "rules".

Other examples are KafkaEventProcessorFindingRule_ and (more general in scope) ClassSearchingRule_.

But wait, how do we even know how to write those rules and correctly pattern match on the right trees?
For that we have rule TreeAndSymbolDisplayingRule_. It has been made easier to use (in Maven Scala projects)
by "bootstrapping" program ShowTreeAndSymbols_, which outputs pretty-printed Scalameta_ trees and symbols (linked
to those trees and their positions in the source code), among other things. Looking at that output for a
source document helps us implement "querying rules".

What about inspecting Java code in mixed Scala/Java projects using Scalafix? For those Java classes we
do have the *Symbol* (can be created from the known String representation), *SymbolInformation* and
*Signature*. So we can query those data structures, which, like Java reflection, stops at method signatures.

A template for such rules could look like this:

.. code-block:: scala

    class MyJavaRule extends SemanticRule("MyJavaRule") {

      private var result: Json = null

      override def beforeStart(): Unit = {
        ???
      }

      override def afterComplete(): Unit = {
        // Print result, e.g. to System.out
        ???
      }

      override def fix(implicit doc: SemanticDocument): Patch = {
        if (result == null) {
          // Do some class path scanning for the relevant directory, say, the target/classes directory
          // Filter on Java symbols, excluding the Scala symbols (if that's what is wanted)
          // Process those Java symbols, and set the result accordingly
          ???
        }
        // If the result has been filled (after the first "fix" call), this is a no-op
        Patch.empty // no linting or refactoring Patch
      }
    }

But then, how do we "bootstrap" those rules, and set up configuration? For linting and refactoring,
Scalafix could be fed with just one ".scalafix.conf" file, for all relevant rules.

For our purposes it is handy to have different ".scalafix-XYZ.conf" files, each one mentioning only 1
rule and containing configuration only pertaining to that single rule (or even to just one use case of that rule).

So how do we get this all to work? The following steps are needed during development:

* Implement the Scalafix_ semantic rule, obviously
* Mention the rule in "META-INF/services/scalafix.v1.Rule" (which lands in the same JAR as the rule code)
* Create a "template" configuration file for this rule (landing in the same JAR)
* Deploy the JAR with the compiled rules and their direct dependencies (locally, or to Maven Central)

Having this "rule(s) JAR file", it can be used on a Scala(/Java) project:

* Have the Scala compiler emit "semanticdb" files (or else semantic Scalafix rules will not work)
* Create one or more config files for the rule(s) we would like to run
* Run a rule, taking the appropriate Scalafix config file

In a Maven Scala(/Java) project, the `following XML snippet`_ can help for "semanticdb" compilation as well
as running rules. It contains Maven profile "semanticdb", which does not interfere with the normal build if
this Maven profile is not explicitly activated.

To make running a rule in a Maven project more concrete, assume that we have a POM file "pom-semanticdb.xml"
generated from "pom.xml" using program EnhancePom_, containing the XML snippet just mentioned.

Then running a rule could be done as follows:

.. code-block:: bash

    mvn scalafix:scalafix -Dscalafix.config=.scalafix-ScalatraServletFindingRule.conf \
      -Psemanticdb -f pom-semanticdb.xml

It's not very hard to automate this (including "semanticdb" compilation), across multiple projects, by
a push of a button (like in similar experiments using Java reflection).

Conclusion
==========

Previous experiments with Scalafix_ for querying code bases worked with "standalone rules" that were not
very practical (in that they had to be single-source, without any dependencies outside Scalafix etc.).
Also, I did not exploit rule lifecycle methods in those attempts.

This time I can really claim that it's practical to use Scalafix_ to write "rules" that query Scala/Java code
bases to help us understand them more quickly. Moreover, any documentation generated from such Scalafix_
rules can easily be kept up-to-date.

.. _`try-scalafix`: https://github.com/dvreeze/tryscalafix
.. _`Scalafix`: https://scalacenter.github.io/scalafix
.. _`Java Reflection`: https://www.oracle.com/technical-resources/articles/java/javareflection.html
.. _`Scalameta`: https://scalameta.org
.. _`SemanticDB`: https://scalameta.org/docs/semanticdb/guide.html
.. _`Tree Guide`: https://scalameta.org/docs/trees/guide.html
.. _sbt: https://www.scala-sbt.org/
.. _Maven: https://maven.apache.org/
.. _`Scalafix Scala API`: https://scalacenter.github.io/scalafix/docs/developers/api.html
.. _`Scala symbols`: https://scalameta.org/docs/semanticdb/specification.html#symbol-1
.. _ScalatraServletFindingRule: https://github.com/dvreeze/tryscalafix/blob/master/src/main/scala/eu/cdevreeze/tryscalafix/rule/adhoc/ScalatraServletFindingRule.scala
.. _KafkaEventProcessorFindingRule: https://github.com/dvreeze/tryscalafix/blob/master/src/main/scala/eu/cdevreeze/tryscalafix/rule/adhoc/KafkaEventProcessorFindingRule.scala
.. _ClassSearchingRule: https://github.com/dvreeze/tryscalafix/blob/master/src/main/scala/eu/cdevreeze/tryscalafix/rule/ClassSearchingRule.scala
.. _TreeAndSymbolDisplayingRule: https://github.com/dvreeze/tryscalafix/blob/master/src/main/scala/eu/cdevreeze/tryscalafix/rule/TreeAndSymbolDisplayingRule.scala
.. _ShowTreeAndSymbols: https://github.com/dvreeze/tryscalafix/blob/master/src/main/scala/eu/cdevreeze/tryscalafix/console/ShowTreeAndSymbols.scala
.. _`following XML snippet`: https://github.com/dvreeze/tryscalafix/blob/master/src/main/resources/maven-pom-scalafix-sample.xml
.. _EnhancePom: https://github.com/dvreeze/tryscalafix/blob/master/src/main/scala/eu/cdevreeze/tryscalafix/console/EnhancePom.scala

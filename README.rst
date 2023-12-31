============
Try-Scalafix
============

This project toys with the idea of using Scalafix_ for generating "overviews" of large code bases,
to aid understanding of those code bases.

The project contains Scalafix semantic rules for this purpose, and therefore depends on Scalameta_ and SemanticDB_.

Typically this project is used with specific ".scalafix.<rule>.<use-case>.conf" files, each one referring to
only 1 rule.

In Scala Maven projects:

* Run program *EnhancePom* to generate a "pom-semanticdb.xml" enhanced file copy of the POM file, containing Maven profile "semanticdb"
* Generate ".semanticdb" files
* Run the chosen rule

Compiling into ".semanticdb" files:

.. code-block:: bash

    mvn clean compile -Psemanticdb -f pom-semanticdb.xml

Running the rule:

.. code-block:: bash

    mvn scalafix:scalafix -Dscalafix.config=.scalafix.<XYZ>.conf -Psemanticdb -f pom-semanticdb.xml

If rule ShowTreeAndSymbols should run on one source file, and the chosen config file has this content:

.. code-block::

    rules = [
      TreeAndSymbolDisplayingRule
    ]

then we could do so (roughly) as follows:

.. code-block:: bash

    mvn scalafix:scalafix -Dscalafix.config=.scalafix-TreeAndSymbolDisplayingRule.conf \
      -Dscalafix.command.line.args="--files=src/main/scala/com/test/MyClass.scala" \
      -Psemanticdb -f pom-semanticdb.xml

.. _`Scalafix`: https://scalacenter.github.io/scalafix/docs/users/installation.html
.. _`Scalameta`: https://scalameta.org
.. _`SemanticDB`: https://scalameta.org/docs/semanticdb/guide.html

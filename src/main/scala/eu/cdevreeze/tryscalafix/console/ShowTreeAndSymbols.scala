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

package eu.cdevreeze.tryscalafix.console

import eu.cdevreeze.tryscalafix.rule.TreeAndSymbolDisplayingRule

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import scala.util.Using
import scala.util.chaining.scalaUtilChainingOps

/**
 * Program that invokes rule TreeAndSymbolDisplayingRule for one class, using Maven.
 *
 * The program has one argument, containing the path of a Scala source file, relative to the source root directory
 * (typically "src/main/scala", but changeable by system property "sourceDirectory"). It's best to avoid paths
 * containing spaces in order for this program to work (otherwise using a link to the file might help).
 *
 * The program depends on the existence of a POM file (say, pom-semanticdb.xml) which contains a Maven profile
 * "semanticdb", for Scala compilation emitting semanticdb files, and for running the rule.
 *
 * It might be handy to run this program from IntelliJ, setting the working directory to the root directory of the Scala
 * project where the source to inspect resides (as well as the POM file mentioned above). With system property
 * "mvnCommand" it is easy to change the Maven command to "./mvnw", if needed.
 *
 * @author
 *   Chris de Vreeze
 */
object ShowTreeAndSymbols {

  private val defaultMvnCommand = "mvn"
  private val mvnCommand: String = System.getProperty("mvnCommand", defaultMvnCommand)

  // The POM file contains Maven profile "semanticdb", for "semanticdb" compilation and running the semantic rule
  private val defaultPomFile = "pom-semanticdb.xml"
  private val pomFile: String = System.getProperty("pomFile", defaultPomFile)

  private val defaultSourceDirectory: Path = Path.of("src/main/scala").ensuring(!_.isAbsolute)

  private val sourceDirectory: Path =
    System.getProperty("sourceDirectory", defaultSourceDirectory.toString).pipe(p => Path.of(p))

  private val defaultMvnCommandTemplate: String = {
    Seq(
      s"""$mvnCommand scalafix:scalafix -Dscalafix.config=CFG""",
      s"""-Dscalafix.command.line.args=--files=SCALA-SOURCE-RELATIVE-PATH""",
      s"""-Psemanticdb -f $pomFile"""
    ).mkString(" ")
  }

  private val mvnCommandTemplate: String = System.getProperty("mvnCommandTemplate", defaultMvnCommandTemplate)

  def main(args: Array[String]): Unit = {
    require(args.lengthIs == 1, "Usage: ShowTreeAndSymbols <source-file-relative-path>")
    val sourceFileRelativeToSourceDir =
      Path.of(args(0)).ensuring(!_.isAbsolute, s"Expected source file as relative file path")
    val sourceFileRelativeToCurrentDir = sourceDirectory.resolve(sourceFileRelativeToSourceDir)

    val tempConfigFile: Path = Files.createTempFile(".scalafix-", ".conf")

    Using.resource(
      classOf[TreeAndSymbolDisplayingRule].getResourceAsStream("/scalafix-TreeAndSymbolDisplayingRule.conf")
    ) { is =>
      Files.copy(is, tempConfigFile, StandardCopyOption.REPLACE_EXISTING)
    }

    val mvnCommandTemplateAsStringSeq: Seq[String] =
      mvnCommandTemplate.split(' ').toSeq.map(_.trim).filter(_.nonEmpty)

    val mvnCommandAsStringSeq: Seq[String] =
      mvnCommandTemplateAsStringSeq.map { str =>
        str
          .replace("CFG", tempConfigFile.toString)
          .replace("SCALA-SOURCE-RELATIVE-PATH", sourceFileRelativeToCurrentDir.toString)
      }

    // See https://docs.oracle.com/javase/9/tools/java.htm#JSWOR-GUID-4856361B-8BFD-4964-AE84-121F5F6CF111

    println(s"Maven command: ${mvnCommandAsStringSeq.mkString(" ")}")

    // See https://www.baeldung.com/java-lang-processbuilder-api
    val processBuilder: ProcessBuilder = new ProcessBuilder(mvnCommandAsStringSeq: _*)

    val process: Process = processBuilder.start()
    val processHandle: ProcessHandle = process.toHandle
    val processInfo: ProcessHandle.Info = processHandle.info()

    println("PID: " + processHandle.pid())
    println("Alive: " + processHandle.isAlive)
    println("Info: " + processInfo)

    Using.resource(process.getInputStream) { is =>
      is.transferTo(System.out)
    }

    val exitValue = process.waitFor()
    require(exitValue == 0, s"Expected exit value 0, but got $exitValue")
  }

}

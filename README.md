# XSL-FO Toolkit for IntelliJ IDEA [![Build Status](https://github.com/moritzfl/intellij-xslfo-support/actions/workflows/gradle.yml/badge.svg)](https://github.com/moritzfl/intellij-xslfo-support/actions/workflows/gradle.yml)

This plugin provides run configuration that allows execution of XSL-FO transformations with [Apache FOP](https://xmlgraphics.apache.org/fop/) (Formatting Objects Processor).

## Building the plugin

Simply run the runIde task using the gradle wrapper:

`./gradlew runIde`

## Using the plugin:

The plugin can be used either with the bundled fop library or an external binary.
It allows the creation of run configurations that you can execute to produce output in different formats.

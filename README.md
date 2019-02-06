# Invariants Plugin for Gradle
This is a plugin for Gradle that will run static code analysis to create a call graph and identify all tests
that will run a given method. The plugin will then be able to run the Daikon invariant extractor to create infariants for the methods
by running the selected tests.
Finally the plugin will be able to create per method files with all invariants detected.

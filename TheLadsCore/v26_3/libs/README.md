# Retained native-gallery configuration libraries

These two files are extracted unchanged from the production-pinned MIT
Screenshot Viewer1.3.6-fabric-mc26.2 jar. They are explicit build inputs because
that exact CatConfig-MC Minecraft26.2 coordinate is not in Maven Central.

- catconfig-mc-26.2-0.2.1.jar is nested in the release core, and itself contains
  META-INF/jars/catconfig-0.3.0.jar as declared by its original Fabric metadata.
- catconfig-0.3.0.jar is an extracted compile-classpath input; it is not nested a
  second time by Lads.
- Licenses are in src/main/resources/assets/theladscore/licenses/CatConfig-LICENSE.txt
  and CatConfig-MC-LICENSE.txt.
- Complete provenance and hashes: docs/NATIVE_SCREENSHOTS_26_2.md at repository root.

The gallery implementation is rebuilt from native Lads source; these libraries
provide configuration serialization and editor controls only.

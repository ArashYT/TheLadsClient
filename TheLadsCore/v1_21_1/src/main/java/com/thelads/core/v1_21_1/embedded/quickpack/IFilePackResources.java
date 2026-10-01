// Derived from quick-pack 1.4.0 by Drex (commit b80dac1, MIT); see META-INF/lads-sources/quickpack/LICENSE.
package com.thelads.core.v1_21_1.embedded.quickpack;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public interface IFilePackResources {
    void quick_pack$initializeFileTree(TreeSet<String> fileTree, Map<String, Set<String>> namespaces);
}

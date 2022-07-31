package tel.panfilov.maven.plugins.reposync;


import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

import java.util.HashSet;
import java.util.Set;

class CollectAllDependenciesVisitor implements DependencyVisitor {

    private boolean root = true;
    private Set<Artifact> artifacts = new HashSet<>();

    @Override
    public boolean visitEnter(DependencyNode node) {
        if (root) {
            root = false;
            return true;
        }
        return artifacts.add(node.getArtifact());
    }

    @Override
    public boolean visitLeave(DependencyNode node) {
        return true;
    }

    public Set<Artifact> getArtifacts() {
        return artifacts;
    }
}

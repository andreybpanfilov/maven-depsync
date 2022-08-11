package tel.panfilov.maven.plugins.reposync.selector;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;

public class OptionalDependencySelector implements DependencySelector {

    private final int depth;

    public OptionalDependencySelector() {
        this(0);
    }

    public OptionalDependencySelector(int depth) {
        this.depth = depth;
    }

    public boolean selectDependency(Dependency dependency) {
        return depth < 2 || !dependency.isOptional();
    }

    public DependencySelector deriveChildSelector(DependencyCollectionContext context) {
        if (depth > 1) {
            return this;
        }
        return new OptionalDependencySelector(depth + 1);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (null == obj || !getClass().equals(obj.getClass())) {
            return false;
        }
        OptionalDependencySelector that = (OptionalDependencySelector) obj;
        return depth == that.depth;
    }

    @Override
    public int hashCode() {
        int hash = getClass().hashCode();
        hash = hash * 31 + depth;
        return hash;
    }

}
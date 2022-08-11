package tel.panfilov.maven.plugins.reposync.selector;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class ExclusionDependencySelector implements DependencySelector {

    private final Set<Exclusion> exclusions = new TreeSet<>(
            Comparator.comparing(Exclusion::getArtifactId)
                    .thenComparing(Exclusion::getGroupId)
                    .thenComparing(Exclusion::getExtension)
                    .thenComparing(Exclusion::getClassifier)
    );

    private final int hashCode;


    public ExclusionDependencySelector() {
        this.hashCode = this.exclusions.hashCode();
    }


    public ExclusionDependencySelector(Collection<Exclusion> exclusions) {
        if (exclusions != null && !exclusions.isEmpty()) {
            this.exclusions.addAll(exclusions);
        }
        this.hashCode = this.exclusions.hashCode();
    }

    private ExclusionDependencySelector(Exclusion[] exclusions) {
        if (exclusions != null) {
            Collections.addAll(this.exclusions, exclusions);
        }
        this.hashCode = this.exclusions.hashCode();
    }

    public boolean selectDependency(Dependency dependency) {
        Artifact artifact = dependency.getArtifact();
        for (Exclusion exclusion : exclusions) {
            if (matches(exclusion, artifact)) {
                return false;
            }
        }
        return true;
    }

    private boolean matches(Exclusion exclusion, Artifact artifact) {
        if (!matches(exclusion.getArtifactId(), artifact.getArtifactId())) {
            return false;
        }
        if (!matches(exclusion.getGroupId(), artifact.getGroupId())) {
            return false;
        }
        if (!matches(exclusion.getExtension(), artifact.getExtension())) {
            return false;
        }
        return matches(exclusion.getClassifier(), artifact.getClassifier());
    }

    private boolean matches(String pattern, String value) {
        return "*".equals(pattern) || pattern.equals(value);
    }

    public DependencySelector deriveChildSelector(DependencyCollectionContext context) {
        Dependency dependency = context.getDependency();
        Collection<Exclusion> exclusions = (dependency != null) ? dependency.getExclusions() : Collections.emptyList();
        exclusions = exclusions.stream()
                .filter(e -> !this.exclusions.contains(e))
                .collect(Collectors.toCollection(ArrayList::new));
        if (exclusions.isEmpty()) {
            return this;
        }
        exclusions.addAll(this.exclusions);
        return new ExclusionDependencySelector(exclusions);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (null == obj || !getClass().equals(obj.getClass())) {
            return false;
        }
        ExclusionDependencySelector that = (ExclusionDependencySelector) obj;
        return exclusions.equals(that.exclusions);
    }

    @Override
    public int hashCode() {
        return this.hashCode;
    }

}

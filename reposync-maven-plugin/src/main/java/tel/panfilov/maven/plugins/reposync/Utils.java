package tel.panfilov.maven.plugins.reposync;

import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.model.Parent;
import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class Utils {

    private Utils() {
        super();
    }


    static void checkResult(ArtifactResult result, Predicate<Exception> ignore) throws MojoExecutionException {
        List<Exception> exceptions = result.getExceptions();
        if (exceptions == null || exceptions.isEmpty()) {
            return;
        }
        for (Exception exception : exceptions) {
            if (!ignore.test(exception)) {
                throw new MojoExecutionException("Failed to resolve artifact " + result.getRequest().getArtifact(), exceptions.get(0));
            }
        }
    }

    static void checkResult(ArtifactDescriptorResult result, Predicate<Exception> ignore) throws MojoExecutionException {
        List<Exception> exceptions = result.getExceptions();
        if (exceptions == null || exceptions.isEmpty()) {
            return;
        }
        for (Exception exception : exceptions) {
            if (!ignore.test(exception)) {
                throw new MojoExecutionException("Failed to load descriptor " + result.getRequest().getArtifact(), exceptions.get(0));
            }
        }
    }

    static ArtifactRepositoryPolicy getUpdateAlwaysPolicy() {
        return new ArtifactRepositoryPolicy(true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS,
                ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN);
    }

    static List<Artifact> extractArtifacts(CollectResult collectResult) {
        CollectAllDependenciesVisitor visitor = new CollectAllDependenciesVisitor();
        collectResult.getRoot().accept(visitor);
        List<Artifact> artifacts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Artifact artifact : visitor.getArtifacts()) {
            if (seen.add(getId(artifact))) {
                artifacts.add(artifact);
            }
        }
        Artifact rootArtifact = collectResult.getRoot().getArtifact();
        if (seen.add(getId(rootArtifact))) {
            artifacts.add(rootArtifact);
        }
        return artifacts;
    }

    static ArtifactRequest artifactRequest(Artifact aetherArtifact, List<RemoteRepository> remoteRepositories) {
        return new ArtifactRequest(aetherArtifact, remoteRepositories, null);
    }

    static List<ArtifactRequest> artifactRequests(List<Artifact> aetherArtifacts, List<RemoteRepository> remoteRepositories) {
        List<ArtifactRequest> result = new ArrayList<>();
        for (Artifact artifact : aetherArtifacts) {
            result.add(artifactRequest(artifact, remoteRepositories));
        }
        return result;
    }

    static Artifact getPomArtifact(Parent parent) {
        return new DefaultArtifact(
                parent.getGroupId(),
                parent.getArtifactId(),
                "",
                "pom",
                parent.getVersion()
        );
    }

    static Artifact toSourcesArtifact(Artifact artifact) {
        return new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "sources", "jar", artifact.getVersion());
    }

    static Artifact toJavadocArtifact(Artifact artifact) {
        return new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "javadoc", "jar", artifact.getVersion());
    }

    static Artifact toClassesArtifact(Artifact artifact) {
        return new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "classes", "jar", artifact.getVersion());
    }

    static boolean isPom(Artifact artifact) {
        return "pom".equals(artifact.getExtension());
    }

    static boolean isWar(Artifact artifact) {
        return "war".equals(artifact.getExtension());
    }

    static boolean hasClassifier(Artifact artifact) {
        return artifact.getClassifier().length() > 0;
    }

    static String getId(Artifact artifact) {
        return artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + artifact.getClassifier() + ':' + artifact.getVersion() + ':' + artifact.getExtension();
    }

}

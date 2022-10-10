package tel.panfilov.maven.plugins.reposync.component;

import org.apache.maven.plugin.logging.Log;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

public class LocalRepositoryVisitor extends SimpleFileVisitor<Path> {

    private static final String ARTIFACT_NAME_REGEXP_TMPL = "^%s-%s(?:-(.+)){0,1}(?:.(\\b\\w+\\b))$";
    private static final String POM_FILE_EXTENSION = "pom";

    private static final String ARTIFACT_ILLEGAL_CHARS_REGEXP = ".*[${].*";

    private final Log log;

    private final Path repositoryDir;

    private final boolean failOnBadArtifact;

    private final Set<Artifact> resolvedArtifacts = new HashSet<>();

    public LocalRepositoryVisitor(Path repositoryDir, boolean failOnBadArtifact, Log log) {
        this.repositoryDir = repositoryDir;
        this.failOnBadArtifact = failOnBadArtifact;
        this.log = log;
        requireNonNull(this.repositoryDir, "repositoryDir must be not null");
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        tryResolveArtifact(file)
                .ifPresent(resolvedArtifacts::add);
        return super.visitFile(file, attrs);
    }

    private Optional<Artifact> tryResolveArtifact(Path absolutePath) throws IOException {
        try {
            Path repoPath = repositoryDir.relativize(absolutePath);
            if (repoPath.getNameCount() < 3) {
                return Optional.empty();
            }
            String artifactId = getArtifactId(repoPath);
            String version = getVersion(repoPath);
            String fileName = repoPath.getFileName().toString();
            validateNoIllegalChars(fileName, "fileName", absolutePath);
            validateNoIllegalChars(artifactId, "artifactId", absolutePath);
            validateNoIllegalChars(version, "version", absolutePath);

            String regExp = String.format(ARTIFACT_NAME_REGEXP_TMPL, artifactId, version);
            Pattern pattern = Pattern.compile(regExp);
            Matcher matcher = pattern.matcher(fileName);
            if (matcher.find()) {
                String groupId = getGroupId(repoPath);
                String extension = matcher.group(2);
                String classifier = POM_FILE_EXTENSION.equals(extension) || matcher.group(1) == null ? "" : matcher.group(1);
                validateNoIllegalChars(groupId, "groupId", absolutePath);
                validateNoIllegalChars(classifier, "classifier", absolutePath);
                validateNoIllegalChars(extension, "extension", absolutePath);

                return Optional.of(new DefaultArtifact(
                        groupId,
                        artifactId,
                        classifier,
                        extension,
                        version
                ));
            }
        } catch (Exception e) {
            if (failOnBadArtifact) {
                throw e;
            } else {
                log.warn(e);
            }
        }

        return Optional.empty();
    }

    private void validateNoIllegalChars(String property, String propertyName, Path absolutePath) throws IOException {
        if (property.matches(ARTIFACT_ILLEGAL_CHARS_REGEXP)) {
            throw new IOException("Artifact [" + absolutePath.toAbsolutePath() + "]" +
                    " has illegal characters in '" + propertyName + "' property value: '" + property + "'.");
        }
    }

    private String getArtifactId(Path pomRepoPath) {
        return getArtifactDir(pomRepoPath).getFileName().toString();
    }

    private Path getArtifactDir(Path pomRepoPath) {
        return pomRepoPath.getParent().getParent();
    }

    private String getVersion(Path pomRepoPath) {
        return getVersionDir(pomRepoPath).getFileName().toString();
    }

    private Path getVersionDir(Path pomRepoPath) {
        return pomRepoPath.getParent();
    }

    private String getGroupId(Path pomRepoPath) {
        Path artifactDir = getArtifactDir(pomRepoPath);
        int artifactIndex = artifactDir.getNameCount() - 1;
        Path groupRepoPath = pomRepoPath.subpath(0, artifactIndex);
        StringBuilder groupId = new StringBuilder();
        for (int i = 0; i < groupRepoPath.getNameCount(); i++) {
            groupId.append(groupRepoPath.getName(i));
            if (i != groupRepoPath.getNameCount() - 1) {
                groupId.append(".");
            }
        }
        return groupId.toString();
    }

    public Set<Artifact> getResolvedArtifacts() {
        return resolvedArtifacts;
    }
}
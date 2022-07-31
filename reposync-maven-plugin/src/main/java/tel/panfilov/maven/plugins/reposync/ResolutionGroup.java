package tel.panfilov.maven.plugins.reposync;

import org.eclipse.aether.repository.RemoteRepository;

import java.util.ArrayList;
import java.util.List;

class ResolutionGroup {

    final RemoteRepository repository;

    final List<ResolutionItem> items = new ArrayList<>();

    ResolutionGroup(RemoteRepository repository) {
        this.repository = repository;
    }

    boolean matches(RemoteRepository repo) {
        return repository.getUrl().equals(repo.getUrl())
                && repository.getContentType().equals(repo.getContentType())
                && repository.isRepositoryManager() == repo.isRepositoryManager();
    }

}

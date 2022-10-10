## Synchronises maven artifacts across repositories

#### reposync:single

Synchronises single artifact, available parameters:
```
artifact
  The artifact - a string of the form
  groupId:artifactId:version[:packaging[:classifier]].
  User property: artifact

artifactId
  The artifactId of the artifact to sync. Ignored if artifact is used.
  User property: artifactId

classifier
  The classifier of the artifact to sync. Ignored if artifact is used.
  User property: classifier

dryRun (Default: false)
  Option can be used to obtain a summary of what will be transferred
  User property: dryRun

groupId
  The groupId of the artifact to sync. Ignored if artifact is used.
  User property: groupId

packaging (Default: jar)
  The packaging of the artifact to sync. Ignored if artifact is used.
  User property: packaging

scope (Default: compile)
  Scope threshold to include
  User property: scope

sourceRepositories
  Repositories in the format id::[layout]::url or just url, separated by
  comma. ie.
  central::default::https://repo.maven.apache.org/maven2,myrepo::::https://repo.acme.com,https://repo.acme2.com
  User property: sourceRepositories

syncJavadoc (Default: false)
  Synchronize javadocs
  User property: syncJavadoc

syncSources (Default: false)
  Synchronize sources
  User property: syncSources

targetRepository
  Repository in the format id::[layout]::url or just url
  myrepo::::https://repo.acme.com|https://repo.acme2.com
  Required: Yes
  User property: targetRepository

transitive (Default: true)
  Synchronize transitively, retrieving the specified artifact and all of its
  dependencies.
  User property: transitive

useSettingsRepositories (Default: false)
  Load information about source repositories from settings.xml file
  User property: useSettingsRepositories

version
  The version of the artifact to sync. Ignored if artifact is used.
  User property: version
```

Example:

```shell
% mvn tel.panfilov.maven:reposync-maven-plugin:0.2.0:single \
 -Dartifact=org.springframework:spring-tx:5.3.22 \
 -DsourceRepositories=central::default::https://repo.maven.apache.org/maven2 \
 -DtargetRepository=local::::http://localhost:8081/repository/maven-releases \
 -Dtransitive=true \
 -DdryRun=true \
 -DsyncSources=true \
 -DsyncJavadoc=true
[INFO] Scanning for projects...
[INFO] 
[INFO] ------------------< org.apache.maven:standalone-pom >-------------------
[INFO] Building Maven Stub Project (No POM) 1
[INFO] --------------------------------[ pom ]---------------------------------
[INFO] 
[INFO] --- reposync-maven-plugin:0.1.0:single (default-cli) @ standalone-pom ---
[INFO] Processing Dependency {groupId=org.springframework, artifactId=spring-tx, version=5.3.22, type=jar}
[INFO] Source repositories: [central (https://repo.maven.apache.org/maven2, default, releases+snapshots)]
[INFO] Target repository: local (http://localhost:8081/repository/maven-releases/, default, releases+snapshots)
[INFO] Discovered 16 artifacts
[INFO] 	org.springframework:spring-beans:jar:5.3.22
...
[INFO] 	org.springframework:spring-tx:pom:5.3.22
[INFO] Found 16 missing artifacts
[INFO] 	org.springframework:spring-beans:jar:5.3.22
...
[INFO] 	org.springframework:spring-tx:pom:5.3.22
[INFO] Dry run, exiting
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

#### reposync:bom

Synchronises bill of material, available parameters:
```
artifact
  The artifact - a string of the form
  groupId:artifactId:version[:packaging[:classifier]].
  User property: artifact

artifactId
  The artifactId of the artifact to sync. Ignored if artifact is used.
  User property: artifactId

classifier
  The classifier of the artifact to sync. Ignored if artifact is used.
  User property: classifier

dryRun (Default: false)
  Option can be used to obtain a summary of what will be transferred
  User property: dryRun

groupId
  The groupId of the artifact to sync. Ignored if artifact is used.
  User property: groupId

packaging (Default: pom)
  The packaging of the artifact to sync. Ignored if artifact is used.
  User property: packaging

scope (Default: compile)
  Scope threshold to include
  User property: scope

sourceRepositories
  Repositories in the format id::[layout]::url or just url, separated by
  comma. ie.
  central::default::https://repo.maven.apache.org/maven2,myrepo::::https://repo.acme.com,https://repo.acme2.com
  User property: sourceRepositories

syncJavadoc (Default: false)
  Synchronize javadocs
  User property: syncJavadoc

syncSources (Default: false)
  Synchronize sources
  User property: syncSources

targetRepository
  Repository in the format id::[layout]::url or just url
  myrepo::::https://repo.acme.com|https://repo.acme2.com
  Required: Yes
  User property: targetRepository

transitive (Default: true)
  Synchronize transitively, retrieving the specified artifact and all of its
  dependencies.
  User property: transitive

useSettingsRepositories (Default: false)
  Load information about source repositories from settings.xml file
  User property: useSettingsRepositories

version
  The version of the artifact to sync. Ignored if artifact is used.
  User property: version
```

Example:

```shell
% mvn tel.panfilov.maven:reposync-maven-plugin:0.2.0:bom \
 -Dartifact=org.springframework:spring-framework-bom:5.3.9 \
 -DsourceRepositories=central::default::https://repo.maven.apache.org/maven2 \
 -DtargetRepository=local::::http://localhost:8081/repository/maven-releases \
 -Dtransitive=true \
 -DdryRun=true \
 -DsyncSources=true \
 -DsyncJavadoc=true \
[INFO] Scanning for projects...
[INFO] 
[INFO] ------------------< org.apache.maven:standalone-pom >-------------------
[INFO] Building Maven Stub Project (No POM) 1
[INFO] --------------------------------[ pom ]---------------------------------
[INFO] 
[INFO] --- reposync-maven-plugin:0.1.0:bom (default-cli) @ standalone-pom ---
[INFO] Processing Dependency {groupId=org.springframework, artifactId=spring-framework-bom, version=5.3.9, type=pom}
[INFO] Source repositories: [central (https://repo.maven.apache.org/maven2, default, releases+snapshots)]
[INFO] Target repository: local (http://localhost:8081/repository/maven-releases/, default, releases+snapshots)
[INFO] Discovered 106 artifacts
[INFO] 	io.projectreactor:reactor-core:jar:3.4.8
...
[INFO] 	org.springframework:spring-websocket:pom:5.3.9
[INFO] Found 106 missing artifacts
[INFO] 	io.projectreactor:reactor-core:jar:3.4.8
...
[INFO] 	org.springframework:spring-websocket:pom:5.3.9
[INFO] Dry run, exiting
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------

```

#### reposync:local

Synchronises local repository artifacts as is
```
dryRun (Default: false)
  Option can be used to obtain a summary of what will be transferred
  User property: dryRun

sourceRepositories
  Repositories in the format id::[layout]::url or just url, separated by
  comma. ie.
  first::default::file:///home/user/.m2/repository,second::::file:///home/user/.m2/repository_2,file:///home/user/.m2/repository_3
  Only urls with file protocol is supported. 
  If source repositories is not provided by this property, and by user settings file, value is set to local repository by default.
  User property: sourceRepositories

syncJavadoc (Default: false)
  Synchronize javadocs
  User property: syncJavadoc

syncSources (Default: false)
  Synchronize sources
  User property: syncSources

targetRepository
  Repository in the format id::[layout]::url or just url
  myrepo::::https://repo.acme.com|https://repo.acme2.com
  Required: Yes
  User property: targetRepository

useSettingsRepositories (Default: false)
  Load information about source repositories from settings.xml file
  User property: useSettingsRepositories

failOnBadArtifact
  Whether to failure local goal execution in case of a error during recognizing artifact in local repository.
  This could be caused by incorrect repository structure, unexpected files which looks like as regular maven artifacts
  or if illegal characters is present in artifact filename, version, groupId.
  Default behaviour suppose that provided repository has no such errors.
  Default value is true, that means that errors will be logged at level WARNING, and execution will be continued.
  User property: failOnBadArtifact
```

Example:

```shell
% mvn tel.panfilov.maven:reposync-maven-plugin:0.2.0:local \
 -DsourceRepositories=local::default::file:///home/user/.m2/repository \
 -DtargetRepository=remote::::http://localhost:8081/repository/maven-releases \
 -DdryRun=true \
 -DsyncSources=true \
 -DsyncJavadoc=true \
[INFO] Scanning for projects...
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Build Order:
[INFO] 
[INFO] Maven Repository Sync                                              [pom]
[INFO] reposync-maven-plugin                                     [maven-plugin]
[INFO] 
[INFO] --------------------< tel.panfilov.maven:reposync >---------------------
[INFO] Building Maven Repository Sync 0.2.0                               [1/2]
[INFO] --------------------------------[ pom ]---------------------------------
[INFO] 
[INFO] --- reposync-maven-plugin:0.2.0:local (default-cli) @ reposync       ---
[INFO] Source repositories: [local (file:///home/user/.m2/repository, default, releases+snapshots)]
[INFO] Target repository: remote (http://localhost:8081/repository/maven-releases/, default, releases+snapshots)
[INFO] Discovered 4149 artifacts
...
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------

```
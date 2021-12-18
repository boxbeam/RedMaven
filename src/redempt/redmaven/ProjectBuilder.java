package redempt.redmaven;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ProjectBuilder {
	
	private static Set<String> endings = Set.of("pom", "xml", "sha1", "jar", "log");
	private static Map<ProjectInfo, CompletableFuture<Boolean>> building = new ConcurrentHashMap<>();
	private static Path m2 = Paths.get(System.getProperty("user.home")).resolve(".m2/repository");
	
	public static ProjectInfo getInfo(Path path, Path root) {
		path = root.relativize(path);
		if (path == null) {
			return null;
		}
		String name = path.getFileName().toString();
		String extension = Utils.getFileExtension(name);
		if (endings.contains(extension)) {
			path = path.getParent();
		}
		int folders = path.getNameCount();
		if (folders < 3) {
			return null;
		}
		String version = path.getName(folders - 1).toString();
		String projectName = path.getName(folders - 2).toString();
		String packageName = IntStream.range(0, folders - 2).mapToObj(path::getName).map(Path::toString).collect(Collectors.joining("."));
		return new ProjectInfo(packageName, projectName, version);
	}
	
	public static boolean build(ProjectInfo info) {
		if (building.containsKey(info)) {
			try {
				return building.get(info).get();
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}
		CompletableFuture<Boolean> future = new CompletableFuture<>();
		building.put(info, future);
		boolean success = build0(info);
		future.complete(success);
		building.remove(info, future);
		return success;
	}
	
	private static boolean build0(ProjectInfo info) {
		Path tmpDir = null;
		try {
			Path dest = RedMaven.getRepoPath().resolve(info.getFolderStructure());
			if (Files.exists(dest)) {
				return false;
			}
			String name = info.getProjectName();
			RepoInfo repoInfo = RedMaven.getRepos().get(name);
			if (repoInfo == null) {
				return false;
			}
			System.out.println("Attempting to build: " + info);
			tmpDir = Files.createTempDirectory(info.name());
			Path buildLog = tmpDir.resolve("build.log");
			System.out.println("Build dir: " + tmpDir);
			String[] env = new String[] {"BUILD_VERSION", info.version()};
			if (!Utils.executeCommand(tmpDir, buildLog, env, "git clone " + repoInfo.url())) {
				return false;
			}
			Path workingDir = Files.list(tmpDir).filter(Files::isDirectory).findFirst().get();
			List<String> commands = new ArrayList<>();
			commands.add("git checkout " + info.version());
			Collections.addAll(commands, repoInfo.buildCommands());
			if (!Utils.executeCommands(workingDir, buildLog, env, commands)) {
				return false;
			}
			if (!copyArtifacts(info)) {
				return false;
			}
			Files.copy(buildLog, dest.resolve("build.log"));
			DocsBuilder.buildDocs(workingDir, dest, info);
			Utils.deleteRecursive(tmpDir);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			if (tmpDir != null) {
				Utils.deleteRecursive(tmpDir);
			}
		}
		return false;
	}
	
	private static boolean copyArtifacts(ProjectInfo info) throws IOException {
		Path cached = m2.resolve(info.getFolderStructure());
		System.out.println("Cached: " + cached.toAbsolutePath());
		if (!Files.exists(cached)) {
			return false;
		}
		Path destination = RedMaven.getRepoPath().resolve(info.getFolderStructure());
		Files.createDirectories(destination);
		List<Path> files = Files.list(cached).collect(Collectors.toList());
		for (Path file : files) {
			Files.copy(file, destination.resolve(file.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
		}
		return true;
	}
	
}

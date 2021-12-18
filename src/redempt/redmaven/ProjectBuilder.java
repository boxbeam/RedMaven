package redempt.redmaven;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
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
		String name = path.getFileName().toString();
		String extension = HttpUtils.getFileExtension(name);
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
			if (!executeCommand(tmpDir, buildLog, env, "git clone " + repoInfo.url())) {
				return false;
			}
			Path workingDir = Files.list(tmpDir).filter(Files::isDirectory).findFirst().get();
			List<String> commands = new ArrayList<>();
			commands.add("git checkout " + info.version());
			Collections.addAll(commands, repoInfo.buildCommands());
			if (!executeCommands(workingDir, buildLog, env, commands)) {
				return false;
			}
			if (!copyArtifacts(info)) {
				return false;
			}
			Files.copy(buildLog, dest.resolve("build.log"));
			DocsBuilder.buildDocs(workingDir, dest, info);
			deleteRecursive(tmpDir);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			if (tmpDir != null) {
				deleteRecursive(tmpDir);
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
	
	public static boolean executeCommands(Path path, Path log, String[] env, List<String> commands) throws IOException {
		for (String cmd : commands) {
			if (!executeCommand(path, log, env, cmd)) {
				return false;
			}
		}
		return true;
	}
	
	public static boolean executeCommand(Path path, Path log, String[] env, String command) throws IOException {
		ProcessBuilder proc = new ProcessBuilder();
		for (int i = 0; i < env.length - 1; i += 2) {
			proc.environment().put(env[i], env[i + 1]);
		}
		Redirect redirect = Redirect.appendTo(log.toFile());
		proc.command(command.split(" ")).directory(path.toFile()).redirectError(redirect).redirectOutput(redirect);
		Process process = proc.start();
		try {
			return process.waitFor() == 0;
		} catch (InterruptedException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	public static void deleteRecursive(Path path) {
		try {
			Files.walk(path).sorted(Comparator.comparingInt(p -> -p.getNameCount())).collect(Collectors.toList()).forEach(WrappedConsumer.wrap(Files::delete));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}

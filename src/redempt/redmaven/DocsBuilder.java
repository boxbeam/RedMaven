package redempt.redmaven;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.jar.JarFile;

public class DocsBuilder {

	public static Path getDocsDir(ProjectInfo info) {
		return RedMaven.getRepoPath().resolve("javadoc").resolve(info.getFolderStructure()).getParent();
	}
	
	public static void buildDocs(Path gitDir, Path buildDir, ProjectInfo info) throws IOException {
		Path docsDir = getDocsDir(info);
		long commitTime = getTime(gitDir, info.version());
		if (!Files.exists(docsDir)) {
			buildDocs0(buildDir, docsDir, commitTime, info);
			return;
		}
		Path timeFile = docsDir.resolve("time");
		long time = Long.parseLong(Files.lines(timeFile).findFirst().get());
		if (commitTime > time) {
			buildDocs0(buildDir, docsDir, commitTime, info);
		}
	}
	
	private static void buildDocs0(Path buildDir, Path docsDir, long time, ProjectInfo info) throws IOException {
		Optional<String> jarname = Files.list(buildDir).map(Path::getFileName).map(Path::toString).filter(s -> s.contains("javadoc") && s.endsWith(".jar")).findFirst();
		if (jarname.isEmpty()) {
			return;
		}
		System.out.println("Updating docs for " + info);
		JarFile jar = new JarFile(buildDir.resolve(jarname.get()).toFile());
		if (Files.exists(docsDir)) {
			Utils.deleteRecursive(docsDir);
		}
		Files.createDirectories(docsDir);
		Utils.extract(jar, docsDir);
		jar.close();
		Files.writeString(docsDir.resolve("time"), String.valueOf(time), StandardOpenOption.CREATE);
	}
	
	private static long getTime(Path gitDir, String version) throws IOException {
		ProcessBuilder builder = new ProcessBuilder();
		builder.directory(gitDir.toFile());
		builder.command("git", "show", "-s", "--format=%ct", version);
		Process process = builder.start();
		try {
			process.waitFor();
			InputStream stream = process.getInputStream();
			String str = new String(stream.readAllBytes()).trim();
			return Long.parseLong(str);
		} catch (InterruptedException e) {
			e.printStackTrace();
			return 0;
		}
	}

}

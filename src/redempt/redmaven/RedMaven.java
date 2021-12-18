package redempt.redmaven;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class RedMaven {
	
	private static Map<String, RepoInfo> repos = new HashMap<>();
	private static Path repo = Paths.get("repo");
	
	public static Path getRepoPath() {
		return repo;
	}
	
	public static void loadRepos() throws IOException {
		repos.clear();
		Path path = Paths.get("repos");
		Files.lines(path).forEach(l -> {
			String[] split = l.split(";");
			String first = split[0].trim();
			String[] buildCmds = Arrays.stream(split).skip(1).map(String::trim).toArray(String[]::new);
			split = first.split(" ");
			String name = split[0];
			String url = split[1];
			repos.put(name, new RepoInfo(url, buildCmds));
		});
		System.out.println(repos);
	}
	
	public static Map<String, RepoInfo> getRepos() {
		return repos;
	}
	
	public static void main(String[] args) throws Exception {
		loadRepos();
		HttpServer server = ServerBuilder.createServer(Paths.get("redmaven.properties"));
		server.createContext("/", HttpUtils.wrapHandler(exchange -> {
			String p = exchange.getRequestURI().toString().replaceAll("^/", "");
			System.out.println("Got request for: " + p);
			Path path = repo.resolve(Paths.get(p));
			if (!Files.exists(path)) {
				ProjectInfo info = ProjectBuilder.getInfo(path, repo);
				if (info == null) {
					exchange.sendResponseHeaders(404, -1);
					return;
				}
				if (!ProjectBuilder.build(info)) {
					exchange.sendResponseHeaders(404, -1);
					return;
				}
			}
			if (Files.isDirectory(path)) {
				HttpUtils.sendDirectoryListing(exchange, path, repo);
			} else {
				HttpUtils.sendFile(exchange, path);
			}
		}));
		System.out.println("Starting");
		server.start();
	}
	
}

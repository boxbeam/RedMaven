package redempt.redmaven;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class Utils {
	
	public static HttpHandler wrapHandler(HttpHandler handler) {
		return ex -> {
			try {
				handler.handle(ex);
			} catch (Exception e) {
				e.printStackTrace();
			}
		};
	}
	
	public static void sendResponse(HttpExchange exchange, int responseCode, String content) throws IOException {
		exchange.sendResponseHeaders(responseCode, content.getBytes().length);
		OutputStream stream = exchange.getResponseBody();
		stream.write(content.getBytes(StandardCharsets.UTF_8));
		stream.flush();
		exchange.close();
	}
	
	public static void sendFile(HttpExchange exchange, Path path) throws IOException {
		if (!Files.exists(path)) {
			exchange.sendResponseHeaders(404, 0);
			exchange.close();
			System.out.println("404");
			return;
		}
		if (exchange.getRequestMethod().equals("HEAD")) {
			exchange.sendResponseHeaders(200, -1);
			return;
		}
		String ending = getFileExtension(path.getFileName().toString());
		exchange.getResponseHeaders().add("Content-Type", getContentType(ending));
		exchange.sendResponseHeaders(200, Files.size(path));
		InputStream stream = Files.newInputStream(path);
		stream.transferTo(exchange.getResponseBody());
		exchange.getResponseBody().flush();
		stream.close();
		exchange.close();
	}
	
	public static void sendDirectoryListing(HttpExchange exchange, Path path, Path root) throws IOException {
		if (!Files.exists(path)) {
			exchange.sendResponseHeaders(404, 0);
			exchange.close();
			return;
		}
		String listing = Files.list(path).sorted(Comparator.comparingInt(p -> Files.isDirectory(p) ? 0 : 1)).map(root::relativize).map(p -> {
			String href = p.toString();
			if (!href.startsWith("/")) {
				href = "/" + href;
			}
			return "<a href=" + href + ">" + p.getFileName().toString() + "</a>";
		}).collect(Collectors.joining("<br>"));
		sendResponse(exchange, 200, listing);
	}
	
	public static String getFileExtension(String filename) {
		int last = filename.lastIndexOf('.');
		if (last == -1) {
			return "";
		}
		return filename.substring(last + 1);
	}
	
	public static String getContentType(String ending) {
		return switch (ending) {
			case "jar" -> "application/java-archive";
			case "xml", "pom" -> "application/xml";
			case "html" -> "text/html";
			case "css" -> "text/css";
			case "js" -> "application/javascript";
			case "png" -> "image/png";
			default -> "multipart/mixed";
		};
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
	
	public static void extract(JarFile jar, Path extractDir) throws IOException {
		Enumeration<JarEntry> entries = jar.entries();
		List<JarEntry> list = new ArrayList<>();
		while (entries.hasMoreElements()) {
			list.add(entries.nextElement());
		}
		list.sort(Comparator.comparingInt(e -> e.isDirectory() ? 0 : 1));
		for (JarEntry entry : list) {
			Path path = extractDir.resolve(entry.getName());
			if (entry.isDirectory()) {
				Files.createDirectories(path);
				continue;
			}
			InputStream in = jar.getInputStream(entry);
			OutputStream out = Files.newOutputStream(path, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
			in.transferTo(out);
			out.flush();
			out.close();
			in.close();
		}
	}
	
}

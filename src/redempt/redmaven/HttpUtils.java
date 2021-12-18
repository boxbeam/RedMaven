package redempt.redmaven;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Collectors;

public class HttpUtils {
	
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
	
}

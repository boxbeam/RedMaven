package redempt.redmaven;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ServerBuilder {
	
	private static String defaultConfig = """
			address: localhost
			port: 80
			backlog: 20
			ssl: false
			keyStorePath: none
			keyStorePassword: none
			""".stripIndent();
	
	public static HttpServer createServer(Path options) throws IOException, NoSuchAlgorithmException, KeyStoreException, CertificateException, UnrecoverableKeyException, KeyManagementException {
		Executor exec = Executors.newCachedThreadPool();
		Map<String, String> props = new HashMap<>();
		if (!Files.exists(options)) {
			Files.write(options, defaultConfig.getBytes());
		}
		Files.lines(options).forEach(l -> {
			if (!l.contains(":")) return;
			String[] split = l.split(":", 2);
			props.put(split[0].trim(), split[1].trim());
		});
		String address = props.get("address");
		int port = Integer.parseInt(props.get("port"));
		int backlog = Integer.parseInt(props.get("backlog"));
		InetSocketAddress addr = new InetSocketAddress(address, port);
		if (!props.getOrDefault("ssl", "false").equals("true")) {
			HttpServer server = HttpServer.create(addr, backlog);
			server.setExecutor(exec);
			return server;
		}
		HttpsConfigurator sslConfig = createConfig(props);
		HttpsServer server = HttpsServer.create(addr, backlog);
		server.setExecutor(exec);
		server.setHttpsConfigurator(sslConfig);
		return server;
	}
	
	private static HttpsConfigurator createConfig(Map<String, String> props) throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, UnrecoverableKeyException, KeyManagementException {
		SSLContext context = SSLContext.getInstance("TLS");
		File keyStorePath = new File(props.get("keyStorePath"));
		char[] pass = props.get("keyStorePassword").toCharArray();
		KeyStore keyStore = KeyStore.getInstance(keyStorePath, pass);
		
		KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
		kmf.init(keyStore, pass);
		TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
		tmf.init(keyStore);
		context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		return new HttpsConfigurator(context) {
			
			@Override
			public void configure(HttpsParameters params) {
				SSLContext configContext = getSSLContext();
				SSLEngine engine = configContext.createSSLEngine();
				params.setNeedClientAuth(false);
				params.setCipherSuites(engine.getEnabledCipherSuites());
				params.setProtocols(engine.getEnabledProtocols());
				SSLParameters sslParams = context.getSupportedSSLParameters();
				params.setSSLParameters(sslParams);
			}
		};
	}
	
}

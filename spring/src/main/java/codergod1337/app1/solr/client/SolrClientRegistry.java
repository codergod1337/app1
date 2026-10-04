package codergod1337.app1.solr.client;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.jetty.HttpJettySolrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Die SolrJ-Clients, einer je Kern plus einer für die Verwaltung (CoreAdmin, ohne Kern in der URL). Statt fester Beans
 * je Kern ein Register: Die Kerne stehen in Postgres, ein neuer braucht keinen Code. Ein Client entsteht beim ersten
 * Zugriff und bleibt offen, er hält seinen Verbindungspool.
 *
 * HttpJettySolrClient, nie HttpJdkSolrClient: Der JDK-Client hängt nach einem Verbindungsabbruch für immer, ohne
 * Fehler und ohne Log (SOLR-17707, in 10.0.0 ungefixt). HTTP/1.1 mit dem Jetty-Pool, echte Parallelität für mehrere
 * Threads. Basic Auth mit den Zugangsdaten aus der .env, dieselben wie in der security.json des Containers.
 */
@Service
public class SolrClientRegistry {

	private static final Logger log = LoggerFactory.getLogger(SolrClientRegistry.class);

	/** Basis-URL ohne Kern, z. B. http://192.168.1.140:9009/solr */
	private final String baseUrl;
	private final String user;
	private final String password;

	private final Map<String, HttpJettySolrClient> clientsByCoreKey = new ConcurrentHashMap<>();
	private volatile HttpJettySolrClient adminClient;

	public SolrClientRegistry(@Value("${app1.solr.url}") String baseUrl, @Value("${app1.solr.user}") String user,
			@Value("${app1.solr.pw}") String password) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.user = user;
		this.password = password;
		log.info("Solr unter {} als {}", this.baseUrl, user);
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	/** Für die Verwaltung (CoreAdmin): die Basis-URL ohne Kern */
	public SolrClient adminClient() {
		HttpJettySolrClient client = adminClient;
		if (client == null) {
			synchronized (this) {
				client = adminClient;
				if (client == null) {
					client = newClient(baseUrl);
					adminClient = client;
				}
			}
		}
		return client;
	}

	/** Der Client eines Kerns: Basis-URL plus key. Ob es den Kern gibt, prüft der Aufrufer, hier entsteht nur der Client. */
	public SolrClient clientFor(String coreKey) {
		return clientsByCoreKey.computeIfAbsent(coreKey, key -> newClient(baseUrl + "/" + key));
	}

	private HttpJettySolrClient newClient(String url) {
		HttpJettySolrClient.Builder builder = new HttpJettySolrClient.Builder(url)
				.useHttp1_1(true)
				.withConnectionTimeout(10, TimeUnit.SECONDS)
				.withRequestTimeout(60, TimeUnit.SECONDS)
				.withIdleTimeout(120, TimeUnit.SECONDS);
		if (user != null && !user.isBlank()) {
			builder.withBasicAuthCredentials(user, password);
		}
		return builder.build();
	}

	@PreDestroy
	void closeAll() {
		for (HttpJettySolrClient client : clientsByCoreKey.values()) {
			close(client);
		}
		clientsByCoreKey.clear();
		if (adminClient != null) {
			close(adminClient);
			adminClient = null;
		}
	}

	private static void close(HttpJettySolrClient client) {
		try {
			client.close();
		} catch (RuntimeException e) {
			log.warn("Solr-Client ließ sich nicht schließen: {}", e.getMessage());
		}
	}

}

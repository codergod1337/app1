package codergod1337.app1.system.security;

import codergod1337.app1.system.security.login.SessionCookies;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Die Filterkette: wer welchen Pfad darf und woher das Token kommt.
 *
 * Zustandslos: Es gibt keine Server-Session, jede Anfrage bringt ihr Token selbst mit. CSRF ist aus, den Schutz
 * übernehmen die Cookies mit SameSite=Strict, und GET ändert nie etwas.
 *
 * Die Regeln, von oben nach unten:
 *   /error                  offen, sonst würde aus jedem Fehler ein zweiter
 *   /api/rest/v1/public/**  offen: Login, Refresh, Sitzung, Stammdaten, später Module ohne Anmeldung
 *   /api/rest/v1/admin/**   AR ADMIN
 *   /api/rest/v1/**         angemeldet, die Rechte prüfen dann die AR
 *   alles andere            gesperrt. Ein versehentlich falsch angelegter Endpunkt ist damit zu und nicht offen.
 *
 * Dazu prüfen die Services selbst mit @PreAuthorize, wer eine Methode aufrufen darf. Die Rollen kommen dabei nur aus
 * dem Token (Claim roles), ohne Zugriff auf die Datenbank. Eine Absage wird zu 403.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/error").permitAll()
						.requestMatchers("/api/rest/v1/public/**").permitAll()
						.requestMatchers("/api/rest/v1/admin/**").hasRole("ADMIN")
						.requestMatchers("/api/rest/v1/**").authenticated()
						.anyRequest().denyAll())
				.oauth2ResourceServer(oauth2 -> oauth2
						.bearerTokenResolver(jwtFromHeaderOrCookie(jwtDecoder))
						.jwt(jwt -> jwt.jwtAuthenticationConverter(accessRoleKeysFromClaim())));
		return http.build();
	}

	/**
	 * Erst der Header Authorization: Bearer (für Werkzeuge und Skripte), sonst das Cookie (der Normalfall im Browser).
	 *
	 * Ein ungültiges Cookie gilt als „nicht angemeldet“ und nicht als Fehler: Nach einem Neustart sind alle Tokens
	 * ungültig. Löste das einen Fehler aus, bekäme jede Anfrage eine Abweisung, auch die auf die offenen Pfade, und
	 * niemand käme mehr an den Login oder den Refresh. Ein ausdrücklich gesetzter Header bleibt streng.
	 */
	private BearerTokenResolver jwtFromHeaderOrCookie(JwtDecoder jwtDecoder) {
		DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
		return request -> {
			String jwtFromHeader = headerResolver.resolve(request);
			if (jwtFromHeader != null) {
				return jwtFromHeader;
			}
			if (request.getCookies() == null) {
				return null;
			}
			for (Cookie cookie : request.getCookies()) {
				if (SessionCookies.JWT_COOKIE.equals(cookie.getName())) {
					try {
						jwtDecoder.decode(cookie.getValue());
						return cookie.getValue();
					} catch (JwtException e) {
						return null;
					}
				}
			}
			return null;
		};
	}

	/**
	 * Aus jedem AR-Key im Claim roles wird eine Berechtigung ROLE_<key>. Das Präfix erwartet Spring bei hasRole, deshalb
	 * steht in den Regeln oben hasRole("ADMIN") und nicht hasRole("ROLE_ADMIN").
	 */
	private JwtAuthenticationConverter accessRoleKeysFromClaim() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(jwt -> {
			List<String> accessRoleKeys = jwt.getClaimAsStringList(JwtConfig.CLAIM_ROLES);
			if (accessRoleKeys == null) {
				return List.of();
			}
			return accessRoleKeys.stream()
					.<GrantedAuthority>map(accessRoleKey -> new SimpleGrantedAuthority("ROLE_" + accessRoleKey))
					.toList();
		});
		return converter;
	}

}

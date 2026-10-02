package codergod1337.app1.system.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Der Schlüssel, mit dem alle Tokens signiert und geprüft werden. Bei jedem Start entsteht ein neues RSA-Schlüsselpaar
 * nur im Arbeitsspeicher: Er kann aus keiner Platte und keinem Backup gestohlen werden, und nach einem Neustart sind
 * alle JWTs ungültig. Das Frontend holt sich dann per Refresh ein neues.
 *
 * Daraus folgt: Es läuft immer genau ein Backend-Prozess. Ein zweiter hätte einen anderen Schlüssel.
 */
@Configuration
public class JwtConfig {

	/** Die AR-Keys des Users im Token: die direkt zugewiesenen und die aus seiner ARC */
	public static final String CLAIM_ROLES = "roles";

	/** Die ARC des Users. Fehlt, wenn er keine hat: Eine ARC ist optional. */
	public static final String CLAIM_ACCESS_ROLE_COLLECTION = "arc";

	/** Über welchen Login das Token entstand (Area) */
	public static final String CLAIM_AREA = "area";

	/** Der Sitzungsplatz 1 bis 3 */
	public static final String CLAIM_SESSION = "session";

	private final RSAKey rsaKey = createRsaKey();

	/** Signiert mit dem privaten Schlüssel. */
	@Bean
	public JwtEncoder jwtEncoder() {
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
	}

	/** Prüft Signatur (fest RS256), Ablauf und die Sperrliste: Wurde das Token vor der Sperre ausgestellt, gilt es nicht. */
	@Bean
	public JwtDecoder jwtDecoder(TokenRevocations tokenRevocations) {
		NimbusJwtDecoder jwtDecoder;
		try {
			jwtDecoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
		} catch (JOSEException e) {
			throw new IllegalStateException("öffentlicher Schlüssel nicht ableitbar", e);
		}
		OAuth2TokenValidator<Jwt> notRevoked = jwt -> tokenRevocations
				.isUsersTokenRevoked(UUID.fromString(jwt.getSubject()), jwt.getIssuedAt())
						? OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token gesperrt", null))
						: OAuth2TokenValidatorResult.success();
		jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), notRevoked));
		return jwtDecoder;
	}

	private static RSAKey createRsaKey() {
		try {
			KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
			keyPairGenerator.initialize(2048);
			KeyPair keyPair = keyPairGenerator.generateKeyPair();
			return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
					.privateKey((RSAPrivateKey) keyPair.getPrivate())
					.keyID(UUID.randomUUID().toString())
					.build();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("RSA nicht verfügbar", e);
		}
	}

}

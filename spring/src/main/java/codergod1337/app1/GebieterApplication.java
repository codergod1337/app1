package codergod1337.app1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Findet auch die Records mit @ConfigurationProperties, z. B. SftpProperties. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GebieterApplication {

	public static void main(String[] args) {
		SpringApplication.run(GebieterApplication.class, args);
	}

}

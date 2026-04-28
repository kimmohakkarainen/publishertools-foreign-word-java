package fi.publishertools.foreign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ForeignWordApplication {

	public static void main(String[] args) {
		SpringApplication.run(ForeignWordApplication.class, args);
	}

}

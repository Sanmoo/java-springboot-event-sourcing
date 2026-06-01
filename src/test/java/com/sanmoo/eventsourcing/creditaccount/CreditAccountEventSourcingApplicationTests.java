package com.sanmoo.eventsourcing.creditaccount;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CreditAccountEventSourcingApplicationTests {

	@Autowired
	private DataSource dataSource;

	@Test
	void contextLoads() {
		assertThat(dataSource).isNotNull();
	}

}

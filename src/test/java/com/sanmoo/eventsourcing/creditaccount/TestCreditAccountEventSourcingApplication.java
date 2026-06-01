package com.sanmoo.eventsourcing.creditaccount;

import org.springframework.boot.SpringApplication;

public class TestCreditAccountEventSourcingApplication {

	public static void main(String[] args) {
		SpringApplication.from(CreditAccountApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

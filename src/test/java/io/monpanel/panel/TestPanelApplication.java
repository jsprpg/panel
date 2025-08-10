package io.monpanel.panel;

import org.springframework.boot.SpringApplication;

public class TestPanelApplication {

	public static void main(String[] args) {
		SpringApplication.from(PanelApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

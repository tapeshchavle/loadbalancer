package com.loadbalancer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Production-Grade Load Balancer.
 *
 * <p>{@link EnableScheduling} activates the scheduled health checker
 * and session eviction tasks.
 */
@SpringBootApplication
@EnableScheduling
public class LoadbalancerApplication {

	public static void main(String[] args) {
		SpringApplication.run(LoadbalancerApplication.class, args);
	}

}

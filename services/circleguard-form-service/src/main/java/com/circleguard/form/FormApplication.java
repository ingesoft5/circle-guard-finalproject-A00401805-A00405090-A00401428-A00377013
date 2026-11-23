/**
 * CG-DOC: Java source for the form service: Form Application.
 */
package com.circleguard.form;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FormApplication {
    public static void main(String[] args) {
        SpringApplication.run(FormApplication.class, args);
    }
}

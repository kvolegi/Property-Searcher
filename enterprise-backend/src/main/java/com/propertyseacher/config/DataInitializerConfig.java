package com.propertyseacher.config;

import com.propertyseacher.domain.entity.SearchTarget;
import com.propertyseacher.domain.repository.SearchTargetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializerConfig {

    private final SearchTargetRepository searchTargetRepository;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            if (searchTargetRepository.count() == 0) {
                log.info("Database has no search targets. Checking for settings.txt...");
                File settingsFile = new File("settings.txt");
                if (!settingsFile.exists()) {
                    settingsFile = new File("../settings.txt");
                }

                if (settingsFile.exists()) {
                    log.info("Migrating targets from settings.txt: {}", settingsFile.getAbsolutePath());
                    try (BufferedReader reader = new BufferedReader(new FileReader(settingsFile))) {
                        reader.readLine(); // bot name
                        reader.readLine(); // bot token
                        reader.readLine(); // empty line

                        String category;
                        while ((category = reader.readLine()) != null) {
                            String link = reader.readLine();
                            if (link != null && !link.isBlank()) {
                                searchTargetRepository.save(SearchTarget.builder()
                                        .title(category.trim())
                                        .url(link.trim())
                                        .platform("OLX")
                                        .active(true)
                                        .build());
                                log.info("Imported target: {} -> {}", category, link);
                            }
                        }
                    } catch (IOException e) {
                        log.warn("Could not read settings.txt: {}", e.getMessage());
                    }
                }

                // If still empty, add default mock and live target
                if (searchTargetRepository.count() == 0) {
                    searchTargetRepository.save(SearchTarget.builder()
                            .title("Квартири Одеса")
                            .url("https://www.olx.ua/uk/nedvizhimost/kvartiry/od/?currency=UAH")
                            .platform("OLX")
                            .active(true)
                            .build());

                    searchTargetRepository.save(SearchTarget.builder()
                            .title("Тестовий напрямок (Mock Engine)")
                            .url("https://mock-property.local")
                            .platform("MOCK")
                            .active(true)
                            .build());
                }
            }
        };
    }
}

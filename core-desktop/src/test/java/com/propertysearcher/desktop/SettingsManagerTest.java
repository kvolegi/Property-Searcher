package com.propertysearcher.desktop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SettingsManager Configuration & Persistence Tests")
class SettingsManagerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should write and reload categories with active and paused status intact")
    void testSaveAndLoadCategoriesRoundTrip() throws IOException {
        File settingsFile = tempDir.resolve("test_settings.txt").toFile();

        List<SettingsManager.CategoryEntry> entries = new ArrayList<>();
        entries.add(new SettingsManager.CategoryEntry("Квартири Київ", "https://www.olx.ua/uk/nedvizhimost/kvartiry/kiev/", true));
        entries.add(new SettingsManager.CategoryEntry("Будинки Одеса", "https://dom.ria.com/uk/arenda-domov/odessa/", false));

        SettingsManager.saveToFile(settingsFile, "TestPropertyBot", "1234567890:AAH_test_token_sample", entries);

        assertTrue(settingsFile.exists(), "Settings file must be created on disk");

        // Verify raw contents include header directives and #OFF# tag
        String rawContent = new String(Files.readAllBytes(settingsFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(rawContent.contains("TestPropertyBot"));
        assertTrue(rawContent.contains("1234567890:AAH_test_token_sample"));
        assertTrue(rawContent.contains("LANGUAGE="));
        assertTrue(rawContent.contains("CURRENCY="));
        assertTrue(rawContent.contains("#OFF# Будинки Одеса"), "Inactive category must have #OFF# prefix");

        // Reload via reader
        List<SettingsManager.CategoryEntry> reloaded = SettingsManager.loadCategoriesFromFile(settingsFile);
        assertEquals(2, reloaded.size());

        assertEquals("Квартири Київ", reloaded.get(0).name);
        assertEquals("https://www.olx.ua/uk/nedvizhimost/kvartiry/kiev/", reloaded.get(0).url);
        assertTrue(reloaded.get(0).active);

        assertEquals("Будинки Одеса", reloaded.get(1).name);
        assertEquals("https://dom.ria.com/uk/arenda-domov/odessa/", reloaded.get(1).url);
        assertFalse(reloaded.get(1).active, "Reloaded inactive category must have active=false");
    }

    @Test
    @DisplayName("Should return empty list without exception when file does not exist")
    void testLoadFromNonExistentFile() {
        File nonExistent = tempDir.resolve("does_not_exist.txt").toFile();
        List<SettingsManager.CategoryEntry> result = SettingsManager.loadCategoriesFromFile(nonExistent);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should ensure first launch directives never pollute bot credentials as empty")
    void testFirstLaunchDirectivesDoNotPolluteCredentials() {
        File file = tempDir.resolve("first_launch_settings.txt").toFile();

        // Simulate first launch: user selects Language=EN, Theme=GRAY, Font=SYSTEM before entering any token
        SettingsManager.AppSettings appSettings = new SettingsManager.AppSettings();
        appSettings.language = I18n.Language.EN;
        appSettings.uiColor = SettingsManager.UiColorTheme.GRAY;
        appSettings.uiFont = SettingsManager.UiFontMode.SYSTEM;
        appSettings.languageConfigured = true;

        SettingsManager.saveToFile(file, appSettings);

        // Reload
        SettingsManager.AppSettings reloaded = SettingsManager.loadSettingsFromFile(file);
        assertEquals("", reloaded.botName, "Bot name must be empty string on first launch");
        assertEquals("", reloaded.botToken, "Bot token must be empty string on first launch");
        assertEquals(I18n.Language.EN, reloaded.language);
        assertEquals(SettingsManager.UiColorTheme.GRAY, reloaded.uiColor);
        assertEquals(SettingsManager.UiFontMode.SYSTEM, reloaded.uiFont);
        assertTrue(reloaded.languageConfigured);
    }

    @Test
    @DisplayName("Should sanitize legacy corrupted files having directives on lines 1 and 2")
    void testCorruptedDirectivesOnCredentialsLinesAreSanitized() throws IOException {
        File file = tempDir.resolve("corrupted_settings.txt").toFile();
        String badContent = "LANGUAGE=EN\nUI_COLOR=GRAY\nLANGUAGE=EN\nCURRENCY=DUAL\nUI_THEME=CORPORATE_PRO\n\n";
        Files.write(file.toPath(), badContent.getBytes());

        SettingsManager.AppSettings s = SettingsManager.loadSettingsFromFile(file);
        assertEquals("", s.botName, "Directives on line 1 must not be interpreted as botName");
        assertEquals("", s.botToken, "Directives on line 2 must not be interpreted as botToken");
        assertEquals(I18n.Language.EN, s.language);
        assertEquals(SettingsManager.UiColorTheme.GRAY, s.uiColor);
    }

    @Test
    @DisplayName("Should persist bot credentials and categories simultaneously without data loss")
    void testBotCredentialsAndCategoriesPersistence() {
        File file = tempDir.resolve("complete_settings.txt").toFile();

        SettingsManager.AppSettings s = new SettingsManager.AppSettings();
        s.botName = "Property Seacher";
        s.botToken = "8937208825:AAHKwl9aNpWLAeVRXB3fhgxbquQhP8MsVKQ";
        s.language = I18n.Language.UA;
        s.uiColor = SettingsManager.UiColorTheme.GRAY;
        s.uiFont = SettingsManager.UiFontMode.SYSTEM;
        s.currency = CurrencyService.CurrencyMode.USD;
        s.categories.add(new SettingsManager.CategoryEntry("Київ Оренда", "https://www.olx.ua/uk/nedvizhimost/kvartiry/dolgosrochnaya-arenda-kvartir/kiev/", true));
        s.categories.add(new SettingsManager.CategoryEntry("Львів Продаж", "https://dom.ria.com/uk/prodazha-kvartir/lvov/", false));

        SettingsManager.saveToFile(file, s);

        SettingsManager.AppSettings reloaded = SettingsManager.loadSettingsFromFile(file);
        assertEquals("Property Seacher", reloaded.botName);
        assertEquals("8937208825:AAHKwl9aNpWLAeVRXB3fhgxbquQhP8MsVKQ", reloaded.botToken);
        assertEquals(I18n.Language.UA, reloaded.language);
        assertEquals(SettingsManager.UiColorTheme.GRAY, reloaded.uiColor);
        assertEquals(SettingsManager.UiFontMode.SYSTEM, reloaded.uiFont);
        assertEquals(CurrencyService.CurrencyMode.USD, reloaded.currency);

        assertEquals(2, reloaded.categories.size());
        assertEquals("Київ Оренда", reloaded.categories.get(0).name);
        assertTrue(reloaded.categories.get(0).active);
        assertEquals("Львів Продаж", reloaded.categories.get(1).name);
        assertFalse(reloaded.categories.get(1).active);
    }

    @Test
    @DisplayName("Should safely handle empty or corrupt files")
    void testLoadFromEmptyFile() throws IOException {
        File emptyFile = tempDir.resolve("empty.txt").toFile();
        Files.write(emptyFile.toPath(), new byte[0]);

        List<SettingsManager.CategoryEntry> result = SettingsManager.loadCategoriesFromFile(emptyFile);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}

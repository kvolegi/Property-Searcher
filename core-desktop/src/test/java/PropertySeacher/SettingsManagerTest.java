package PropertySeacher;

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
        String rawContent = new String(Files.readAllBytes(settingsFile.toPath()));
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
    @DisplayName("Should safely handle empty or corrupt files")
    void testLoadFromEmptyFile() throws IOException {
        File emptyFile = tempDir.resolve("empty.txt").toFile();
        Files.write(emptyFile.toPath(), new byte[0]);

        List<SettingsManager.CategoryEntry> result = SettingsManager.loadCategoriesFromFile(emptyFile);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}

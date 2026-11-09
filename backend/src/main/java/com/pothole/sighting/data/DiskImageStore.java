package com.pothole.sighting.data;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import com.pothole.config.PotholeProperties;
import com.pothole.sighting.domain.ImageStore;

import org.springframework.stereotype.Component;

@Component
class DiskImageStore implements ImageStore {

    private final Path directory;

    DiskImageStore(PotholeProperties properties) {
        this.directory = Path.of(properties.getImageDir());
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create image directory " + directory, ex);
        }
    }

    @Override
    public String finalPath(UUID sightingId) {
        return storedFile(sightingId).toString();
    }

    @Override
    public void writeTemp(UUID sightingId, byte[] bytes) {
        try {
            Files.write(tempPath(sightingId), bytes);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void promote(UUID sightingId) {
        Path temp = tempPath(sightingId);
        Path stored = storedFile(sightingId);
        try {
            try {
                Files.move(temp, stored, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, stored, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void deleteTemp(UUID sightingId) {
        try {
            Files.deleteIfExists(tempPath(sightingId));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private Path storedFile(UUID sightingId) {
        return directory.resolve(sightingId + ".jpg");
    }

    private Path tempPath(UUID sightingId) {
        return directory.resolve(sightingId + ".jpg.part");
    }
}

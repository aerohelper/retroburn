// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Puts the Windows helper's files where PowerShell can run them.
 *
 * <p>The files ship inside the application, which PowerShell cannot read from, so they are copied to
 * a folder named after their SHA-256 hash and checked before every start; a damaged or stale copy is
 * replaced. This guards against accidents, not attacks: the folder belongs to the user running
 * RetroBurn. See {@code docs/design/LLD-windows-platform.md} section 3.
 */
final class HelperFiles {

    static final String SCRIPT = "retroburn-helper.ps1";
    static final String SOURCE = "RetroBurnHelper.cs";

    private static final String RESOURCE_FOLDER = "/windows-helper/";
    private static final int FOLDER_NAME_HEX_CHARS = 16;

    private final Path root;
    private final Map<String, byte[]> contents;
    private final String folderName;

    /** @param root the folder that holds one subfolder per helper version */
    HelperFiles(Path root) {
        this.root = root;
        this.contents = new LinkedHashMap<>();
        for (String name : List.of(SCRIPT, SOURCE)) {
            contents.put(name, readResource(name));
        }
        this.folderName = HexFormat.of().formatHex(sha256(contents)).substring(0, FOLDER_NAME_HEX_CHARS);
    }

    /**
     * The script to pass to PowerShell, with its C# source next to it, copied or repaired if needed.
     *
     * @throws UncheckedIOException if the files cannot be written
     */
    synchronized Path script() {
        Path folder = root.resolve(folderName);
        try {
            Files.createDirectories(folder);
            for (Map.Entry<String, byte[]> file : contents.entrySet()) {
                Path target = folder.resolve(file.getKey());
                if (!Files.isRegularFile(target) || !Arrays.equals(Files.readAllBytes(target), file.getValue())) {
                    Path temporary = Files.createTempFile(folder, file.getKey(), ".tmp");
                    Files.write(temporary, file.getValue());
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not prepare the Windows helper in " + folder, e);
        }
        return folder.resolve(SCRIPT);
    }

    private static byte[] readResource(String name) {
        try (InputStream input = HelperFiles.class.getResourceAsStream(RESOURCE_FOLDER + name)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled helper file " + name);
            }
            return input.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read bundled helper file " + name, e);
        }
    }

    private static byte[] sha256(Map<String, byte[]> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            files.values().forEach(digest::update);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }
}

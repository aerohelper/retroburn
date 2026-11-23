// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.credits;

import io.aerohelper.retroburn.common.OperatingSystem;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One entry of the third-party component register in {@code credits/components.json}.
 *
 * <p>The field meanings are documented in {@code docs/design/LLD-credits.md}.
 *
 * @param id stable identifier, lower case with dashes
 * @param name the project's own spelling of its name
 * @param role what RetroBurn uses it for
 * @param authors who makes it
 * @param license SPDX licence expression
 * @param homepage upstream web address
 * @param provision how it reaches the user
 * @param platforms the operating systems it is used on
 * @param licenseFiles file names under {@code credits/licenses/}, shown in this order
 * @param noticeFiles file names under {@code credits/notices/} (upstream NOTICE files and
 *     third-party notices), possibly empty
 * @param mavenArtifacts {@code groupId:artifactId} pairs this entry covers; empty for non-Maven
 *     components
 */
public record ThirdPartyComponent(
        String id,
        String name,
        String role,
        String authors,
        String license,
        String homepage,
        Provision provision,
        Set<OperatingSystem> platforms,
        List<String> licenseFiles,
        List<String> noticeFiles,
        List<String> mavenArtifacts) {

    public ThirdPartyComponent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(authors, "authors");
        Objects.requireNonNull(license, "license");
        Objects.requireNonNull(homepage, "homepage");
        Objects.requireNonNull(provision, "provision");
        platforms = Set.copyOf(platforms);
        licenseFiles = List.copyOf(licenseFiles);
        noticeFiles = List.copyOf(noticeFiles);
        mavenArtifacts = List.copyOf(mavenArtifacts);
        if (licenseFiles.isEmpty()) {
            throw new IllegalArgumentException("Component '" + id + "' has no licence file");
        }
    }

    /** Whether this component is used on the given operating system. */
    public boolean isUsedOn(OperatingSystem operatingSystem) {
        return platforms.contains(operatingSystem);
    }
}

/**
 * Copyright (c) 2012, md_5. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * The name of the author may not be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.md_5.specialsource.util;

import com.google.common.base.CharMatcher;
import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import net.md_5.specialsource.SpecialSource;

public class FileLocator {

    public static boolean useCache = true;

    private static File download(String url) throws IOException {
        // Create temporary dir in system location
        File tempDir = new File(System.getProperty("java.io.tmpdir") + File.separator + "ss-cache");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        // Create our own cache file here, replacing potentially invalid characters
        String id = CharMatcher.javaLetterOrDigit().or(CharMatcher.anyOf("-_.")).negate().replaceFrom(url.toString(), '_');
        File file = new File(tempDir, id);

        // Check cache for a hit
        if (file.exists() && useCache) {
            if (true || SpecialSource.verbose()) {
                System.out.println("Using cached file " + file.getPath() + " for " + url);
            }

            return file;
        }

        // Nope, we need to download it ourselves
        if (true || SpecialSource.verbose()) {
            System.out.println("Downloading " + url);
        }

        // TODO: Better solution for cleaning names - this extraneous '\' is introduced by path joining on the mcp dir
        try (ReadableByteChannel rbc = Channels.newChannel(new URL(url.replace('\\', '/')).openStream());
             FileOutputStream fos = new FileOutputStream(file)) {
            fos.getChannel().transferFrom(rbc, 0, 1 << 24);
        }

        // Success!
        if (SpecialSource.verbose()) {
            System.out.println("Downloaded to " + file.getPath());
        }

        return file;
    }

    /**
     * Either download, or get a File object corresponding to the given URL /
     * file name.
     *
     * @param path
     * @return
     * @throws IOException
     */
    public static File getFile(String path) throws IOException {
        if (isHTTPURL(path)) {
            return download(path);
        }
        return new File(path);
    }

    public static boolean isHTTPURL(String string) {
        return string.startsWith("http://") || string.startsWith("https://");
    }
}

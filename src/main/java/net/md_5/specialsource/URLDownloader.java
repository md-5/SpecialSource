/**
 * Copyright (c) 2012-2013, md_5. All rights reserved.
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
package net.md_5.specialsource;

import com.google.common.base.CharMatcher;
import com.google.common.io.Files;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;

public class URLDownloader {

    private static String CACHE_FOLDER = "SpecialSource.cache";

    private URL url;
    public static boolean verbose = true;
    public static boolean useCache = true;

    public URLDownloader(String urlString) throws MalformedURLException {
        urlString = urlString.replace('\\', '/'); // Windows paths to URLs - TODO: improve this in JarMapping directory loading
        this.url = new URL(urlString);
    }

    public File download() throws IOException {
        // Cache to temporary directory
        String sep = System.getProperty("file.separator");
        String id = CharMatcher.JAVA_LETTER_OR_DIGIT.or(CharMatcher.anyOf("-_.")).negate().replaceFrom(url.toString(), '_');
        String cacheFilename = System.getProperty("java.io.tmpdir") + sep + CACHE_FOLDER + sep + id;
        File file = new File(cacheFilename);

        if (file.exists() && useCache) {
            if (verbose) {
                System.out.println("Using cached file "+file.getPath()+" for "+url);
            }

            return file;
        }

        // Download
        file.getParentFile().mkdirs();
        if (verbose) {
            System.out.println("Downloading "+url);
        }

        url.openConnection();
        InputStream inputStream = url.openStream();


        FileOutputStream outputStream = new FileOutputStream(file);

        byte[] buffer = new byte[4096];
        int n = 0;
        while ((n = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, n);
        }

        inputStream.close();
        outputStream.close();

        if (verbose) {
            System.out.println("Downloaded to " + file.getPath());
        }

        return file;
    }

    public static File getLocalFile(String string) throws IOException {
        if (isHTTPURL(string)) {
            URLDownloader downloader = new URLDownloader(string);
            return downloader.download();
        } else {
            return new File(string);
        }
    }

    public static boolean isHTTPURL(String string) {
        return string.startsWith("http://") || string.startsWith("https://");
    }

    // Borrowed from Guava 13 (since we're on Guava 12) - TODO: remove and use Guava after https://github.com/MinecraftForge/FML/commit/937e9a016936195e4dc51f33ab9e8dde52621684
    /**
     * Returns the file name without its
     * <a href="http://en.wikipedia.org/wiki/Filename_extension">file extension</a> or path. This is
     * similar to the {@code basename} unix command. The result does not include the '{@code .}'.
     *
     * @param file The name of the file to trim the extension from. This can be either a fully
     *     qualified file name (including a path) or just a file name.
     * @return The file name without its path or extension.
     * @since 14.0
     */
    public static String getNameWithoutExtension(String file) {
        String fileName = new File(file).getName();
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }
}

// Copyright 2022 ForgeFlower contributors and JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.util;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipFile;

public final class ZipFileCache implements AutoCloseable {
  private final Map<String, ZipFile> files = new ConcurrentHashMap<>();

  public ZipFile get(final String path) throws IOException {
    try {
      return this.files.computeIfAbsent(path, pth -> {
        try {
          return new ZipFile(new File(pth));
        } catch (final IOException ex) {
          throw new UncheckedIOException(ex);
        }
      });
    } catch (final UncheckedIOException ex) {
      throw ex.getCause();
    }
  }

  @Override
  public void close() throws IOException {
    IOException failure = null;
    for (final Map.Entry<String, ZipFile> entry : this.files.entrySet()) {
      try {
        entry.getValue().close();
      } catch (final IOException ex) {
        if (failure == null) {
          failure = ex;
        } else {
          failure.addSuppressed(ex);
        }
      }
    }
    this.files.clear();
  }
}

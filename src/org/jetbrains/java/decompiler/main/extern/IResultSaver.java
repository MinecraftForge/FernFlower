// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.main.extern;

import org.jetbrains.java.decompiler.main.DecompilerContext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.jar.Manifest;

public interface IResultSaver extends AutoCloseable {
  long STABLE_ZIP_TIMESTAMP = 0x386D4380; // 01/01/2000 00:00:00 java 8 breaks when using 0.

  void saveFolder(String path);

  void copyFile(String source, String path, String entryName);

  // mapping: a flat array of pairs of (input line number, output line number). null when -bsm=0
  void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping);

  void createArchive(String path, String archiveName, Manifest manifest);

  void saveDirEntry(String path, String archiveName, String entryName);

  void copyEntry(String source, String path, String archiveName, String entry);

  @Deprecated
  default void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {}

  // mapping: a flat array of pairs of (input line number, output line number). null when -bsm=0
  default void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content, int[] mapping) {
    this.saveClassEntry(path, archiveName, qualifiedName, entryName, content);
  }

  void closeArchive(String path, String archiveName);

  @Override
  default void close() throws IOException {}

  default byte[] getCodeLineData(int[] mappings) {
    if (mappings == null || mappings.length == 0 || !DecompilerContext.getOption(IFernflowerPreferences.DUMP_CODE_LINES)) {
      return null;
    }
    ByteBuffer buf = ByteBuffer.allocate(5 + (mappings.length * 2));
    buf.order(ByteOrder.LITTLE_ENDIAN);
    // Zip Extra entry header, described in http://www.info-zip.org/doc/appnote-19970311-iz.zip
    buf.putShort((short)0x4646); //FF - ForgeFlower
    buf.putShort((short)((mappings.length * 2) + 1)); // Mapping data + our version marker
    buf.put((byte)1); // Version code, in case we want to change it in the future.
    for (int line : mappings) {
        buf.putShort((short)line);
    }
    return buf.array();
  }
}

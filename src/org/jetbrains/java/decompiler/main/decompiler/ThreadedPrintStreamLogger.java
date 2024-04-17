// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.main.decompiler;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadedPrintStreamLogger extends PrintStreamLogger {

  private final ThreadLocal<AtomicInteger> indent = ThreadLocal.withInitial(AtomicInteger::new);

  public ThreadedPrintStreamLogger(PrintStream printStream) {
    super(printStream);
  }

  @Override
  protected void push() {
    indent.get().incrementAndGet();
  }

  @Override
  protected void pop() {
    indent.get().decrementAndGet();
  }

  @Override
  protected int indent() {
    return indent.get().get();
  }

  @Override
  protected String prefix() {
    return Thread.currentThread().getName() + ": ";
  }
}

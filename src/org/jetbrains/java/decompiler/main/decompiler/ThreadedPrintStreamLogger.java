// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.main.decompiler;

import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.util.TextUtil;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadedPrintStreamLogger extends IFernflowerLogger {

  private final PrintStream stream;
  private final ThreadLocal<AtomicInteger> indent = ThreadLocal.withInitial(AtomicInteger::new);

  public ThreadedPrintStreamLogger(PrintStream printStream) {
    stream = printStream;
  }

  @Override
  public void writeMessage(String message, Severity severity) {
    if (accepts(severity)) {
      Thread th = Thread.currentThread();
      stream.println(th.getName() + ": " + severity.prefix + TextUtil.getIndentString(indent.get().get()) + message);
    }
  }

  @Override
  public void writeMessage(String message, Severity severity, Throwable t) {
    if (accepts(severity)) {
      writeMessage(message, severity);
      t.printStackTrace(stream);
    }
  }

  @Override
  public void startProcessingClass(String className) {
    if (accepts(Severity.INFO)) {
      writeMessage("PreProcessing class " + className, Severity.INFO);
      indent.get().incrementAndGet();
    }
  }

  @Override
  public void endProcessingClass() {
    if (accepts(Severity.INFO)) {
      indent.get().decrementAndGet();
      writeMessage("... done", Severity.INFO);
    }
  }

  @Override
  public void startReadingClass(String className) {
    if (accepts(Severity.INFO)) {
      writeMessage("Decompiling class " + className, Severity.INFO);
      indent.get().incrementAndGet();
    }
  }

  @Override
  public void endReadingClass() {
    if (accepts(Severity.INFO)) {
      indent.get().decrementAndGet();
      writeMessage("... done", Severity.INFO);
    }
  }

  @Override
  public void startClass(String className) {
    if (accepts(Severity.INFO)) {
      writeMessage("Processing class " + className, Severity.TRACE);
      indent.get().decrementAndGet();
    }
  }

  @Override
  public void endClass() {
    if (accepts(Severity.INFO)) {
      indent.get().decrementAndGet();
      writeMessage("... proceeded", Severity.TRACE);
    }
  }

  @Override
  public void startMethod(String methodName) {
    if (accepts(Severity.INFO)) {
      writeMessage("Processing method " + methodName, Severity.TRACE);
      indent.get().decrementAndGet();
    }
  }

  @Override
  public void endMethod() {
    if (accepts(Severity.INFO)) {
      indent.get().decrementAndGet();
      writeMessage("... proceeded", Severity.TRACE);
    }
  }

  @Override
  public void startWriteClass(String className) {
    if (accepts(Severity.INFO)) {
      writeMessage("Writing class " + className, Severity.TRACE);
      indent.get().decrementAndGet();
    }
  }

  @Override
  public void endWriteClass() {
    if (accepts(Severity.INFO)) {
      indent.get().decrementAndGet();
      writeMessage("... written", Severity.TRACE);
    }
  }
}

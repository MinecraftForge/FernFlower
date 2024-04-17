// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.main.decompiler;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.util.TextUtil;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class PrintStreamLogger extends IFernflowerLogger {

  protected final PrintStream stream;
  private int indent;
  private final List<String> errors = new ArrayList<>();

  public PrintStreamLogger(PrintStream printStream) {
    stream = printStream;
    indent = 0;
  }

  @Override
  public void writeMessage(String message, Severity severity) {
    if (accepts(severity)) {
      writeMessage(message, severity, null);
    }
  }

  @Override
  public void writeMessage(String message, Severity severity, Throwable t) {
    if (accepts(severity)) {
      String prefix = prefix() + severity.prefix + TextUtil.getIndentString(indent());
      StringBuilder buf = new StringBuilder();
      buf.append(prefix).append(message);

      if (t != null) {
        String indentStr = (String)DecompilerContext.getProperty(IFernflowerPreferences.INDENT_STRING);
        var sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        for (var line : sw.toString().split(System.lineSeparator())) {
          buf.append(System.lineSeparator()).append(prefix).append(indentStr).append(line);
        }
      }

      stream.println(buf.toString());
    }
  }

  @Override
  public void startProcessingClass(String className) {
    if (accepts(Severity.INFO)) {
      writeMessage("PreProcessing class " + className, Severity.INFO);
      push();
    }
  }

  @Override
  public void endProcessingClass() {
    if (accepts(Severity.INFO)) {
      pop();
      writeMessage("... done", Severity.INFO);
    }
  }

  @Override
  public void startReadingClass(String className) {
    if (accepts(Severity.INFO)) {
      writeMessage("Decompiling class " + className, Severity.INFO);
      push();
    }
  }

  @Override
  public void endReadingClass() {
    if (accepts(Severity.INFO)) {
      pop();
      writeMessage("... done", Severity.INFO);
    }
  }

  @Override
  public void startClass(String className) {
    if (accepts(Severity.INFO)) {
      writeMessage("Processing class " + className, Severity.TRACE);
      push();
    }
  }

  @Override
  public void endClass() {
    if (accepts(Severity.INFO)) {
      pop();
      writeMessage("... proceeded", Severity.TRACE);
    }
  }

  @Override
  public void startMethod(String methodName) {
    if (accepts(Severity.INFO)) {
      writeMessage("Processing method " + methodName, Severity.TRACE);
      push();
    }
  }

  @Override
  public void endMethod() {
    if (accepts(Severity.INFO)) {
      pop();
      writeMessage("... proceeded", Severity.TRACE);
    }
  }

  @Override
  public void startWriteClass(String className) {
    if (accepts(Severity.INFO)) {
      writeMessage("Writing class " + className, Severity.TRACE);
      push();
    }
  }

  @Override
  public void endWriteClass() {
    if (accepts(Severity.INFO)) {
      pop();
      writeMessage("... written", Severity.TRACE);
    }
  }

  protected void push() {
    indent++;
  }

  protected void pop() {
    indent--;
  }

  protected int indent() {
    return indent;
  }

  protected String prefix() {
    return "";
  }

  @Override
  public synchronized void logError(String message, Throwable t) {
    writeMessage(message, Severity.ERROR, t);
    var sw = new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    errors.add(message + System.lineSeparator() + sw.toString());
  }

  @Override
  public List<String> getErrors() {
    return errors;
  }
}

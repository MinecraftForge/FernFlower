// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.main.collectors;

import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructContext;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructInnerClassesAttribute;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.*;
import java.util.stream.Collectors;

public class ImportCollector {
  private static final String JAVA_LANG_PACKAGE = "java.lang";

  private final Map<String, String> mapSimpleNames = new HashMap<>();
  private final Set<String> setNotImportedNames = new HashSet<>();
  // set of field names in this class and all its predecessors.
  private final Set<String> setFieldNames = new HashSet<>();
  private final Map<String, Map<String, String>> mapInnerClassNames = new HashMap<>();
  private final String currentPackageSlash;
  private final String currentPackagePoint;

  public ImportCollector(ClassNode root) {
    String clName = root.classStruct.qualifiedName;
    int index = clName.lastIndexOf('/');
    if (index >= 0) {
      String packageName = clName.substring(0, index);
      currentPackageSlash = packageName + '/';
      currentPackagePoint = packageName.replace('/', '.');
    }
    else {
      currentPackageSlash = "";
      currentPackagePoint = "";
    }

    Map<String, StructClass> classes = DecompilerContext.getStructContext().getClasses();
    StructClass currentClass = root.classStruct;
    while (currentClass != null) {
      // all field names for the current class ..
      for (StructField f : currentClass.getFields()) {
        setFieldNames.add(f.getName());
      }

      // .. and traverse through parent.
      currentClass = currentClass.superClass != null ? classes.get(currentClass.superClass.getString()) : null;
    }

    collectConflictingShortNames(root, new HashMap<>());
  }

  /**
   * Check whether the package-less name ClassName is shaded by variable in a context of
   * the decompiled class
   * @param classToName - pkg.name.ClassName - class to find the nested name for
   * @return ClassName if the name is not shaded by local field, pkg.name.ClassName otherwise
   */
  public String getNestedNameInClassContext(String classToName) {
    String nestedName = getNestedName(classToName);
    if (setFieldNames.contains(nestedName)) {
      return classToName;
    }
    else {
      return nestedName;
    }
  }

  public String getNestedName(String fullName) {
    return getNestedName(fullName, true);
  }

  public String getNestedName(String fullName, boolean imported) {
    ClassNode node = DecompilerContext.getClassProcessor().getMapRootClasses().get(fullName.replace('.', '/')); //todo[r.sh] anonymous classes?

    String nestedName;
    if (node != null && node.classStruct.isOwn()) {
      nestedName = node.simpleName;

      while (node.parent != null && node.type == ClassNode.CLASS_MEMBER) {
        //noinspection StringConcatenationInLoop
        nestedName = node.parent.simpleName + '.' + nestedName;
        node = node.parent;
      }

      if (node.type == ClassNode.CLASS_ROOT) {
        fullName = node.classStruct.qualifiedName;
        fullName = fullName.replace('/', '.');
      }
      else {
        return nestedName;
      }
    }
    else {
      fullName = fullName.replace('/', '.');
      int lastDot = fullName.lastIndexOf('.');
      if (lastDot != -1) {
        nestedName = fullName.substring(lastDot + 1).replace("$", ".");
      } else {
        nestedName = fullName;
      }
    }

    String outerShortName = fullName;
    String packageName = "";

    int lastDot = fullName.lastIndexOf('.');
    if (lastDot >= 0) {
      int firstNestedDot = nestedName.indexOf(".");
      if (firstNestedDot >= 0) {
        outerShortName = nestedName.substring(0, firstNestedDot);
      } else {
        outerShortName = nestedName;
      }
      packageName = fullName.substring(0, lastDot);
    }

    StructContext context = DecompilerContext.getStructContext();

    // check for another class which could 'shadow' this one. Three cases:
    // 1) class with the same short name in the current package
    // 2) class with the same short name in the default package
    // 3) inner class with the same short name in the current class, a super class, or an implemented interface
    boolean existsDefaultClass =
      (context.getClass(currentPackageSlash + outerShortName) != null && !packageName.equals(currentPackagePoint)) || // current package
      (context.getClass(outerShortName) != null && !currentPackagePoint.isEmpty());

    ClassNode currCls = (ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE);
    String mapKey = currCls == null ? "" : currCls.classStruct.qualifiedName;
    Map<String, String> innerClassNames = mapInnerClassNames.getOrDefault(mapKey, new HashMap<>());
    if (!existsDefaultClass && innerClassNames.containsKey(outerShortName) && !innerClassNames.get(outerShortName).equals(fullName)) {
      // if the class being accessed is also an inner class
      // attempt to import the outer class and reference OuterClass.InnerClass
      if (context.getClass(packageName.replace('.', '/') + "$" + outerShortName) != null) {
        lastDot = fullName.lastIndexOf(".", lastDot - 1);
        if (lastDot >= 0) {
          nestedName = fullName.substring(lastDot + 1);
          outerShortName = packageName.substring(lastDot + 1);
          packageName = packageName.substring(0, lastDot);

          if (innerClassNames.containsKey(outerShortName)  && !innerClassNames.get(outerShortName).equals(packageName + '.' + outerShortName)) {
            existsDefaultClass = true;
            nestedName = null;
          }
        }
      }
      else {
        existsDefaultClass = true;
      }
    }

    if (existsDefaultClass ||
        (mapSimpleNames.containsKey(outerShortName) && !packageName.equals(mapSimpleNames.get(outerShortName)))) {
      //  don't return full name because if the class is a inner class, full name refers to the parent full name, not the child full name
      return nestedName == null ? fullName : (packageName + "." + nestedName);
    }
    else if (!mapSimpleNames.containsKey(outerShortName)) {
      mapSimpleNames.put(outerShortName, packageName);
      if (!imported) {
        setNotImportedNames.add(outerShortName);
      }
    }

    return nestedName;
  }

  public void writeImports(TextBuffer buffer, boolean addSeparator) {
    List<String> imports = packImports();
    for (String line : imports) {
      buffer.append("import ").append(line).append(';').appendLineSeparator();
    }
    if (addSeparator && !imports.isEmpty()) {
      buffer.appendLineSeparator();
    }
  }

  private List<String> packImports() {
    return mapSimpleNames.entrySet().stream()
      .filter(ent ->
                // exclude the current class or one of the nested ones
                // empty, java.lang and the current packages
                !setNotImportedNames.contains(ent.getKey()) &&
                !ent.getValue().isEmpty() &&
                !JAVA_LANG_PACKAGE.equals(ent.getValue()) &&
                !ent.getValue().equals(currentPackagePoint)
      )
      .sorted(Map.Entry.<String, String>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
      .map(ent -> ent.getValue() + "." + ent.getKey())
      .collect(Collectors.toList());
  }

  private void collectConflictingShortNames(ClassNode root, Map<String, String> rootNames) {
    Map<String, String> names = new HashMap<>(rootNames);
    getSuperClassInnerClasses(root, names);
    mapInnerClassNames.put(root.classStruct.qualifiedName, names);

    for (ClassNode nested : root.nested) {
      collectConflictingShortNames(nested, names);
    }
  }

  private void getSuperClassInnerClasses(ClassNode node, Map<String, String> names) {
    Map<String, StructClass> classes = DecompilerContext.getStructContext().getClasses();
    LinkedList<String> queue = new LinkedList<>();
    StructClass currentClass = node.classStruct;
    while (currentClass != null) {
      if (currentClass.superClass != null) {
        queue.add(currentClass.superClass.getString());
      }

      Collections.addAll(queue, currentClass.getInterfaceNames());

      // .. all inner classes for the current class ..
      StructInnerClassesAttribute attribute = currentClass.getAttribute(StructGeneralAttribute.ATTRIBUTE_INNER_CLASSES);
      if (attribute != null) {
        for (StructInnerClassesAttribute.Entry entry : attribute.getEntries()) {
          if (entry.enclosingName != null && entry.enclosingName.equals(currentClass.qualifiedName)) {
            names.put(entry.simpleName, entry.innerName.replace('/', '.').replace('$', '.'));
          }
        }
      }

      // .. and traverse through parent.
      currentClass = !queue.isEmpty() ? classes.get(queue.removeFirst()) : null;
      while (currentClass == null && !queue.isEmpty()) {
        currentClass = classes.get(queue.removeFirst());
      }
    }
  }
}
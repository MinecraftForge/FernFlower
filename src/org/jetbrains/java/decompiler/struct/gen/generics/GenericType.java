// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.struct.gen.generics;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.typeann.TypeAnnotationWriteHelper;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.gen.Type;
import org.jetbrains.java.decompiler.struct.gen.VarType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.Objects;
import java.util.Set;

public class GenericType extends VarType implements Type {

  public static final int WILDCARD_EXTENDS = 1;
  public static final int WILDCARD_SUPER = 2;
  public static final int WILDCARD_UNBOUND = 3;
  public static final int WILDCARD_NO = 4;

  private static final Pattern DOT_SPLIT = Pattern.compile("\\.");

  private final VarType parent;
  private final List<VarType> arguments;
  private final int wildcard;

  public static final GenericType DUMMY_VAR = new GenericType(CodeConstants.TYPE_GENVAR, 0, "", null, null, GenericType.WILDCARD_NO);

  public GenericType(int type, int arrayDim, String value, VarType parent, List<VarType> arguments, int wildcard) {
    super(type, arrayDim, value, getFamily(type, arrayDim), getStackSize(type, arrayDim), false);
    this.parent = parent;
    this.arguments = arguments == null ? Collections.<VarType>emptyList() : arguments;
    this.wildcard = wildcard;
  }

  public static VarType parse(String signature) {
    return parse(signature, WILDCARD_NO);
  }

  public static VarType parse(String signature, int wildcard) {
    int type = 0;
    int arrayDim = 0;
    String value = null;
    List<VarType> params = null;
    VarType parent = null;

    int index = 0;
    loop:
    while (index < signature.length()) {
      switch (signature.charAt(index)) {
        case '[':
          arrayDim++;
          break;

        case 'T':
          type = CodeConstants.TYPE_GENVAR;
          value = signature.substring(index + 1, signature.length() - 1);
          break loop;

        case 'L':
          type = CodeConstants.TYPE_OBJECT;
          signature = signature.substring(index + 1, signature.length() - 1);
          String cl = getNextClassSignature(signature);

          if (cl.length() == signature.length()) {
            int argStart = cl.indexOf('<');
            if (argStart >= 0) {
              value = cl.substring(0, argStart);
              params = parseArgumentsList(cl.substring(argStart + 1, cl.length() - 1));
            }
            else {
              value = cl;
            }
          }
          else {
            StringBuilder name_buff = new StringBuilder();
            while (signature.length() > 0) {
              String name = cl;
              String args = null;

              int argStart = cl.indexOf('<');
              if (argStart >= 0) {
                name = cl.substring(0, argStart);
                args = cl.substring(argStart + 1, cl.length() - 1);
              }

              if (name_buff.length() > 0) {
                name_buff.append('$');
              }
              name_buff.append(name);

              value = name_buff.toString();
              params = args == null ? null : parseArgumentsList(args);

              if (cl.length() == signature.length()) {
                break;
              }
              else {
                if (parent == null && params == null) {
                  parent = GenericType.parse("L" + value + ";");
                }
                else {
                  parent = new GenericType(CodeConstants.TYPE_OBJECT, 0, value, parent, params, wildcard);
                }

                signature = signature.substring(cl.length() + 1);
              }
              cl = getNextClassSignature(signature);
            }
          }
          break loop;

        default:
          value = signature.substring(index, index + 1);
          type = VarType.getType(value.charAt(0));
      }

      index++;
    }

    if (type == CodeConstants.TYPE_GENVAR) {
      return new GenericType(type, arrayDim, value, null, null, wildcard);
    }
    else if (type == CodeConstants.TYPE_OBJECT) {
      if (parent == null && params == null && wildcard == WILDCARD_NO) {
        return new VarType(type, arrayDim, value);
      }
      else {
        return new GenericType(type, arrayDim, value, parent, params, wildcard);
      }
    }
    else {
      return new VarType(type, arrayDim, value);
    }
  }

  private static String getNextClassSignature(String value) {
    int counter = 0;
    int index = 0;

    loop:
    while (index < value.length()) {
      switch (value.charAt(index)) {
        case '<' -> counter++;
        case '>' -> counter--;
        case '.' -> {
          if (counter == 0) {
            break loop;
          }
        }
      }

      index++;
    }

    return value.substring(0, index);
  }

  private static List<VarType> parseArgumentsList(String value) {
    if (value == null) {
      return null;
    }

    List<VarType> args = new ArrayList<>();

    while (value.length() > 0) {
      String typeStr = getNextType(value);
      int len = typeStr.length();
      int wildcard = switch (typeStr.charAt(0)) {
        case '*' -> WILDCARD_UNBOUND;
        case '+' -> WILDCARD_EXTENDS;
        case '-' -> WILDCARD_SUPER;
        default -> WILDCARD_NO;
      };

      if (wildcard != WILDCARD_NO) {
        typeStr = typeStr.substring(1);
      }

      args.add(typeStr.length() == 0 ? null : GenericType.parse(typeStr, wildcard));

      value = value.substring(len);
    }

    return args;
  }

  public static String getNextType(String value) {
    int counter = 0;
    int index = 0;

    boolean contMode = false;

    loop:
    while (index < value.length()) {
      switch (value.charAt(index)) {
        case '*':
          if (!contMode) {
            break loop;
          }
          break;
        case 'L':
        case 'T':
          if (!contMode) {
            contMode = true;
          }
        case '[':
        case '+':
        case '-':
          break;
        default:
          if (!contMode) {
            break loop;
          }
          break;
        case '<':
          counter++;
          break;
        case '>':
          counter--;
          break;
        case ';':
          if (counter == 0) {
            break loop;
          }
      }

      index++;
    }

    return value.substring(0, index + 1);
  }

  public static VarType withWildcard(VarType var, int wildcard) {
    if (var.isGeneric()) {
      GenericType genVar = (GenericType)var;
      return new GenericType(genVar.getType(), genVar.getArrayDim(), genVar.getValue(), genVar.parent, genVar.arguments, wildcard);
    }
    return new GenericType(var.getType(), var.getArrayDim(), var.getValue(), null, Collections.emptyList(), wildcard);
  }

  @Override
  public GenericType decreaseArrayDim() {
    assert getArrayDim() > 0 : this;
    return new GenericType(getType(), getArrayDim() - 1, getValue(), parent, arguments, wildcard);
  }

  @Override
  public VarType resizeArrayDim(int newArrayDim) {
    return new GenericType(getType(), newArrayDim, getValue(), parent, arguments, wildcard);
  }

  public VarType getParent() {
    return parent;
  }

  public List<VarType> getArguments() {
    return arguments;
  }

  @Override
  public boolean isGeneric() {
    return getType() == CodeConstants.TYPE_GENVAR || !arguments.isEmpty() || parent != null || wildcard != WILDCARD_NO;
  }

  public int getWildcard() {
    return wildcard;
  }

  public List<TypeAnnotationWriteHelper> appendCastName(final StringBuilder clsName, List<TypeAnnotationWriteHelper> typeAnnWriteHelpers) {
    if (parent != null && parent.isGeneric()) {
      typeAnnWriteHelpers = ((GenericType) parent).appendCastName(clsName, typeAnnWriteHelpers);
      typeAnnWriteHelpers = ExprProcessor.writeNestedTypeAnnotations(clsName, typeAnnWriteHelpers);
      clsName.append(".").append(getValue().substring(parent.getValue().length() + 1));
    }
    else {
      List<String> nestedTypes = Arrays.asList(DOT_SPLIT.split(DecompilerContext.getImportCollector().getNestedName(getValue().replace('/', '.'))));
      ExprProcessor.writeNestedClass(clsName, this, nestedTypes, typeAnnWriteHelpers);
      ExprProcessor.popNestedTypeAnnotation(typeAnnWriteHelpers);
    }
    typeAnnWriteHelpers = appendTypeArguments(clsName, typeAnnWriteHelpers);
    return typeAnnWriteHelpers;
  }

  private List<TypeAnnotationWriteHelper> appendTypeArguments(final StringBuilder buffer, List<TypeAnnotationWriteHelper> typeAnnWriteHelpers) {
    if (!arguments.isEmpty()) {
      buffer.append('<');

      for (int i = 0; i < arguments.size(); i++) {
        if (i > 0) {
          buffer.append(", ");
        }

        VarType par = arguments.get(i);
        // only take type paths that are in the generic
        List<TypeAnnotationWriteHelper> locTypeAnnWriteHelpers = GenericMain.getGenericTypeAnnotations(i, typeAnnWriteHelpers);
        typeAnnWriteHelpers.removeAll(locTypeAnnWriteHelpers);
        locTypeAnnWriteHelpers = GenericMain.writeTypeAnnotationBeforeWildCard(buffer, par, wildcard, locTypeAnnWriteHelpers);
        if (par == null) { // Wildcard unbound
          buffer.append('?');
        }
        else if (par.isGeneric()) {
          GenericType gen = (GenericType)par;
          switch (gen.getWildcard()) {
            case GenericType.WILDCARD_EXTENDS:
              buffer.append("? extends ");
              break;
            case GenericType.WILDCARD_SUPER:
              buffer.append("? super ");
              break;
          }
          locTypeAnnWriteHelpers = GenericMain.writeTypeAnnotationAfterWildCard(buffer, par, locTypeAnnWriteHelpers);
          buffer.append(GenericMain.getGenericCastTypeName(gen, locTypeAnnWriteHelpers));
        }
        else {
          buffer.append(ExprProcessor.getCastTypeName(par, locTypeAnnWriteHelpers));
        }
      }

      buffer.append(">");
    }
    return typeAnnWriteHelpers;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    switch(getWildcard()) {
      case GenericType.WILDCARD_EXTENDS:
        buf.append("? extends ");
        break;
      case GenericType.WILDCARD_SUPER:
        buf.append("? super ");
      break;
    }
    buf.append(super.toString());
    appendTypeArguments(buf, Collections.emptyList());
    return buf.toString();
  }


  @Override
  public VarType remap(Map<VarType, VarType> map) {
    VarType main = super.remap(map);
    if (main != this) {
      int wild = main != null && main.isGeneric() ? ((GenericType)main).getWildcard() : WILDCARD_NO;
      if (main != null && getWildcard() != WILDCARD_NO && wild != getWildcard()) {
        main = withWildcard(main, getWildcard());
      }
      return main;
    }
    boolean changed = false;
    VarType parent = getParent();
    if (map.containsKey(parent)) {
      parent = map.get(parent);
      changed = true;
    }
    List<VarType> newArgs = new ArrayList<>();
    for (VarType arg : getArguments()) {
      VarType newArg = null;
      if (arg != null) {
        newArg = arg.remap(map);
      }
      if (newArg != arg) {
        newArgs.add(newArg);
        changed = true;
      } else {
        newArgs.add(arg);
      }
    }
    if (changed) {
      return new GenericType(main.getType(), main.getArrayDim(), main.getValue(), parent, newArgs, getWildcard());
    }
    return this;
  }

  public boolean equalsExact(Object o) {
    if (o == this) {
      return true;
    }

    if (!(o instanceof VarType)) {
      return false;
    }

    if (!(o instanceof GenericType)) {
      return parent == null && arguments.isEmpty() && wildcard == WILDCARD_NO && o.equals(this);
    }

    GenericType gt = (GenericType)o;
    if (getType() != gt.getType() || getArrayDim() != gt.getArrayDim() || wildcard != gt.wildcard || !Objects.equals(getValue(), gt.getValue())) {
      return false;
    }

    return this.argumentsEqual(gt);
  }

  public boolean argumentsEqual(GenericType gt) {
    if (arguments.size() != gt.arguments.size()) {
      return false;
    }

    for (int i = 0; i < arguments.size(); ++i) {
      VarType t = arguments.get(i);
      VarType o = gt.arguments.get(i);

      if (t == null && o == null) {
        continue;
      }

      if (t == null || o == null || t.isGeneric() != o.isGeneric() || !t.equals(o)) {
        return false;
      }

      if ((t.isGeneric() && !((GenericType)t).equalsExact(o))) {
        return false;
      }
    }
    return true;
  }

  public static boolean isAssignable(VarType from, VarType to, Map<VarType, List<VarType>> named) {
    if (from.getArrayDim() != to.getArrayDim()) {
        return false;
    }

    if (from.getType() == CodeConstants.TYPE_OBJECT && from.getType() == to.getType()) {
      if (!DecompilerContext.getStructContext().instanceOf(from.getValue(), to.getValue())) {
        return false;
      }
    }
    else if (!from.equals(to)) {
      if (from.getType() == CodeConstants.TYPE_GENVAR && from.getType() != to.getType() && named.containsKey(from)) {
        return named.get(from).stream().anyMatch(bound -> {
          if (to.isGeneric() && !bound.getValue().equals(to.getValue())) {
            VarType _new = getGenericSuperType(bound, to);

            if (bound != _new && _new.isGeneric()) {
              bound = _new;
            }
          }

          return areArgumentsAssignable(bound, to, named);
        });
      }
      else {
        return false;
      }
    }

    if (to.isGeneric() && !from.getValue().equals(to.getValue())) {
      VarType _new = getGenericSuperType(from, to);

      if (from != _new && _new.isGeneric()) {
        from = _new;
      }
    }

    return areArgumentsAssignable(from, to, named);
  }

  public static boolean areArgumentsAssignable(VarType from, VarType to, Map<VarType, List<VarType>> named) {
    if (from.isGeneric() && to.isGeneric()) {
      GenericType genFrom = (GenericType)from;
      GenericType genTo = (GenericType)to;

      if (genFrom.arguments.size() != genTo.arguments.size()) {
        return genFrom.arguments.isEmpty() || genTo.arguments.isEmpty();
      }

      for (int i = 0; i < genFrom.arguments.size(); ++i) {
        VarType f = genFrom.arguments.get(i);
        VarType t = genTo.arguments.get(i);

        if (t == null) {
          continue;
        }

        int tWild = t.isGeneric() ? ((GenericType)t).wildcard : WILDCARD_NO;

        if (f == null) {
          StructClass cls = DecompilerContext.getStructContext().getClass(genFrom.getValue());
          VarType bounds = cls.getSignature().fbounds.get(i).get(0);
          if (VarType.VARTYPE_OBJECT.equals(bounds)) {
            return false;
          }
          f = bounds;
        }
        else if (f.getType() == CodeConstants.TYPE_GENVAR && f.getType() != t.getType() && named.containsKey(f))
        {
          f = named.get(f).get(0);
        }

        int fWild = f.isGeneric() ? ((GenericType)f).wildcard : WILDCARD_NO;

        if (tWild == WILDCARD_EXTENDS) {
          if (fWild == WILDCARD_SUPER || !DecompilerContext.getStructContext().instanceOf(f.getValue(), t.getValue())) {
            return false;
          }
        }
        else if (tWild == WILDCARD_SUPER) {
          if (fWild == WILDCARD_EXTENDS || !DecompilerContext.getStructContext().instanceOf(t.getValue(), f.getValue())) {
            return false;
          }
        }
        else if (tWild == WILDCARD_NO && fWild != tWild && genFrom.wildcard == genTo.wildcard) {
          return false;
        }
        else if (!f.getValue().equals(t.getValue())) {
          return false;
        }

        if (!areArgumentsAssignable(f, t, named)) {
          return false;
        }
      }
    }

    return true;
  }

  public List<GenericType> getAllGenericVars() {
    List<GenericType> ret = new ArrayList<>();

    if (this.getType() == CodeConstants.TYPE_GENVAR) {
      ret.add((GenericType)this.resizeArrayDim(0));
      return ret;
    }

    for (VarType arg : arguments) {
      if (arg != null && arg.isGeneric()) {
        ret.addAll(((GenericType)arg).getAllGenericVars());
      }
    }
    return ret;
  }

  public void mapGenVarsTo(GenericType other, Map<VarType, VarType> map) {
    if (arguments.size() == other.arguments.size()) {
      for (int i = 0; i < arguments.size(); ++i) {
        VarType thisArg = arguments.get(i);
        VarType otherArg = other.arguments.get(i);

        if (thisArg != null && !DUMMY_VAR.equals(otherArg)) {
          if (thisArg.getType() == CodeConstants.TYPE_GENVAR) {
            int tWild = ((GenericType)thisArg).wildcard;
            int oWild = otherArg == null || !otherArg.isGeneric() ? WILDCARD_NO : ((GenericType)otherArg).wildcard;

            if (tWild == oWild && tWild != WILDCARD_NO) {
              thisArg = withWildcard(thisArg, WILDCARD_NO);
              otherArg = withWildcard(otherArg, WILDCARD_NO);
            }

            if (otherArg == null && thisArg.getArrayDim() == 0) {
              if (!map.containsKey(thisArg)) {
                map.put(thisArg, otherArg);
              }
            }
            else if (otherArg != null && thisArg.getArrayDim() <= otherArg.getArrayDim()) {
              if (thisArg.getArrayDim() > 0) {
                otherArg = otherArg.resizeArrayDim(otherArg.getArrayDim() - thisArg.getArrayDim());
                thisArg = thisArg.resizeArrayDim(0);
              }
              if (!map.containsKey(thisArg)) {
                map.put(thisArg, otherArg);
              }
              else {
                VarType curr = map.get(thisArg);
                int cWild = curr == null || !curr.isGeneric() ? WILDCARD_NO : ((GenericType)curr).wildcard;
                if (oWild != cWild) {
                  map.put(thisArg, withWildcard(otherArg, WILDCARD_NO));
                }
              }
            }
          }
          else if (thisArg.isGeneric() && otherArg != null && otherArg.isGeneric()) {
            ((GenericType)thisArg).mapGenVarsTo((GenericType)otherArg, map);
          }
        }
      }

      if (other.parent != null && other.parent.isGeneric())
      {
        GenericType parent = this.parent != null && this.parent.isGeneric() ? (GenericType)this.parent : null;
        if (parent == null) {
          StructClass cls = DecompilerContext.getStructContext().getClass(other.parent.getValue());
          parent = cls.getSignature().genericType;
        }

        parent.mapGenVarsTo((GenericType)other.parent, map);
      }
    }
  }

  public boolean hasUnknownGenericType(Set<VarType> namedGenerics) {
    if (getType() == CodeConstants.TYPE_GENVAR) {
      return !namedGenerics.contains(this.resizeArrayDim(0));
    }

    for (VarType arg : arguments) {
      if (arg != null && arg.isGeneric() && ((GenericType)arg).hasUnknownGenericType(namedGenerics)) {
        return true;
      }
    }
    return false;
  }

  public static VarType getGenericSuperType(VarType derivedType, VarType superType) {
    StructClass dcls = DecompilerContext.getStructContext().getClass(derivedType.getValue());
    StructClass scls = DecompilerContext.getStructContext().getClass(superType.getValue());

    if (dcls != null && scls != null) {
      Map<String, Map<VarType, VarType>> hierarchy = dcls.getAllGenerics();

      if (hierarchy.containsKey(scls.qualifiedName) && scls.getSignature() != null) {
        Map<VarType, VarType> tempMap = new HashMap<>();

        if (derivedType.isGeneric() && dcls.getSignature() != null) {
          dcls.getSignature().genericType.mapGenVarsTo((GenericType)derivedType, tempMap);
          // Given MyClass<T extends MyClass<T>> implements MyInterface<T>
          // converting MyClass<?> to MyInterface should produce MyInterface<MyClass<?>> not MyInterface<?>
          for (int i = 0; i < dcls.getSignature().fparameters.size(); ++i) {
            VarType param = parse("T" + dcls.getSignature().fparameters.get(i) + ";");
            if (tempMap.get(param) == null) {
              List<VarType> bounds = dcls.getSignature().fbounds.get(i);
              if (!bounds.isEmpty()) {
                VarType replacement = bounds.get(0).remap(tempMap);
                if (!VarType.VARTYPE_OBJECT.equals(replacement))
                  tempMap.put(param, replacement);
              }
            }
          }
        }
        return scls.getSignature().genericType.remap(hierarchy.get(scls.qualifiedName)).remap(tempMap);
      }
    }
    return derivedType;
  }
}

package org.jetbrains.java.decompiler.modules.decompiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.modules.decompiler.exps.AnnotationExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ConstExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.FunctionExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.InvocationExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.NewExprent;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.attr.StructAnnotationAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;

public class MethodHandleDescriptorHelper
{

    private static final String OBJECT_ARRAY_PARAM = "([Ljava/lang/Object;)";

    public static void changeMethodHandleDescriptorsStats(Statement stat) {
        for (Statement inner : stat.getStats()) {
            changeMethodHandleDescriptorsStats(inner);
        }

        if (stat.getExprents() != null) {
            for (int i = 0; i < stat.getExprents().size(); i++) {
                Exprent exprent = stat.getExprents().get(i);
                Exprent updated = updateExprent(exprent);
                if (updated != null) {
                    stat.getExprents().set(i, updated);
                }
            }
        }
    }

    private static Exprent updateExprent(Exprent exprent) {
        for (Exprent inner : exprent.getAllExprents()) {
            Exprent updated = updateExprent(inner);
            if (updated != null) {
                exprent.replaceExprent(inner, updated);
            }
        }

        if (exprent.type == Exprent.EXPRENT_INVOCATION) {
            InvocationExprent invocation = (InvocationExprent) exprent;
            StructClass cl = DecompilerContext.getStructContext().getClass(invocation.getClassName());
            if (cl != null) {
                StructMethod mtd = cl.getMethod(invocation.getName(), OBJECT_ARRAY_PARAM + "Ljava/lang/Object;");
                if (mtd == null) {
                    mtd = cl.getMethod(invocation.getName(), OBJECT_ARRAY_PARAM + invocation.getDescriptor().ret);
                }
                if (mtd != null) {
                    StructAnnotationAttribute annotations = mtd.getAttribute(StructGeneralAttribute.ATTRIBUTE_RUNTIME_VISIBLE_ANNOTATIONS);
                    if (annotations != null) {
                        for (AnnotationExprent annotation : annotations.getAnnotations()) {
                            if (annotation.getClassName().equals("java/lang/invoke/MethodHandle$PolymorphicSignature")) {
                                MethodDescriptor oldDesc = invocation.getDescriptor();
                                invocation.setStringDescriptor(mtd.getDescriptor());
                                invocation.setDescriptor(MethodDescriptor.parseDescriptor(mtd.getDescriptor()));

                                List<Exprent> params = invocation.getParameters();
                                NewExprent newParam = new NewExprent(VarType.VARTYPE_OBJECT.resizeArrayDim(1), Collections.emptyList(), null);
                                newParam.setLstArrayElements(params);
                                List<Exprent> newParams = new ArrayList<>();
                                newParams.add(newParam);
                                invocation.setParameters(newParams);

                                if (!VarType.VARTYPE_VOID.equals(oldDesc.ret)) {
                                  List<Exprent> castExprents = new ArrayList<>();
                                  castExprents.add(invocation);
                                  castExprents.add(new ConstExprent(oldDesc.ret, null, null));
                                  FunctionExprent cast = new FunctionExprent(FunctionExprent.FUNCTION_CAST, castExprents, null);
                                  return cast;
                                }
                                return null;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

}

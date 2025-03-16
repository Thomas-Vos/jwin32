package com.falsepattern.jwin32.internal.conversion;

import com.falsepattern.jwin32.internal.conversion.common.*;

import java.lang.foreign.FunctionDescriptor;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.List;

public class GraalGenerator {

    private final CClass newClass = new CClass();
    private final CConstructor constructor = new CConstructor();
    private final List<String> downcallFunctions = new ArrayList<>();
    private final List<String> functionClasses = new ArrayList<>();

    public GraalGenerator() {
        newClass.accessSpecifier.vis = AccessSpecifier.Visibility.PUBLIC;
        newClass.name = "GraalVMHelper";
        newClass.pkg = "win32.mapped.graalvm";
        newClass.importImplicitly(new CType(FunctionDescriptor.class));

        var field = new CField();
        field.accessSpecifier.vis = AccessSpecifier.Visibility.PUBLIC;
        field.type = new CType((FunctionDescriptor[].class));
        field.name = "downcallFunctions";
        newClass.addField(field);

        field = new CField();
        field.accessSpecifier.vis = AccessSpecifier.Visibility.PUBLIC;
        field.type = new CType((Class[].class));
        field.name = "functionClasses";
        newClass.addField(field);

        constructor.accessSpecifier.vis = AccessSpecifier.Visibility.PUBLIC;
    }

    public void add(String fName) {
        try {
            var baseClass = Class.forName("win32.pure." + fName, false, GraalGenerator.class.getClassLoader());
            if (fName.endsWith("Vtbl")) {
                var classes = baseClass.getClasses();
                for (var nestedClass : classes) {
                    addMethods(nestedClass, baseClass);
                    try {
                        var functionClass = Class.forName(nestedClass.getName() + "$Function", false, GraalGenerator.class.getClassLoader());

                        var name = functionClass.getName().replace("$Function", ".Function");
                        name = name.replace(baseClass.getName() + "$", baseClass.getName() + ".");

                        functionClasses.add(name+".class");

                    } catch (ClassNotFoundException _) {
                    }
                }
            } else {
                addMethods(baseClass, null);
            }
        } catch (ClassNotFoundException _) {
        }
    }

    private void addMethods(Class<?> baseClass, Class<?> parentClass) {
        if (!baseClass.accessFlags().contains(AccessFlag.PUBLIC)) {
            return;
        }
        var methods = baseClass.getDeclaredMethods();
        for (var method : methods) {
            if (method.getReturnType() == FunctionDescriptor.class) {
                var name = baseClass.getName();
                if (parentClass != null) {
                    name = name.replace(parentClass.getName() + "$", parentClass.getName() + ".");
                }
                downcallFunctions.add(name + "."+method.getName()+"()");
            }
        }
    }

    public CClass generate() {
        constructor.code.append("downcallFunctions = new FunctionDescriptor[" + downcallFunctions.size() + "];\n");
        constructor.code.append("functionClasses = new Class[" + functionClasses.size() + "];\n");
        constructor.code.append("downcallFunctions0();\n");
        constructor.code.append("functionClasses0();\n");

        var method = new CMethod();
        method.name = "downcallFunctions0";
        newClass.addMethod(method);

        for (int i = 0; i < downcallFunctions.size(); i++) {
            var downcallFunction = downcallFunctions.get(i);

            method.code.append("try { downcallFunctions[").append(i).append("] = ").append(downcallFunction).append("; } catch(UnsatisfiedLinkError _) {}\n");
            var last = i != 0 && (i) % 1000  == 0;
            var methodCount = i / 1000;
            if (last && i != downcallFunctions.size()-1) {
                method.code.append("downcallFunctions").append(methodCount).append("();");
                method = new CMethod();
                newClass.addMethod(method);
                method.name = "downcallFunctions" + (methodCount);
            }
        }

        method = new CMethod();
        method.name = "functionClasses0";
        newClass.addMethod(method);

        for (int i = 0; i < functionClasses.size(); i++) {
            var functionClass = functionClasses.get(i);

            method.code.append("try { functionClasses[").append(i).append("] = ").append(functionClass).append("; } catch(UnsatisfiedLinkError _) {}\n");
            var last = i != 0 && (i) % 1000  == 0;
            var methodCount = i / 1000;
            if (last && i != functionClasses.size()-1) {
                method.code.append("functionClasses").append(methodCount).append("();");
                method = new CMethod();
                newClass.addMethod(method);
                method.name = "functionClasses" + (methodCount);
            }
        }

        newClass.addConstructor(constructor);
        return newClass;
    }
}

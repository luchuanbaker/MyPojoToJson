package com.clu.idea.utils;

import com.google.common.io.LineReader;
import com.intellij.lang.jvm.types.JvmReferenceType;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.PsiClassType.ClassResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class MyPojoToJsonCore {

    @NonNls
    private static final Map<String, Object> normalTypes = new HashMap<>();

    private static final BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

    static {
        LocalDateTime now = LocalDateTime.now();
        String dateTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        normalTypes.put("Boolean", false);
        normalTypes.put("Float", zero);
        normalTypes.put("Double", zero);
        normalTypes.put("BigDecimal", zero);
        normalTypes.put("Number", 0);
        normalTypes.put("CharSequence", "");
        normalTypes.put("Date", dateTime);
        normalTypes.put("Temporal", now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        normalTypes.put("LocalDateTime", dateTime);
        normalTypes.put("LocalDate", now.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        normalTypes.put("LocalTime", now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    static PsiClassType checkAndGetPsiType(DataContext dataContext) {
        Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);

        if (!(psiFile instanceof PsiJavaFile)) {
            return null;
        }

        if (editor == null) {
            return null;
        }

        PsiElement psiElement = psiFile.findElementAt(editor.getCaretModel().getOffset());

        // 类型，方法返回值，变量类型等
        PsiTypeElement selectedTypeElement = PsiTreeUtil.getContextOfType(psiElement, PsiTypeElement.class);
        if (selectedTypeElement != null) {
            PsiType selectType = selectedTypeElement.getType();
            if (selectType instanceof PsiClassType) {
                return (PsiClassType) selectType;
            }
        }

        // new
        PsiNewExpression selectedNewExpression = PsiTreeUtil.getContextOfType(psiElement, PsiNewExpression.class);
        if (selectedNewExpression != null) {
            PsiType selectType = selectedNewExpression.getType();
            if (selectType instanceof PsiClassType) {
                return (PsiClassType) selectType;
            }
        }

        // 构造方法
        PsiClass psiClass = null;
        PsiMethod selectedMethod = PsiTreeUtil.getContextOfType(psiElement, PsiMethod.class);
        if (selectedMethod != null) {
            if (selectedMethod.isConstructor()) {
                psiClass = selectedMethod.getContainingClass();
            }
        }

        if (psiClass == null) {
            // 类声明
            if (psiElement instanceof PsiIdentifier) {
                PsiClass selectedClass = PsiTreeUtil.getContextOfType(psiElement, PsiClass.class);
                if (selectedClass != null && psiElement.getText() != null && selectedClass.getNameIdentifier() != null && psiElement.getText().equals(selectedClass.getNameIdentifier().getText())) {
                    psiClass = selectedClass;
                } else {
                    // Bean.of()...
                    PsiReferenceExpression referenceExpression = PsiTreeUtil.getContextOfType(psiElement, PsiReferenceExpression.class);
                    if (referenceExpression != null) {
                        PsiElement element = referenceExpression.resolve();
                        if (element instanceof PsiClass) {
                            psiClass = (PsiClass) element;
                        }
                    }
                }
            }
        }

        if (psiClass != null) {
            return PsiTypesUtil.getClassType(psiClass);
        }

        return null;
    }

    static String getClassName(PsiType psiType) {
        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(psiType);
        if (psiClass != null) {
            return psiClass.getName();
        }
        return psiType.getPresentableText();
    }

    static Object resolveType(@NotNull PsiType psiType, @NotNull ProcessingInfo processingInfo) {
        String genericText = psiType.getPresentableText();

        processingInfo.checkOverflow();

        Object primitiveTypeDefaultValue = getDefaultValue(psiType);
        if (primitiveTypeDefaultValue != null) {
            return primitiveTypeDefaultValue;
        }

        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(psiType);

        if (psiClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName())) {
            return Collections.emptyMap();
        }

        if (psiType instanceof PsiArrayType) {
            List<Object> list = new ArrayList<>();
            PsiType deepType = psiType.getDeepComponentType();
            list.add(resolveType(deepType, processingInfo));
            return list;
        } else {
            Map<String, Object> map = new LinkedHashMap<>();
            if (psiClass == null) {
                return map;
            } else if (psiClass.isEnum()) {
                for (PsiField field : psiClass.getFields()) {
                    if (field instanceof PsiEnumConstant) {
                        return field.getName();
                    }
                }
                return "";
            } else {
                List<String> fieldTypeNames = new ArrayList<>();
                PsiType[] superTypes = psiType.getSuperTypes();
                fieldTypeNames.add(genericText);
                fieldTypeNames.addAll(Arrays.stream(superTypes).map(PsiType::getPresentableText).collect(Collectors.toList()));
                if (fieldTypeNames.stream().anyMatch((s) -> s.startsWith("Collection") || s.startsWith("Iterable"))) {
                    List<Object> list = new ArrayList<>();
                    PsiType deepType = PsiUtil.extractIterableTypeParameter(psiType, false);
                    if (deepType != null) {
                        list.add(resolveType(deepType, processingInfo));
                    }
                    return list;
                }

                List<String> retain = new ArrayList<>(fieldTypeNames);
                /*取交集，常见类型的默认值*/
                retain.retainAll(normalTypes.keySet());
                if (!retain.isEmpty()) {
                    String typeName = retain.get(0);
                    return normalTypes.get(typeName);
                } else {
                    if (isIgnoreForValue(psiClass)) {
                        return null;
                    }

                    String result = listAllMyNonStaticFields(psiType, map, processingInfo);
                    if (result != null) {
                        return result;
                    }
                    return map;
                }
            }
        }
    }

    private static boolean isIgnoreForValue(@NotNull String qualifiedName) {
        return qualifiedName.startsWith("java.") || qualifiedName.startsWith("javax.");
    }

    private static boolean isIgnoreForValue(@NotNull PsiClass psiClass) {
        // 泛型的类型的不能忽略
        if (psiClass instanceof PsiTypeParameter) {
            return false;
        }
        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null) {
            return true;
        }

        if (isIgnoreForValue(qualifiedName)) {
            return true;
        }

        return false;
    }

    private static boolean isIgnoreForKey(PsiField psiField) {
        PsiModifierList modifierList = psiField.getModifierList();
        if (modifierList != null && modifierList.hasExplicitModifier(PsiModifier.STATIC)) {
            return true;
        }
        return false;
    }

    private static boolean isGenericType(PsiType psiType) {
        if (PsiUtil.resolveClassInClassTypeOnly(psiType) instanceof PsiTypeParameter) {
            return true;
        }

        ClassResolveResult classResolveResult = PsiUtil.resolveGenericsClassInType(psiType);
        Map<PsiTypeParameter, PsiType> substitutionMap = classResolveResult.getSubstitutor().getSubstitutionMap();
        if (substitutionMap.isEmpty()) {
            return false;
        }

        for (PsiType type : substitutionMap.values()) {
            if (isGenericType(type)) {
                return true;
            }
        }

        return false;
    }

    private static PsiType processGenericType(PsiField psiField, PsiType classType) {
        PsiType fieldType = psiField.getType();
        PsiElement context = psiField.getContext();
        if (fieldType instanceof PsiClassType && classType instanceof PsiClassType && context != null) {
            ClassResolveResult fieldClassResolveResult = ((PsiClassType) fieldType).resolveGenerics();

            ClassResolveResult classResolveResult = ((PsiClassType) classType).resolveGenerics();
            PsiSubstitutor realTypePsiSubstitutor = classResolveResult.getSubstitutor();

            if (isGenericType(fieldType)) {
                PsiClass fieldClass = PsiUtil.resolveClassInClassTypeOnly(fieldType);
                if (fieldClass instanceof PsiTypeParameter) {
                    // fieldType就是泛型T
                    return realTypePsiSubstitutor.substitute((PsiTypeParameter) fieldClass);
                }

                // 使用实际类型替换泛型类型
                JavaPsiFacade facade = JavaPsiFacade.getInstance(context.getProject());
                PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
                if (fieldClass != null) {
                    for (PsiTypeParameter psiTypeParameter : fieldClass.getTypeParameters()) {
                        PsiType realType;
                        // List<E>的E转换为PageList<T>中data字段的List<T>的T
                        // psiTypeParameter为字段类型中的泛型声明，通过此方法获取到在本类中使用的泛型
                        PsiType psiType = fieldClassResolveResult.getSubstitutor().substitute(psiTypeParameter);
                        if (isGenericType(psiType)) {
                            // 获取本类中泛型声明的T的真实类型：PageList<T> 的T 变成真实类型
                            realType = realTypePsiSubstitutor.substitute(psiType);
                        } else {
                            realType = psiType;
                        }
                        // 建立字段类型中泛型声明的E 到真实类型的映射
                        substitutor = substitutor.put(psiTypeParameter, realType);
                    }
                    return facade.getElementFactory().createType(fieldClass, substitutor, PsiUtil.getLanguageLevel(context));
                }
            }
        }

        return fieldType;
    }

    private static String listAllMyNonStaticFields(@NotNull PsiType psiType, Map<String, Object> map, ProcessingInfo processingInfo) {
        String className = getClassName(psiType);
        // 要放在try/finally外面
        if (processingInfo.isListingFields(psiType)) {
            // 防止递归依赖
            return "Recursion(" + className + ")...";
        }

        processingInfo.checkOverflow();

        try {
            processingInfo.increase();
            processingInfo.startListFields(psiType);

            PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(psiType);
            if (psiClass == null) {
                return null;
            }
            if (isIgnoreForValue(psiClass)) {
                return null;
            }
            for (PsiField psiField : psiClass.getFields()) {
                if (isIgnoreForKey(psiField)) {
                    continue;
                }
                PsiType finalType = processGenericType(psiField, psiType);
                Object value;
                if (finalType == null) {
                    value = "null(rawType)(" + className + ":" + psiField.getType().getPresentableText() + ")";
                } else {
                    value = resolveType(finalType, processingInfo);
                }
                map.put(psiField.getName(), value);
            }
            JvmReferenceType superClassType = psiClass.getSuperClassType();
            if (superClassType instanceof PsiClassType) {
                return listAllMyNonStaticFields((PsiClassType) superClassType, map, processingInfo);
            }
            return null;
        } finally {
            processingInfo.decrease();
            processingInfo.finishListFields();
        }
    }

    private static Object getDefaultValue(PsiType psiType) {
        if (psiType instanceof PsiPrimitiveType) {
            return getDefaultValue(psiType.getCanonicalText());
        }
        if (psiType instanceof PsiClassType) {
            PsiClass psiClass = ((PsiClassType) psiType).resolve();

            if (psiClass != null) {
                String qualifiedName = psiClass.getQualifiedName();
                String prefix = "java.lang.";
                if (qualifiedName != null && qualifiedName.startsWith(prefix)) {
                    return getDefaultValue(qualifiedName.substring(prefix.length()));
                }
            }
        }
        return null;
    }

    /**
     * 获取类型的默认值
     * @param typeName
     * @return
     */
    private static Object getDefaultValue(@NotNull String typeName) {
        typeName = typeName.toLowerCase();
        switch (typeName) {
            case "boolean":
                return false;
            case "byte":
                return (byte) 0;
            case "character": // 兼容包装类型
            case "char":
                return 'C';
            case "short":
                return (short) 0;
            case "int":
                return 0;
            case "long":
                return 0L;
            case "float":
                return zero;
            case "double":
                return zero;
            default:
                return null;
        }
    }

    static String myFormat(String json) throws IOException {
        StringBuilder builder = new StringBuilder();
        LineReader lineReader = new LineReader(new StringReader(json));
        String line = null;
        while ((line = lineReader.readLine()) != null) {
            StringBuilder spaceBuilder = new StringBuilder();
            for (char c : line.toCharArray()) {
                if (c == ' ') {
                    spaceBuilder.append(c);
                } else {
                    break;
                }
            }
            if (spaceBuilder.length() > 0) {
                builder.append(spaceBuilder);
            }
            builder.append(line).append(System.lineSeparator());
        }
        return builder.toString();
    }

}

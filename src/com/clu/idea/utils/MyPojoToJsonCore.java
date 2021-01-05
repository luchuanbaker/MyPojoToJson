package com.clu.idea.utils;

import com.google.common.io.LineReader;
import com.intellij.lang.jvm.types.JvmReferenceType;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.PsiClassType.ClassResolveResult;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
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

    private static void getAllTypeNames(PsiType psiType, Set<String> psiTypeNames) {
        processAllTypes(psiType, new Processor<PsiType>() {
            @Override
            public boolean process(PsiType psiType) {
                psiTypeNames.add(psiType.getPresentableText());
                return true;
            }
        });
    }

    private static void processAllTypes(PsiType psiType, Processor<PsiType> processor) {
        if (psiType == null) {
            return;
        }
        if (!processor.process(psiType)) {
            return;
        }

        for (PsiType subPsiType : psiType.getSuperTypes()) {
            processAllTypes(subPsiType, processor);
        }
    }

    static Object resolveType(@NotNull PsiType psiType, @NotNull ProcessingInfo processingInfo) {
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
                Set<String> fieldTypeNames = new LinkedHashSet<>();
                getAllTypeNames(psiType, fieldTypeNames);
                List<String> retain = new ArrayList<>(fieldTypeNames);
                /*取交集，常见类型的默认值*/
                retain.retainAll(normalTypes.keySet());
                if (!retain.isEmpty()) {
                    String typeName = retain.get(0);
                    return normalTypes.get(typeName);
                } else {
                    // java.lang.Iterable
                    PsiClassType iterableType = PsiType.getTypeByName(CommonClassNames.JAVA_LANG_ITERABLE, processingInfo.getProject(), GlobalSearchScope.allScope(processingInfo.getProject()));
                    if (iterableType.isAssignableFrom(psiType)) {
                        List<Object> list = new ArrayList<>();
                        PsiType deepType = PsiUtil.extractIterableTypeParameter(psiType, false);
                        if (deepType != null) {
                            list.add(resolveType(deepType, processingInfo));
                        }
                        return list;
                    }

                    // java.util.Map
                    PsiClassType mapType = PsiType.getTypeByName(CommonClassNames.JAVA_UTIL_MAP, processingInfo.getProject(), GlobalSearchScope.allScope(processingInfo.getProject()));
                    if (mapType.isAssignableFrom(psiType)) {
                        // java.util.Map
                        PsiClass mapClass = mapType.resolve();
                        processAllTypes(psiType, new Processor<PsiType>() {
                            @Override
                            public boolean process(PsiType psiType) {
                                ClassResolveResult classResolveResult = PsiUtil.resolveGenericsClassInType(psiType);
                                if (Objects.requireNonNull(mapClass).equals(classResolveResult.getElement())) {
                                    Object key = null;
                                    Object value = null;
                                    PsiType keyRealType = null;
                                    for (Map.Entry<PsiTypeParameter, PsiType> entry : classResolveResult.getSubstitutor().getSubstitutionMap().entrySet()) {
                                        PsiType realType = entry.getValue();
                                        String name = entry.getKey().getName();
                                        if ("K".equals(name)) {
                                            if (realType == null) {
                                                key = "(rawType)";
                                            } else {
                                                // key不能使用类型的默认值，使用类型值
                                                key = "{" + realType.getPresentableText() + "}";
                                                keyRealType = realType;
                                            }
                                        } else if ("V".equals(name)) {
                                            if (realType == null) {
                                                value = "(rawType)";
                                            } else {
                                                value = resolveType(realType, processingInfo);
                                            }
                                        }
                                    }
                                    if (key != null) {
                                        map.put(String.valueOf(key), value);
                                        if (keyRealType != null) {
                                            Object resolvedKey = resolveType(keyRealType, processingInfo);
                                            if (resolvedKey instanceof Map && !((Map) resolvedKey).isEmpty()) {
                                                // 使用额外的属性记录key的数据结构
                                                map.put("__key__", resolvedKey);
                                            }
                                        }
                                    }
                                    return false;
                                }
                                return true;
                            }
                        });
                        return map;
                    }

                    // result可能是防止递归的字符串
                    String result = listAllMyNonStaticFields(psiType, map, processingInfo); // 属性解析递归
                    return Optional.<Object>ofNullable(result/*递归文字*/).orElse(map);
                }
            }
        }
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

    private static PsiType getFieldRealType(PsiType fieldType, PsiType classType, PsiElement context) {
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

    private static PsiType processGenericType(PsiField psiField, PsiType classType) {
        PsiType fieldType = psiField.getType();
        PsiElement context = psiField.getContext();

        boolean isArray = false;
        int arrayDim = 0;
        PsiType psiType = fieldType;
        while (psiType instanceof PsiArrayType) {
            isArray = true;
            psiType = ((PsiArrayType) psiType).getComponentType();
            arrayDim++;
        }

        PsiType realType = getFieldRealType(psiType, classType, context);
        if (isArray && realType != null) {
            realType = PsiTypesUtil.createArrayType(realType, arrayDim);
        }
        return realType;
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
            // 模糊的
            PsiClass superPsiClass = psiClass.getSuperClass();
            PsiType superClassType = null;
            // 尝试精细化查找
            for (PsiType superType : psiType.getSuperTypes()) {
                if (superType instanceof PsiClassType) {
                    PsiClass genericTypePsiClass = PsiUtil.resolveClassInClassTypeOnly(superType);
                    if (genericTypePsiClass != null && superPsiClass != null && Objects.requireNonNull(superPsiClass.getQualifiedName()).equals(genericTypePsiClass.getQualifiedName())) {
                        superClassType = superType;
                        break;
                    }
                }
            }
            if (superClassType == null) {
                JvmReferenceType rawSuperClassType = psiClass.getSuperClassType();
                if (rawSuperClassType instanceof PsiType) {
                    superClassType = (PsiType) rawSuperClassType;
                }
            }

            if (superClassType instanceof PsiClassType) {
                return listAllMyNonStaticFields(superClassType, map, processingInfo); // 父类递归
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

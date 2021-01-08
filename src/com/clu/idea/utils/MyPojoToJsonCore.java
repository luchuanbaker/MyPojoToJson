package com.clu.idea.utils;

import com.clu.idea.MyPluginException;
import com.google.common.io.LineReader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.lang.jvm.types.JvmReferenceType;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.PsiClassType.ClassResolveResult;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.javadoc.PsiDocComment;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.clu.idea.utils.ProcessingInfo.CheckProcessingType.MAX_DEPTH;
import static com.clu.idea.utils.ProcessingInfo.CheckProcessingType.PROCESSING;

public class MyPojoToJsonCore {

    @NonNls
    private static final ConcurrentMap<String, Object> normalTypeNameValues = new ConcurrentHashMap<>();

    /**
     * 特殊类型带默认值
     */
    @NonNls
    private static final ConcurrentMap<String, PsiClassType> normalTypeOfName = new ConcurrentHashMap<>();

    private static final String JAVA_DOC_KEY = "----JAVA_DOC----";

    public static final Gson GSON = (new GsonBuilder()).serializeNulls().setPrettyPrinting().create();

    private static final BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

    static {
        Supplier<LocalDateTime> now = LocalDateTime::now;
        Supplier<String> dateTime = () -> now.get().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        normalTypeNameValues.put(CommonClassNames.JAVA_LANG_BOOLEAN, false);
        normalTypeNameValues.put(CommonClassNames.JAVA_LANG_FLOAT, zero);
        normalTypeNameValues.put(CommonClassNames.JAVA_LANG_DOUBLE, zero);
        normalTypeNameValues.put("java.math.BigDecimal", zero);
        normalTypeNameValues.put(CommonClassNames.JAVA_LANG_NUMBER, 0);
        normalTypeNameValues.put("java.lang.CharSequence", "");
        normalTypeNameValues.put(CommonClassNames.JAVA_UTIL_DATE, dateTime);
        normalTypeNameValues.put("java.time.temporal.Temporal", (Supplier<Long>) () -> now.get().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        normalTypeNameValues.put("java.time.LocalDateTime", dateTime);
        normalTypeNameValues.put("java.time.LocalDate", (Supplier<String>) () -> now.get().toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        normalTypeNameValues.put("java.time.LocalTime", (Supplier<String>) () -> now.get().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        normalTypeNameValues.put(CommonClassNames.JAVA_IO_FILE, "{File}");
        normalTypeNameValues.put("java.net.SocketAddress", "{SocketAddress}");
        normalTypeNameValues.put("java.net.InetAddress", "{InetAddress}");
        normalTypeNameValues.put("java.util.TimeZone", "{TimeZone}");
        normalTypeNameValues.put("java.util.concurrent.atomic.AtomicBoolean", false);
        normalTypeNameValues.put(CommonClassNames.JAVA_LANG_CLASS, "{Class}");
        normalTypeNameValues.put("java.nio.file.Path", "{Path}");
        normalTypeNameValues.put("java.lang.Thread", "{Thread}");
        normalTypeNameValues.put(CommonClassNames.JAVA_LANG_THROWABLE, "{Throwable}");
    }

    private static Object getNormalTypeValue(PsiType psiType, Project project) {
        if (!(psiType instanceof PsiClassType)) {
            return null;
        }

        for (Map.Entry<String, Object> entry : normalTypeNameValues.entrySet()) {
            String typeClassName = entry.getKey();
            PsiClassType psiClassType = normalTypeOfName.computeIfAbsent(typeClassName, new Function<String, PsiClassType>() {
                @Override
                public PsiClassType apply(String className) {
                    return PsiType.getTypeByName(className, project, GlobalSearchScope.allScope(project));
                }
            });
            if (psiClassType.isAssignableFrom(psiType)) {
                Object value = entry.getValue();
                if (value instanceof Supplier) {
                    return ((Supplier) value).get();
                }
                return value;
            }
        }
        return null;
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

        PsiClass psiClass = null;

        PsiQualifiedReferenceElement psiQualifiedReferenceElement = PsiTreeUtil.getContextOfType(psiElement, PsiQualifiedReferenceElement.class);
        if (psiQualifiedReferenceElement != null) {
            PsiElement psiJavaElement = psiQualifiedReferenceElement.resolve();
            if (psiJavaElement instanceof PsiClass) {
                psiClass = (PsiClass) psiJavaElement;
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
        if (psiClass == null) {
            PsiMethod selectedMethod = PsiTreeUtil.getContextOfType(psiElement, PsiMethod.class);
            if (selectedMethod != null) {
                if (selectedMethod.isConstructor()) {
                    psiClass = selectedMethod.getContainingClass();
                }
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

    @NotNull
    static String getClassName(PsiType psiType) {
        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(psiType);
        return Optional.ofNullable(psiClass)
            .map(PsiClass::getName)
            .orElse(psiType.getPresentableText());
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
        String className = getClassName(psiType);
        // 要放在try/finally外面
        ProcessingInfo.CheckProcessingType checkProcessingType = processingInfo.checkProcessingType(psiType);
        if (checkProcessingType == PROCESSING) {
            // 防止递归依赖
            return "Recursion(" + className + ")...";
        }
        if (checkProcessingType == MAX_DEPTH) {
            // 防止过深
            return "MaxDepth(" + className + ")...";
        }

        processingInfo.updateProgress(psiType); // resolveType
        processingInfo.checkOverflowAndCanceled();

        try {
            processingInfo.increase();
            processingInfo.startProcessType(psiType);

            Object primitiveTypeDefaultValue = getDefaultValue(psiType, processingInfo.getProject());
            if (primitiveTypeDefaultValue != null) {
                return primitiveTypeDefaultValue;
            }

            PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(psiType);

            if (psiClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName())) {
                return Collections.emptyMap();
            }

            if (psiType instanceof PsiArrayType) {
                List<Object> list = new ArrayList<>();
                processingInfo.setResultIfAbsent(list);
                PsiType deepType = psiType.getDeepComponentType();
                list.add(resolveType(deepType, processingInfo)); // PsiArrayType
                return list;
            } else {
                Map<String, Object> map = new LinkedHashMap<>();
                processingInfo.setResultIfAbsent(map);
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
                    // java.lang.Iterable
                    PsiClassType iterableType = PsiType.getTypeByName(CommonClassNames.JAVA_LANG_ITERABLE, processingInfo.getProject(), GlobalSearchScope.allScope(processingInfo.getProject()));
                    if (iterableType.isAssignableFrom(psiType)) {
                        List<Object> list = new ArrayList<>();
                        processingInfo.setResultIfAbsent(list);
                        PsiType deepType = PsiUtil.extractIterableTypeParameter(psiType, false);
                        if (deepType != null) {
                            list.add(resolveType(deepType, processingInfo)); // iterableType
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
                                                value = resolveType(realType, processingInfo); // V of Map
                                            }
                                        }
                                    }
                                    if (key != null) {
                                        map.put(String.valueOf(key), value);
                                        if (keyRealType != null) {
                                            Object resolvedKey = resolveType(keyRealType, processingInfo); // __key__ of Map
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

                    // interface
                    if (psiClass.isInterface()) {
                        return "{}";
                    }

                    // result可能是防止递归的字符串
                    String result = listAllMyNonStaticFields(psiType, map, processingInfo); // 属性解析递归
                    return Optional.<Object>ofNullable(result/*递归文字*/).orElse(map);
                }
            }
        } finally {
            processingInfo.decrease();
            processingInfo.finishProcessType();
        }
    }

    private static boolean isIgnoreForKey(PsiField psiField) {
        PsiModifierList modifierList = psiField.getModifierList();
        if (modifierList != null && (modifierList.hasExplicitModifier(PsiModifier.STATIC) || modifierList.hasExplicitModifier(PsiModifier.TRANSIENT))) {
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

    private static String getJavadoc(PsiField psiField) {
        PsiDocComment psiDocComment = PsiTreeUtil.getChildOfType(psiField, PsiDocComment.class);
        if (psiDocComment != null) {
            String text = psiDocComment.getText();
            if (text == null) {
                return null;
            }
            text = text.trim();
            if (text.isEmpty()) {
                return null;
            }
            return text;
        }
        return null;
    }

    private static String listAllMyNonStaticFields(@NotNull PsiType psiType, Map<String, Object> map, ProcessingInfo processingInfo) {
        String className = getClassName(psiType);

        processingInfo.updateProgress(psiType); // listAllMyNonStaticFields

        processingInfo.checkOverflowAndCanceled();

        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(psiType);
        if (psiClass == null) {
            return null;
        }

        // add support for Android Application
        if (psiClass instanceof ClsClassImpl) {
            PsiClass sourceMirrorClass = ((ClsClassImpl) psiClass).getSourceMirrorClass();
            if (sourceMirrorClass != null) {
                psiClass = sourceMirrorClass;
            }
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
                value = resolveType(finalType, processingInfo); // listAllMyNonStaticFields
            }
            String javadoc = getJavadoc(psiField);
            String fieldName = psiField.getName();
            if (javadoc != null) {
                map.put(JAVA_DOC_KEY + "-" + fieldName, javadoc);
            }
            map.put(fieldName, value);
        }

        // 模糊的
        PsiClass superPsiClass = psiClass.getSuperClass();
        PsiType superClassType = null;
        if (superPsiClass != null) {
            // 尝试精细化查找(getSuperClass()方法会丢失泛型信息，getSuperTypes()会保留)
            for (PsiType superType : psiType.getSuperTypes()) {
                if (superType instanceof PsiClassType) {
                    PsiClass genericTypePsiClass = PsiUtil.resolveClassInClassTypeOnly(superType);
                    if (genericTypePsiClass != null && Objects.requireNonNull(superPsiClass.getQualifiedName()).equals(genericTypePsiClass.getQualifiedName())) {
                        superClassType = superType;
                        break;
                    }
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
    }

    private static Object getDefaultValue(PsiType psiType, Project project) {
        // 基本类型
        if (psiType instanceof PsiPrimitiveType) {
            return getPrimitiveTypeDefaultValue(psiType.getCanonicalText());
        }
        // 包装类型
        if (psiType instanceof PsiClassType) {
            PsiClass psiClass = ((PsiClassType) psiType).resolve();

            if (psiClass != null) {
                String qualifiedName = psiClass.getQualifiedName();
                // 包装类的包名前缀
                String prefix = "java.lang.";
                if (qualifiedName != null && qualifiedName.startsWith(prefix)) {
                    Object value = getPrimitiveTypeDefaultValue(qualifiedName.substring(prefix.length()));
                    if (value != null) {
                        return value;
                    }
                }
            }
        }
        // 特殊类型
        Object normalTypeValue = getNormalTypeValue(psiType, project);
        if (normalTypeValue != null) {
            return normalTypeValue;
        }
        return null;
    }

    /**
     * 获取类型的默认值
     * @param typeName
     * @return
     */
    private static Object getPrimitiveTypeDefaultValue(@NotNull String typeName) {
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

    @NotNull
    private static String formatJavadoc(@NotNull String javadoc) {
        javadoc = javadoc.substring("/**".length());
        javadoc = javadoc.substring(0, javadoc.length() - "*/".length());
        LineReader lineReader = new LineReader(new StringReader(javadoc));
        StringBuilder builder = new StringBuilder();
        String line;
        try {
            while ((line = lineReader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("*")) {
                    line = line.substring(1).trim();
                }
                builder.append(line);
            }
        } catch (IOException e) {
            throw new MyPluginException("get javadoc fail", e);
        }
        return builder.toString();
    }

    static String myFormat(String json) throws IOException {
        StringBuilder builder = new StringBuilder();
        LineReader lineReader = new LineReader(new StringReader(json));
        String line = lineReader.readLine();
        do {
            String trimLine = line.trim();
            String javadoc = null;
            if (trimLine.startsWith("\""+ JAVA_DOC_KEY)) {
                if (trimLine.endsWith(",")) {
                    trimLine = trimLine.substring(0, trimLine.length() - 1);
                }
                Map<String, String> javadocMap = GSON.fromJson("{" + trimLine + "}", new TypeToken<Map<String, String>>() {
                }.getType());
                String _javadoc;
                Iterator<String> iterator;
                if ((iterator = javadocMap.values().iterator()).hasNext() && (_javadoc = iterator.next()) != null) {
                    javadoc = formatJavadoc(_javadoc);
                }
                line = lineReader.readLine();
                if (line == null) {
                    // javadoc居然是最后一行，丢弃javadoc
                    break;
                }
            }

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
            builder.append(line);

            if (javadoc != null) {
                builder.append("  // ").append(javadoc);
            }

            // 换行符
            line = lineReader.readLine();
            if (line != null) {
                builder.append(System.lineSeparator());
            }
        } while (line != null);
        return builder.toString();
    }

}

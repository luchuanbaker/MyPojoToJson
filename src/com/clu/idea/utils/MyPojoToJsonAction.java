package com.clu.idea.utils;

import com.clu.idea.MyPluginException;
import com.google.common.io.LineReader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.lang.jvm.types.JvmReferenceType;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications.Bus;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.PsiClassType.ClassResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class MyPojoToJsonAction extends AnAction {

    private static final Logger logger = Logger.getInstance(MyPojoToJsonAction.class);

    private static final NotificationGroup notifyGroup = new NotificationGroup("myPojoToJson.NotificationGroup", NotificationDisplayType.BALLOON, true);
    @NonNls
    private static final Map<String, Object> normalTypes = new HashMap<>();
    // private static final GsonBuilder gsonBuilder = (new GsonBuilder()).setPrettyPrinting();
    private static final Gson GSON = (new GsonBuilder()).serializeNulls().setPrettyPrinting().create();
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

    private PsiClass checkAndGetPsiClass(PsiFile psiFile, PsiElement psiElement) {
        if (!(psiFile instanceof PsiJavaFile)) {
            return null;
        }

        if (!(psiElement instanceof PsiClass)) {
            return null;
        }
        return (PsiClass) psiElement;
    }

    @Override
    public void update(AnActionEvent e) {
        DataContext dataContext = e.getDataContext();
        PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);
        // psiElement获取到的，直接是PsiJavaCodeReferenceElement关联的类类型
        PsiElement psiElement = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
        PsiClass psiClass = checkAndGetPsiClass(psiFile, psiElement);
        if (psiClass == null) {
            e.getPresentation().setEnabled(false);
        }
    }

    private Project project;

    @Override
    public void actionPerformed(AnActionEvent e) {
        DataContext dataContext = e.getDataContext();
        Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);
        // psiElement获取到的，直接是PsiJavaCodeReferenceElement关联的类类型
        PsiElement psiElement = CommonDataKeys.PSI_ELEMENT.getData(dataContext);

        this.project = e.getProject();
        if (editor == null || psiFile == null || project == null) {
            return;
        }

        PsiClass psiClass = checkAndGetPsiClass(psiFile, psiElement);
        if (psiClass == null) {
            e.getPresentation().setEnabled(false);
            return;
        }

        // 找到当前光标的类型，不能直接获取PsiClass，因为泛型信息会丢失
        PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
        if (element == null) {
            Bus.notify(notifyGroup.createNotification("SelectedPsiElement is null", NotificationType.INFORMATION), project);
            return;
        }

        MyClassInfo classInfo = null;

        // 类型，方法返回值，变量类型等
        PsiTypeElement selectedTypeElement = PsiTreeUtil.getContextOfType(element, PsiTypeElement.class);
        if (selectedTypeElement != null) {
            PsiType selectType = selectedTypeElement.getType();
            if (selectType instanceof PsiClassType) {
                classInfo = new MyClassInfo((PsiClassType) selectType);
            }
        }

        // new
        if (classInfo == null) {
            PsiNewExpression selectedNewExpression = PsiTreeUtil.getContextOfType(element, PsiNewExpression.class);
            if (selectedNewExpression != null) {
                PsiType selectType = selectedNewExpression.getType();
                if (selectType instanceof PsiClassType) {
                    classInfo = new MyClassInfo((PsiClassType) selectType);
                }
            }
        }

        if (classInfo == null) {
            // 类声明
            /*PsiClass selectedClass = PsiTreeUtil.getContextOfType(element, PsiClass.class);
            if (selectedClass != null) {
                classInfo = new MyGenericInfo(selectedClass);
            }*/
            PsiClassType psiClassType = PsiTypesUtil.getClassType(psiClass);
            classInfo = new MyClassInfo(psiClassType);
        }

        Object result = resolveType(classInfo.getPsiClassType(), classInfo, new ProcessingInfo());
        String json = GSON.toJson(result);
        try {
            json = myFormat(json);
        } catch (IOException ex) {
            throw new MyPluginException("Error", ex);
        }
        StringSelection selection = new StringSelection(json);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, selection);
        String message = "Convert " + classInfo.getPsiClass().getName() + " to JSON success, copied to clipboard.";
        Notification success = notifyGroup.createNotification(message, NotificationType.INFORMATION);
        Bus.notify(success, project);

//        try {
//
//        } catch (MyPluginException exception) {
//            Bus.notify(notifyGroup.createNotification(exception.getMessage(), NotificationType.WARNING), project);
//        } catch (Exception var14) {
//            Bus.notify(notifyGroup.createNotification("Convert to JSON failed.", NotificationType.ERROR), project);
//        }
    }

    private Object resolveType(@NotNull PsiType psiType, @Nullable MyClassInfo myClassInfo, @NotNull ProcessingInfo processingInfo) {
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
            MyClassInfo myClassInfo2 = new MyClassInfo((PsiClassType) deepType);
            list.add(resolveType(deepType, myClassInfo2, processingInfo));
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
                    // deepType 可能会是 PsiArrayType: Response<SimpleUserInfoVo>[]
                    // MyGenericInfo myGenericInfo2 = deepType instanceof PsiClassType ? new MyGenericInfo((PsiClassType) deepType, myGenericInfo) : null;
                    MyClassInfo myClassInfo2 = null;
                    if (deepType != null) {
                        // 情况：deepType是PageList中的data: List<T>的T，myGenericInfo类型是PsiType:PageList<SearchGroupResultItemVo>
                        if (deepType instanceof PsiClassType) {
                            myClassInfo2 = new MyClassInfo((PsiClassType) deepType);
                        } else {
                            // PsiArrayType: Response<SimpleUserInfoVo>[]
                            myClassInfo2 = new MyClassInfo(deepType);
                        }
                        list.add(resolveType(deepType, myClassInfo2, processingInfo));
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

                    String result = listAllMyNonStatusFields(psiType, myClassInfo, map, processingInfo);
                    if (result != null) {
                        return result;
                    }
                    return map;
                }
            }
        }
    }

    private boolean isIgnoreForValue(@NotNull String qualifiedName) {
        return qualifiedName.startsWith("java.") || qualifiedName.startsWith("javax.");
    }

    private boolean isIgnoreForValue(@NotNull PsiClass psiClass) {
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

    private boolean isIgnoreForKey(PsiField psiField) {
        PsiModifierList modifierList = psiField.getModifierList();
        if (modifierList != null && modifierList.hasExplicitModifier(PsiModifier.STATIC)) {
            return true;
        }
        return false;
    }

    private boolean isGenericType(PsiType psiType) {
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

    private PsiType processGenericType(PsiField psiField, PsiType classType) {
        PsiType fieldType = psiField.getType();
        PsiElement context = psiField.getContext();
        if (fieldType instanceof PsiClassType && classType instanceof PsiClassType && context != null) {
            PsiClassType.ClassResolveResult fieldClassResolveResult = ((PsiClassType) fieldType).resolveGenerics();

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
                        // List<E>的E转换为PageList<T>中data字段的List<T>的T
                        // psiTypeParameter为字段类型中的泛型声明，通过此方法获取到在本类中使用的泛型
                        PsiType substitute = fieldClassResolveResult.getSubstitutor().substitute(psiTypeParameter);
                        // 获取本类中泛型声明的T的真实类型：PageList<T> 的T 变成真实类型
                        PsiType realType = realTypePsiSubstitutor.substitute(substitute);
                        // 建立字段类型中泛型声明的E 到真实类型的映射
                        substitutor = substitutor.put(psiTypeParameter, realType);
                    }
                    return facade.getElementFactory().createType(fieldClass, substitutor, PsiUtil.getLanguageLevel(context));
                }
            }
        }

        return fieldType;
    }

    private String listAllMyNonStatusFields(@NotNull PsiType psiType, MyClassInfo myClassInfo, Map<String, Object> map, ProcessingInfo processingInfo) {
        // 要放在try/finally外面
        if (processingInfo.isListingFields(psiType)) {
            // 防止递归依赖
            String className = psiType.getPresentableText();
            if (myClassInfo != null && myClassInfo.getPsiClassType() != null) {
                className = myClassInfo.getPsiClassType().getClassName();
            }
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
                    value = "null(rawType)(" + myClassInfo.getPsiClassType().getName() + ":" + psiField.getType().getPresentableText() + ")";
                } else {
                    value = resolveType(finalType, myClassInfo, processingInfo);
                }
                map.put(psiField.getName(), value);
            }
            JvmReferenceType superClassType = psiClass.getSuperClassType();
            if (superClassType instanceof PsiClassType) {
                MyClassInfo myClassInfo2 = new MyClassInfo((PsiClassType) superClassType);
                return listAllMyNonStatusFields((PsiClassType) superClassType, myClassInfo2, map, processingInfo);
            }
            return null;
        } finally {
            processingInfo.decrease();
            processingInfo.finishListFields();
        }
    }

    private Object getDefaultValue(PsiType psiType) {
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
    private Object getDefaultValue(@NotNull String typeName) {
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

    private String myFormat(String json) throws IOException {
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

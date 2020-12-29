package com.clu.idea.utils;

import com.clu.idea.MyPluginException;
import com.google.common.io.LineReader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications.Bus;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
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

    private static final NotificationGroup notifyGroup = new NotificationGroup("myPojoToJson.NotificationGroup", NotificationDisplayType.BALLOON, true);
    @NonNls
    private static final Map<String, Object> normalTypes = new HashMap<>();
    // private static final GsonBuilder gsonBuilder = (new GsonBuilder()).setPrettyPrinting();
    private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
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

    @Override
    public void actionPerformed(AnActionEvent e) {
        DataContext dataContext = e.getDataContext();
        Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);
        // psiElement获取到的，直接是PsiJavaCodeReferenceElement关联的类类型
        PsiElement psiElement = CommonDataKeys.PSI_ELEMENT.getData(dataContext);

        Project project = e.getProject();
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

        MyGenericInfo classInfo = null;

        // 类型，方法返回值，变量类型等
        PsiTypeElement selectedTypeElement = PsiTreeUtil.getContextOfType(element, PsiTypeElement.class);
        if (selectedTypeElement != null) {
            PsiType selectType = selectedTypeElement.getType();
            if (selectType instanceof PsiClassType) {
                classInfo = new MyGenericInfo((PsiClassType) selectType);
            }
        }

        // new
        if (classInfo == null) {
            PsiNewExpression selectedNewExpression = PsiTreeUtil.getContextOfType(element, PsiNewExpression.class);
            if (selectedNewExpression != null) {
                PsiType selectType = selectedNewExpression.getType();
                if (selectType instanceof PsiClassType) {
                    classInfo = new MyGenericInfo((PsiClassType) selectType);
                }
            }
        }


        // 类声明
        if (classInfo == null) {
            /*PsiClass selectedClass = PsiTreeUtil.getContextOfType(element, PsiClass.class);
            if (selectedClass != null) {
                classInfo = new MyGenericInfo(selectedClass);
            }*/
            // 转换为纯类转JSON，抛弃泛型信息
            classInfo = new MyGenericInfo(psiClass);
        }

        // List或者Map
        Object result = getTypeStruct(classInfo);
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

    private static List<String> getAllTypeNames(PsiType psiType) {
        List<String> fieldTypeNames = new ArrayList<>();
        PsiType[] superTypes = psiType.getSuperTypes();
        fieldTypeNames.add(psiType.getPresentableText());
        fieldTypeNames.addAll(Arrays.stream(superTypes).map(PsiType::getPresentableText).collect(Collectors.toList()));
        return fieldTypeNames;
    }

    /**
     * list格式的数组
     * @param psiType
     * @param fieldTypeNames
     * @return
     */
    private static List<Object> tryGetListTypeStruct(PsiType psiType, List<String> fieldTypeNames) {
        if (fieldTypeNames.stream().anyMatch((s) -> s.startsWith("Collection") || s.startsWith("Iterable"))) {
            ArrayList<Object> list = new ArrayList<>();
            PsiType deepType = PsiUtil.extractIterableTypeParameter(psiType, false);
            // deepType 可能会是 PsiArrayType: Response<SimpleUserInfoVo>[]
            MyGenericInfo myGenericInfo2 = deepType instanceof PsiClassType ? new MyGenericInfo((PsiClassType) deepType) : null;
            list.add(typeResolve(deepType, myGenericInfo2, 0));
            return list;
        }
        return null;
    }

    private static Object/*Map<String, Object>*/ getTypeStruct(MyGenericInfo myGenericInfo) {
        Map<String, Object> map = new LinkedHashMap<>();
        PsiClass psiClass = myGenericInfo.getPsiClass();
        PsiClassType psiClassType = myGenericInfo.getPsiClassType();
        if (psiClass == null) {
            return map;
        } else {
            if (psiClassType != null) {
                // 直接就是List<Bean>格式
                List<String> fieldTypeNames = getAllTypeNames(psiClassType);
                List<Object> list = tryGetListTypeStruct(psiClassType, fieldTypeNames);
                if (list != null) {
                    return list;
                }
            }

            // 普通bean属性遍历
            for (PsiField field : psiClass.getAllFields()) {
                map.put(field.getName(), typeResolve(field.getType(), myGenericInfo, 0));
            }
            return map;
        }
    }

    private static Object typeResolve(PsiType psiType, @Nullable MyGenericInfo myGenericInfo, int level) {
        ++level;
        Object primitiveTypeDefaultValue = getDefaultValue(psiType);
        if (primitiveTypeDefaultValue != null) {
            return primitiveTypeDefaultValue;
        }
        if (psiType instanceof PsiArrayType) {
            List<Object> list = new ArrayList<>();
            PsiType deepType = psiType.getDeepComponentType();
            MyGenericInfo myGenericInfo2 = new MyGenericInfo((PsiClassType) deepType);
            list.add(typeResolve(deepType, myGenericInfo2, level));
            return list;
        } else {
            Map<String, Object> map = new LinkedHashMap<>();
            PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(psiType);
            if (psiClass instanceof PsiTypeParameter/*表示当前字段的类型是一个泛型类型*/ && myGenericInfo != null) {
                // 当fieldPsiClass不为null，就表示fieldPsiType一定是PsiClassType类型，参考resolveClassInClassTypeOnly的源码
                // 当前属性psiType所隶属于的类
                PsiType realType = myGenericInfo.getRealType(psiType.getPresentableText());
                MyGenericInfo myGenericInfo2 = new MyGenericInfo((PsiClassType) realType);
                return typeResolve(realType, myGenericInfo2, level);
            }
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
                List<String> fieldTypeNames = getAllTypeNames(psiType);
                List<Object> list = tryGetListTypeStruct(psiType, fieldTypeNames);
                if (list != null) {
                    return list;
                }

                List<String> retain = new ArrayList<>(fieldTypeNames);
                /*取交集*/
                retain.retainAll(normalTypes.keySet());
                if (!retain.isEmpty()) {
                    String typeName = retain.get(0);
                    return normalTypes.get(typeName);
                } else if (level > 50) {
                    throw new MyPluginException("This class reference level exceeds maximum limit or has nested references!");
                } else {
                    MyGenericInfo myGenericInfo2 = psiType instanceof PsiClassType ? new MyGenericInfo((PsiClassType) psiType) : new MyGenericInfo(psiClass);
                    for (PsiField field : psiClass.getAllFields()) {
                        PsiType fieldType = field.getType();
                        map.put(field.getName(), typeResolve(fieldType, myGenericInfo2, level));
                    }
                    return map;
                }
            }
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
                return '\0';
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

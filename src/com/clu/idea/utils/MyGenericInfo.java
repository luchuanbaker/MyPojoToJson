package com.clu.idea.utils;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class MyGenericInfo {

    private ConcurrentMap<String, PsiType> genericInfoMap = new ConcurrentHashMap<>();

    private PsiClass psiClass;

    private PsiClassType psiClassType;

    private PsiType psiType;

    MyGenericInfo(@NotNull PsiType psiType) {
        this.psiType = psiType;
    }

    MyGenericInfo(@NotNull PsiClassType psiClassType) {
        this.psiClassType = psiClassType;
        // 引用模板类时的实际参数
        this.psiClass = psiClassType.resolve();
        parseGenerics(this.psiClassType, this.psiClass);
    }

    private void parseGenerics(@NotNull PsiClassType thisPsiClassType, PsiClass thisPsiClass) {
        Map<String, PsiType> mapping = getMapping(thisPsiClassType, thisPsiClass);
        if (mapping != null) {
            this.genericInfoMap.putAll(mapping);
        }
    }

    @Nullable
    private Map<String, PsiType> getMapping(@NotNull PsiClassType psiClassType, PsiClass psiClass) {
        if (psiClass != null) {
            // 实际泛型的参数
            PsiType[] parameters = psiClassType.getParameters();
            // 类的泛型参数列表
            PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
            if (typeParameters.length > 0) {
                int index = 0;
                Map<String, PsiType> genericTypeToRealTypeMap = new LinkedHashMap<>();
                for (PsiTypeParameter psiTypeParameter : typeParameters) {
                    PsiType realType;
                    if (index < parameters.length) {
                        realType = parameters[index];
                    } else {
                        // 使用了rawType
                        realType = MissingGenericType.INSTANCE;
                    }
                    genericTypeToRealTypeMap.put(psiTypeParameter.getName(), realType);
                    index++;
                }
                return genericTypeToRealTypeMap;
            }

        }
        return null;
    }



    /**
     * 获取泛型T代表的实际类型
     * @param typeParameterName
     * @return
     */
    @Nullable
    PsiType getRealType(String typeParameterName) {
//        Map<String, PsiType> mapping = this.genericInfoMap.get(containingClass);
//        if (mapping == null) {
//            return null;
//        }
//        return mapping.get(typeParameterName);
        return genericInfoMap.get(typeParameterName);
    }

    PsiClass getPsiClass() {
        return this.psiClass;
    }

    PsiClassType getPsiClassType() {
        return this.psiClassType;
    }

}

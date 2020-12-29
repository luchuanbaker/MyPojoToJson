package com.clu.idea.utils;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class MyGenericInfo {

    private ConcurrentMap<PsiClass, Map<String, PsiType>> genericInfoMap = new ConcurrentHashMap<>();

    private PsiClass psiClass;

    private PsiClassType psiClassType;

    MyGenericInfo(@NotNull PsiClass psiClass, @Nullable MyGenericInfo prevGenericInfo) {
        this.psiClass = psiClass;
        parseSuperTypes(this.psiClass, prevGenericInfo);
        mergeMapping(prevGenericInfo);
    }

    MyGenericInfo(@NotNull PsiClassType psiClassType, @Nullable MyGenericInfo prevGenericInfo) {
        this.psiClassType = psiClassType;
        // 引用模板类时的实际参数
        this.psiClass = psiClassType.resolve();
        parseGenerics(this.psiClassType, this.psiClass);

        if (this.psiClassType != null) {
            parseSuperTypes(this.psiClassType, prevGenericInfo);
        }
        mergeMapping(prevGenericInfo);
    }

    private void mergeMapping(@Nullable MyGenericInfo prevGenericInfo) {
        if (prevGenericInfo != null) {
            for (Map.Entry<PsiClass, Map<String, PsiType>> entry : prevGenericInfo.genericInfoMap.entrySet()) {
                // 不能覆盖本次递归时类的泛型映射信息
                if (!this.genericInfoMap.containsKey(entry.getKey())) {
                    this.genericInfoMap.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private void parseGenerics(@NotNull PsiClassType thisPsiClassType, PsiClass thisPsiClass) {
        Map<String, PsiType> mapping = getMapping(thisPsiClassType, thisPsiClass);
        if (mapping != null) {
            this.genericInfoMap.put(this.psiClass, mapping);
        }
    }

    private void parseSuperTypes(@Nonnull PsiClass psiClass, @Nullable MyGenericInfo prevGenericInfo) {
        this.doParseSuperTypes(psiClass.getSuperTypes(), prevGenericInfo);
    }

    private void parseSuperTypes(@Nonnull PsiClassType psiClassType, @Nullable MyGenericInfo prevGenericInfo) {
        this.doParseSuperTypes(psiClassType.getSuperTypes(), prevGenericInfo);
    }

    private void doParseSuperTypes(@Nonnull PsiType[] superTypes, @Nullable MyGenericInfo prevGenericInfo) {
        if (superTypes.length == 0) {
            return;
        }
        for (PsiType superPsiType : superTypes) {
            if (superPsiType instanceof PsiClassType) {
                PsiClass superPsiClass = ((PsiClassType) superPsiType).resolve();
                if (prevGenericInfo != null && prevGenericInfo.genericInfoMap.containsKey(superPsiClass)) {
                    continue;
                }

                Map<String, PsiType> superTypeMapping = getMapping((PsiClassType) superPsiType, superPsiClass);
                if (superTypeMapping != null) {
                    this.genericInfoMap.put(superPsiClass, superTypeMapping);
                }
                doParseSuperTypes(superPsiType.getSuperTypes(), prevGenericInfo);
            }
        }
    }

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
     * @param containingClass
     * @param typeParameterName
     * @return
     */
    @Nullable
    PsiType getRealType(PsiClass containingClass, String typeParameterName) {
        if (containingClass == null) {
            return null;
        }
        Map<String, PsiType> mapping = this.genericInfoMap.get(containingClass);
        if (mapping == null) {
            return null;
        }
        return mapping.get(typeParameterName);
    }

    PsiClass getPsiClass() {
        return this.psiClass;
    }

    PsiClassType getPsiClassType() {
        return this.psiClassType;
    }

}

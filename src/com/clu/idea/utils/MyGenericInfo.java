package com.clu.idea.utils;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.PsiUtil;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class MyGenericInfo {

    private ConcurrentMap<PsiClass, Map<String, PsiType>> genericInfoMap = new ConcurrentHashMap<>();

    private final PsiClass psiClass;

    private PsiClassType psiClassType;

    MyGenericInfo(@Nonnull PsiClass psiClass) {
        this.psiClass = psiClass;
    }

    MyGenericInfo(PsiClassType psiClassType) {
        // 引用模板类时的实际参数
        this.psiClass = psiClassType.resolve();
        if (this.psiClass != null) {
            // 实际泛型的参数
            PsiType[] parameters = psiClassType.getParameters();
            // 类的泛型参数列表
            PsiTypeParameter[] typeParameters = this.psiClass.getTypeParameters();
            if (parameters.length == typeParameters.length) {
                int index = 0;
                Map<String, PsiType> genericTypeToRealTypeMap = new LinkedHashMap<>();
                this.genericInfoMap.put(this.psiClass, genericTypeToRealTypeMap);
                for (PsiTypeParameter psiTypeParameter : typeParameters) {
                    genericTypeToRealTypeMap.put(psiTypeParameter.getName(), parameters[index++]);
                }
            }
        }
        this.psiClassType = psiClassType;
    }

    /**
     * 获取泛型T代表的实际类型
     * @param typeParameterName
     * @return
     */
    PsiType getRealType(String typeParameterName) {
        if (this.psiClass == null) {
            return null;
        }
        Map<String, PsiType> psiTypeParameterPsiTypeMap = this.genericInfoMap.get(this.psiClass);
        if (psiTypeParameterPsiTypeMap == null) {
            return null;
        }
        return psiTypeParameterPsiTypeMap.get(typeParameterName);
    }

    PsiClass getPsiClass() {
        return this.psiClass;
    }

    PsiClassType getPsiClassType() {
        return this.psiClassType;
    }

}

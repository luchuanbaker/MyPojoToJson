package com.clu.idea.utils;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

class MyClassInfo {

    private PsiClass psiClass;

    private PsiClassType psiClassType;

    private PsiType psiType;

    MyClassInfo(@NotNull PsiType psiType) {
        this.psiType = psiType;
    }

    MyClassInfo(@NotNull PsiClassType psiClassType) {
        this.psiClassType = psiClassType;
        // 引用模板类时的实际参数
        this.psiClass = psiClassType.resolve();
    }

    PsiClass getPsiClass() {
        return this.psiClass;
    }

    PsiClassType getPsiClassType() {
        return this.psiClassType;
    }

}

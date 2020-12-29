package com.clu.idea.utils;

import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeVisitor;
import com.intellij.psi.TypeAnnotationProvider;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 泛型参数缺失
 */
public class MissingGenericType extends PsiType {

    public static final MissingGenericType INSTANCE = new MissingGenericType();

    private MissingGenericType() {
        super(TypeAnnotationProvider.EMPTY);
    }

    @NotNull
    @Override
    public String getPresentableText() {
        return "MissingGenericType";
    }

    @NotNull
    @Override
    public String getCanonicalText() {
        return "MissingGenericType";
    }

    @Override
    public boolean isValid() {
        return false;
    }

    @Override
    public boolean equalsToText(@NotNull String text) {
        return false;
    }

    @Override
    public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
        return null;
    }

    @Nullable
    @Override
    public GlobalSearchScope getResolveScope() {
        return null;
    }

    @NotNull
    @Override
    public PsiType[] getSuperTypes() {
        return new PsiType[0];
    }
}

package com.vv.asm.transform

import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Status
import com.android.build.api.transform.Transform
import com.android.build.gradle.internal.pipeline.TransformManager
import com.vv.asm.ReflectUtil
import com.google.common.base.Charsets
import com.google.common.hash.Hashing

import java.lang.reflect.Field

/**
 * @author pengyushan 2019-3-7
 */
public abstract class BaseProxyTransform extends Transform {
    protected final Transform origTransform

    public BaseProxyTransform(Transform origTransform) {
        this.origTransform = origTransform
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        return origTransform.isIncremental()
    }

    protected String getUniqueJarName(File jarFile) {
        final String origJarName = jarFile.getName()
        final String hashing = Hashing.sha1().hashString(jarFile.getPath(), Charsets.UTF_16LE).toString()
        final int dotPos = origJarName.lastIndexOf('.')
        if (dotPos < 0) {
            return "${origJarName}_${hashing}"
        } else {
            final String nameWithoutDotExt = origJarName.substring(0, dotPos)
            final String dotExt = origJarName.substring(dotPos)
            return "${nameWithoutDotExt}_${hashing}${dotExt}"
        }
    }

    protected void replaceFile(QualifiedContent input, File newFile) {
        final Field fileField = ReflectUtil.getDeclaredFieldRecursive(input.getClass(), 'file')
        fileField.set(input, newFile)
    }

    protected void replaceChangedFile(DirectoryInput dirInput, Map<File, Status> changedFiles) {
        final Field changedFilesField = ReflectUtil.getDeclaredFieldRecursive(dirInput.getClass(), 'changedFiles')
        changedFilesField.set(dirInput, changedFiles)
    }
}

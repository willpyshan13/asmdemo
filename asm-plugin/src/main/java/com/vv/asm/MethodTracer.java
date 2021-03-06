/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2018 THL A29 Limited, a Tencent company. All rights reserved.
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vv.asm;

import com.vv.asm.item.TraceMethod;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * @author pengyushan 2019-3-7
 * <p>
 * This class hooks all collected methods in oder to trace method in/out.
 * </p>
 */

public class MethodTracer {

    private static final String TAG = "MethodTracer";
    private static AtomicInteger traceMethodCount = new AtomicInteger();
    private final TraceBuildConfig mTraceConfig;
    private final HashMap<String, TraceMethod> mCollectedMethodMap;
    private final HashMap<String, String> mCollectedClassExtendMap;

    MethodTracer(TraceBuildConfig config, HashMap<String, TraceMethod> collectedMap, HashMap<String, String> collectedClassExtendMap) {
        this.mTraceConfig = config;
        this.mCollectedClassExtendMap = collectedClassExtendMap;
        this.mCollectedMethodMap = collectedMap;
    }


    public void trace(Map<File, File> srcFolderList, Map<File, File> dependencyJarList) {
        traceMethodFromSrc(srcFolderList);
        traceMethodFromJar(dependencyJarList);
    }

    private void traceMethodFromSrc(Map<File, File> srcMap) {
        if (null != srcMap) {
            for (Map.Entry<File, File> entry : srcMap.entrySet()) {
                Log.d(TAG, "traceMethodFromSrc==" + entry.getKey().getName());
                innerTraceMethodFromSrc(entry.getKey(), entry.getValue());
            }
        }
    }

    private void traceMethodFromJar(Map<File, File> dependencyMap) {
        if (null != dependencyMap) {
            for (Map.Entry<File, File> entry : dependencyMap.entrySet()) {
                innerTraceMethodFromJar(entry.getKey(), entry.getValue());
            }
        }
    }

    private void innerTraceMethodFromSrc(File input, File output) {
        ArrayList<File> classFileList = new ArrayList<>();
        if (input.isDirectory()) {
            listClassFiles(classFileList, input);
        } else {
            classFileList.add(input);
        }

        for (File classFile : classFileList) {
            Log.d(TAG, "innerTraceMethodFromSrc=" + classFile.getName());
            InputStream is = null;
            FileOutputStream os = null;
            try {
                final String changedFileInputFullPath = classFile.getAbsolutePath();
                final File changedFileOutput = new File(changedFileInputFullPath.replace(input.getAbsolutePath(), output.getAbsolutePath()));
                if (!changedFileOutput.exists()) {
                    changedFileOutput.getParentFile().mkdirs();
                }
                changedFileOutput.createNewFile();

                if (mTraceConfig.isNeedTraceClass(classFile.getName())) {
                    is = new FileInputStream(classFile);
                    ClassReader classReader = new ClassReader(is);
                    ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    ClassVisitor classVisitor = new TraceClassAdapter(Opcodes.ASM5, classWriter);
                    classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);
                    is.close();

                    if (output.isDirectory()) {
                        os = new FileOutputStream(changedFileOutput);
                    } else {
                        os = new FileOutputStream(output);
                    }
                    os.write(classWriter.toByteArray());
                    os.close();
                } else {
                    Util.copyFileUsingStream(classFile, changedFileOutput);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    is.close();
                    os.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    private void innerTraceMethodFromJar(File input, File output) {
        ZipOutputStream zipOutputStream = null;
        ZipFile zipFile = null;
        try {
            zipOutputStream = new ZipOutputStream(new FileOutputStream(output));
            zipFile = new ZipFile(input);
            Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry zipEntry = enumeration.nextElement();
                String zipEntryName = zipEntry.getName();
                Log.d(TAG, "zipEntryName=" + zipEntryName);
                if (mTraceConfig.isNeedTraceClass(zipEntryName)) {
                    InputStream inputStream = zipFile.getInputStream(zipEntry);
                    ClassReader classReader = new ClassReader(inputStream);
                    ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    ClassVisitor classVisitor = new TraceClassAdapter(Opcodes.ASM5, classWriter);
                    classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);
                    byte[] data = classWriter.toByteArray();
                    InputStream byteArrayInputStream = new ByteArrayInputStream(data);
                    ZipEntry newZipEntry = new ZipEntry(zipEntryName);
                    Util.addZipEntry(zipOutputStream, newZipEntry, byteArrayInputStream);
                } else {
                    InputStream inputStream = zipFile.getInputStream(zipEntry);
                    ZipEntry newZipEntry = new ZipEntry(zipEntryName);
                    Util.addZipEntry(zipOutputStream, newZipEntry, inputStream);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "[traceMethodFromJar] err! %s", output.getAbsolutePath());
        } finally {
            try {
                if (zipOutputStream != null) {
                    zipOutputStream.finish();
                    zipOutputStream.flush();
                    zipOutputStream.close();
                }
                if (zipFile != null) {
                    zipFile.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "close stream err!");
            }
        }
    }

    private void listClassFiles(ArrayList<File> classFiles, File folder) {
        File[] files = folder.listFiles();
        if (null == files) {
            Log.e(TAG, "[listClassFiles] files is null! %s", folder.getAbsolutePath());
            return;
        }
        for (File file : files) {
            if (file == null) {
                continue;
            }
            Log.d(TAG, "listClassFiles=" + file.getName());

            if (file.isDirectory()) {
                listClassFiles(classFiles, file);
            } else {
                if (null != file && file.isFile()) {
                    classFiles.add(file);
                }

            }
        }
    }

    private class TraceClassAdapter extends ClassVisitor {

        private String className;
        private boolean isABSClass = false;
        private boolean isMethodBeatClass = false;

        TraceClassAdapter(int i, ClassVisitor classVisitor) {
            super(i, classVisitor);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            this.className = name;
//            Log.e(TAG, "visit=" + className);
            if ((access & Opcodes.ACC_ABSTRACT) > 0 || (access & Opcodes.ACC_INTERFACE) > 0) {
                this.isABSClass = true;
            }
            if (mTraceConfig.isMethodBeatClass(className, mCollectedClassExtendMap)) {
                isMethodBeatClass = true;
            }
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,
                                         String signature, String[] exceptions) {
//            Log.e(TAG, "visitMethod=" + className + " desc= " + desc);
            if (isABSClass) {
                return super.visitMethod(access, name, desc, signature, exceptions);
            } else {
                MethodVisitor methodVisitor = cv.visitMethod(access, name, desc, signature, exceptions);
                return new TraceMethodAdapter(api, methodVisitor, access, name, desc, this.className,
                        isMethodBeatClass);
            }
        }


        @Override
        public void visitEnd() {
            //if want to use a member field ,then you must set a field here
            if (className.contains("com/vv/life/mvvmhabit/binding/viewadapter/view/ViewAdapter")) {


            }else if(className.contains("com/asm/sample/SampleApplication$1")){
                FieldVisitor fv = cv.visitField(Opcodes.ACC_FINAL + Opcodes.ACC_SYNTHETIC, "val$ls", "I", null, null);
                fv.visitEnd();
            }
            super.visitEnd();
        }
    }

    private class TraceMethodAdapter extends AdviceAdapter {

        boolean isHasTracked = false;
        private final String methodName;
        private final String name;
        private final String className;
        private final String methodNameDesc;
        private final boolean isMethodBeatClass;

        protected TraceMethodAdapter(int api, MethodVisitor mv, int access, String name, String desc, String className,
                                     boolean isMethodBeatClass) {
            super(api, mv, access, name, desc);
            TraceMethod traceMethod = TraceMethod.create(0, access, className, name, desc);
            this.methodNameDesc = desc;
            this.methodName = traceMethod.getMethodName();
            this.isMethodBeatClass = isMethodBeatClass;
            this.className = className;
            this.name = name;
        }

        @Override
        protected void onMethodEnter() {
            //设置activity方法
            Log.i(TAG, "onMethodEnter==" + methodNameDesc + " " + methodName);
            if (methodName.contains("onClick") && methodNameDesc.equals("(Landroid/view/View;)V")) {
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(35, l0);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKESTATIC, TraceBuildConstants.LOG_ANALYTICS_BASE, "trackViewOnClick", "(Landroid/view/View;)V", false);
                isHasTracked = true;
            } else if (methodName.contains("onActivity")&&methodName.contains("Application")) {
//                mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
//                mv.visitInsn(Opcodes.DUP);
//                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
//                mv.visitVarInsn(Opcodes.ALOAD, 1);
//                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/app/Activity", "getLocalClassName", "()Ljava/lang/String;", false);
//                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
//                mv.visitLdcInsn("/" + name);
//                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
//                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
//                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/asm/sample/TraceTag", "i", "(Ljava/lang/String;)V", false);

//                Label l0 = new Label();
//                mv.visitLabel(l0);
//                mv.visitLineNumber(45, l0);
//                mv.visitLdcInsn("print");
//                mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
//                mv.visitInsn(Opcodes.DUP);
//                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
//                mv.visitLdcInsn("print  "+methodName+"  ");
//                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
//                mv.visitVarInsn(Opcodes.ALOAD, 0);
//                mv.visitFieldInsn(Opcodes.GETFIELD, className, "val$ls", "I");
//                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
//                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
//                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/util/Log", "d", "(Ljava/lang/String;Ljava/lang/String;)I", false);
//                mv.visitInsn(Opcodes.POP);
            }else if (methodName.contains("accept")&&methodName.contains("com/vv/life/mvvmhabit/binding/viewadapter/view/ViewAdapter")) {


            }else if (methodName.contains("onActivity")&&methodName.contains("com/asm/sample/SampleApplication$1")) {
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(45, l0);
                mv.visitLdcInsn("print");
                mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
                mv.visitInsn(Opcodes.DUP);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
                mv.visitLdcInsn("print  ");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitFieldInsn(Opcodes.GETFIELD, className, "val$ls", "I");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/util/Log", "d", "(Ljava/lang/String;Ljava/lang/String;)I", false);
                mv.visitInsn(Opcodes.POP);
            } else {
//                TraceMethod traceMethod = mCollectedMethodMap.get(methodName);
//                if (traceMethod != null) {
//                    traceMethodCount.incrementAndGet();
//                    String sectionName = methodName;
//                    int length = sectionName.length();
//                    if (length > TraceBuildConstants.MAX_SECTION_NAME_LEN) {
//                        int parmIndex = sectionName.indexOf('(');
//                        sectionName = sectionName.substring(0, parmIndex);
//                        length = sectionName.length();
//                        if (length > TraceBuildConstants.MAX_SECTION_NAME_LEN) {
//                            sectionName = sectionName.substring(length - TraceBuildConstants.MAX_SECTION_NAME_LEN);
//                        }
//                    }
//                    mv.visitLdcInsn(sectionName);
//                    mv.visitMethodInsn(INVOKESTATIC, TraceBuildConstants.TRACE_METHOD_BEAT_CLASS, "i", "(Ljava/lang/String;)V", false);
//                }
            }
        }

        @Override
        protected void onMethodExit(int opcode) {
            TraceMethod traceMethod = mCollectedMethodMap.get(methodName);
            if (traceMethod != null) {
                traceMethodCount.incrementAndGet();
                //mv.visitLdcInsn(traceMethod.id);
//                mv.visitMethodInsn(INVOKESTATIC, TraceBuildConstants.TRACE_METHOD_BEAT_CLASS, "o", "()V", false);
            }
        }
    }
}

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

/**
 *  @author pengyushan 2019-3-7
 */
public class TraceBuildConstants {

    public final static int MAX_SECTION_NAME_LEN = 127;

    public final static String TRACE_METHOD_BEAT_CLASS = "com/asm/sample/TraceTag";
//    public final static String TRACE_METHOD_BEAT_CLASS = "me/goldze/mvvmhabit/tracelog/TraceTag";
    public static final String[] UN_TRACE_CLASS = {"R.class", "R$", "Manifest", "BuildConfig"};
    public final static String DEFAULT_BLACK_TRACE =
                    "[package]\n"
                    + "-keepclass com/vv/life/MainActivity\n";

//    public final static String DEFAULT_BLACK_TRACE =
//            "[package]\n"
//                    + "-keepclass me/goldze/mvvmhabit/tracelog/TraceTag\n"
//                    + "-keepclass me/goldze/mvvmhabit/utils/VLog\n";
}

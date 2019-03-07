package com.vv.asm


import com.vv.asm.extection.SystraceExtension
import com.vv.asm.transform.SystemTraceTransform
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * @author pengyushan 2019-3-7
 */
class ASMPlugin implements Plugin<Project> {
    private static final String TAG = "SystracePlugin"

    @Override
    void apply(Project project) {
        project.extensions.create("systrace", SystraceExtension)

        if (!project.plugins.hasPlugin('com.android.application')) {
            throw new GradleException('Systrace Plugin, Android Application plugin required')
        }

        project.afterEvaluate {
            def android = project.extensions.android
            def configuration = project.systrace
            android.applicationVariants.all { variant ->
                String output = configuration.output
                if (Util.isNullOrNil(output)) {
                    configuration.output = project.getBuildDir().getAbsolutePath() + File.separator + "systrace_output"
                    Log.i(TAG, "set Systrace output file to " + configuration.output)
                }

                Log.i(TAG, "Trace enable is %s", configuration.enable)
                if (configuration.enable) {
                    SystemTraceTransform.inject(project, variant)
                }
            }
        }
    }
}

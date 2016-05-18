package com.appcan

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.android.builder.model.SourceProvider
import com.android.utils.FileUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import proguard.gradle.ProGuardTask

public class AppCanPlugin implements Plugin<Project> {


    static final String PLUGIN_NAME = "appcan"
    Project mProject;
    AppCanPluginExtension mExtension;
    BasePlugin androidPlugin
    List<Task> flavorsJarTask=new ArrayList<Task>()
    List<Task> flavorsProguardTask=new ArrayList<Task>()

    @Override
    public void apply(Project project) {
        this.mProject=project;
        this.mExtension=project.extensions.create(PLUGIN_NAME,AppCanPluginExtension)
        project.afterEvaluate {
            androidPlugin=getAndroidBasePlugin(project)
            def variantManager=getVariantManager(androidPlugin)
            def androidAppPlugin=androidPlugin as AppPlugin
            println(androidAppPlugin)
            processVariantData(variantManager.variantDataList,androidPlugin)

            variantManager.getProductFlavors().keySet().each { name ->
                createFlavorsJarTask(project,androidPlugin,name)
                createFlavorsProguardTask(project,name)
                createWebkitCorePalmZipTask(project,name)
//                createExportEngineZipTask(project,name)
            }
            createJarTask(project)
            createProguardJarTask(project)
        }

    }

    /**
     *
     * @param project
     * @param name
     */
    private static void createWebkitCorePalmZipTask(Project project, String name){
        def task=project.tasks.create("export${name.capitalize()}EngineTemp",Zip)
        def fromFilePath=project.file("../build/$name/WebkitCorePalm").mkdirs()
        def outputDir=project.file("../build/appcan/engine").mkdirs()
        project.file("$project.buildDir/test")
        println("---------"+fromFilePath)
        task.from("../build/$name/en_baseEngineProject/WebkitCorePalm")
        task.into("")
        task.destinationDir=project.file("../build/$name/en_baseEngineProject")
        task.archiveName="Engine_${name}"
        task.encoding="UTF-8"
        task.dependsOn(project.tasks.findByName("proguard${name.capitalize()}Engine"))
        task.doFirst{
            println("project path: "+project.getProjectDir().getPath())
            File baseProjectPath=new File(project.getProjectDir().getParentFile(),"en_baseEngineProject")//en_baseEngineProject 目录
            File tempFileDir=new File(project.getProjectDir().getParentFile(),"build/$name")
            if (!tempFileDir.exists()){
                tempFileDir.mkdirs()
            }
            FileUtils.copy(baseProjectPath,tempFileDir)
            FileUtils.copy(new File(project.getProjectDir(),"build/outputs/jar/AppCanEngine_${name}.jar"),
                    new File(tempFileDir, 'en_baseEngineProject/WebkitCorePalm/libs'))

        }
        task.doLast{

        }


    }


    private static void createExportEngineZipTask(Project project, String name){
        def task=project.tasks.create("export${name.capitalize()}Engine",Zip)
        def fromFilePath=project.file("../build/$name/WebkitCorePalm").mkdirs()
        def outputDir=project.file("../build/appcan/engine").mkdirs()
        project.file("$project.buildDir/test")
        println("---------"+fromFilePath)
        task.from("../build/$name/en_baseEngineProject/")
        task.into("")
        task.exclude("../build/$name/en_baseEngineProject/WebkitCorePalm")
        task.destinationDir=project.file("../build/appcan/engine")
        task.archiveName="Engine_${name}.zip"
        task.encoding="UTF-8"
        task.dependsOn(project.tasks.findByName("export${name.capitalize()}EngineTemp"))
        task.doFirst{

        }
        task.doLast{

        }


    }


    /**
     * 生成所有的flavor未混淆引擎jar
     * @param project
     */
    private void createJarTask(Project project){
        def jarTask=project.tasks.create("jarEngine")
        for (Task task:flavorsJarTask){
            jarTask.dependsOn(task)
        }
     }

    /**
     * 生成所有的flavor混淆过的jar
     * @param project
     */
    private void createProguardJarTask(Project project){
        def proguardTask=project.tasks.create("proguardEngine")
        for (Task task:flavorsProguardTask){
            proguardTask.dependsOn(task)
        }
    }

    /**
     * 对每个flavor创建Task生成不混淆的jar
     **/
    private void createFlavorsJarTask(Project project, BasePlugin androidPlugin, def name){
        def jarBaseName="AppCanEngine_${name}_un_proguard"
        def applicationId=androidPlugin.extension.defaultConfig.applicationId;
        def jarEngineTask = project.tasks.create("jar${name.capitalize()}Engine",Jar)
        jarEngineTask.setBaseName(jarBaseName)
        jarEngineTask.description="export $name Engine jar"
        jarEngineTask.destinationDir=project.file("build/outputs/jar")
        jarEngineTask.from("build/intermediates/classes/$name/release/")
        jarEngineTask.into("")
        jarEngineTask.exclude('**/R.class')
        jarEngineTask.exclude('**/R\$*.class')
        jarEngineTask.exclude('**/BuildConfig.class')
        jarEngineTask.exclude(applicationId.replace('.','/'))
        jarEngineTask.dependsOn(project.tasks.findByName("compile${name.capitalize()}ReleaseJavaWithJavac"))
        flavorsJarTask.add(jarEngineTask)
    }

    /**
     * 对每个flavor创建Task生成混淆的jar
     **/
    private void createFlavorsProguardTask(Project project, def name){
        def jarTaskName="jar${name.capitalize()}Engine"
        def taskName="proguard${name.capitalize()}Engine"
        def proguardTask=project.tasks.create(taskName,ProGuardTask)
        proguardTask.dependsOn(project.tasks.findByName(jarTaskName))

        def androidSDKDir = androidPlugin.sdkHandler.getSdkFolder()

        def androidJarDir = androidSDKDir.toString() + '/platforms/' + androidPlugin.extension.getCompileSdkVersion() +
                '/android.jar'

        proguardTask.injars("build/outputs/jar/AppCanEngine_${name}_un_proguard.jar")
        proguardTask.outjars("build/outputs/jar/AppCanEngine_${name}.jar")
        proguardTask.libraryjars(androidJarDir)
        proguardTask.configuration('proguard.pro')
        if ('crosswalk'.equals(name)){
            proguardTask.libraryjars('libs/crosswalk-19.49.514.0.aar')
        }
        flavorsProguardTask.add(proguardTask)
    }

    private void processVariantData(
            List<BaseVariantData<? extends BaseVariantOutputData>> variantDataList, BasePlugin androidPlugin) {

        variantDataList.each { variantData ->
            def variantDataName = variantData.name

            def javaTask = getJavaTask(variantData)
            if (javaTask == null) {
                project.logger.info("javaTask is missing for $variantDataName, so Groovy files won't be compiled for it")
                return
            }
            def providers = variantData.variantConfiguration.sortedSourceProviders
            providers.each { SourceProvider provider ->

            }

        }
    }

    private static SourceTask getJavaTask(BaseVariantData baseVariantData) {
        if (baseVariantData.metaClass.getMetaProperty('javaCompileTask')) {
            return baseVariantData.javaCompileTask
        } else if (baseVariantData.metaClass.getMetaProperty('javaCompilerTask')) {
            return baseVariantData.javaCompilerTask
        }
        return null
    }

    private static BasePlugin getAndroidBasePlugin(Project project) {
        def plugin = project.plugins.findPlugin('android') ?:
                project.plugins.findPlugin('android-library')

        return plugin as BasePlugin
    }



    private static VariantManager getVariantManager(BasePlugin plugin) {
        return plugin.variantManager
    }



}

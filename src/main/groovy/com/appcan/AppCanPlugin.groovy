package com.appcan

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.android.builder.model.SourceProvider
import net.koiosmedia.gradle.sevenzip.SevenZip
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.Copy
import proguard.gradle.ProGuardTask

import java.util.regex.Matcher
import java.util.regex.Pattern

public class AppCanPlugin implements Plugin<Project> {


    static final String PLUGIN_NAME = "appcan"
    Project mProject;
    AppCanPluginExtension mExtension;
    BasePlugin androidPlugin
    List<Task> flavorsJarTask=new ArrayList<Task>()
    List<Task> flavorsProguardTask=new ArrayList<Task>()
    List<String> flavors=new ArrayList<String>()
    static final String BUILD_APPCAN_DIR ="build/appcan"
    public static String version=""

    @Override
    public void apply(Project project) {
        this.mProject=project;
        this.mExtension=project.extensions.create(PLUGIN_NAME,AppCanPluginExtension)
        project.afterEvaluate {
            version=getEngineVersion(project)
            androidPlugin=getAndroidBasePlugin(project)
            def variantManager=getVariantManager(androidPlugin)
            def androidAppPlugin=androidPlugin as AppPlugin
            println(androidAppPlugin)
            processVariantData(variantManager.variantDataList,androidPlugin)

            variantManager.getProductFlavors().keySet().each { name ->
                createFlavorsJarTask(project,androidPlugin,name)
                createFlavorsProguardTask(project,name)
                createCopyBaseProjectTask(project,name)
                createCopyEngineJarTask(project,name)
                createWebkitCorePalmZipTask(project,name)
                createBuildEngineZipTask(project,name)
            }
            createJarTask(project)
            createProguardJarTask(project)
            createBuildEngineTask(project)
        }

    }

    /**
     * 生成所有的引擎
     */
    private void createBuildEngineTask(Project project){
        def task=project.tasks.create("buildEngine")
        flavors.each { flavor->
            task.dependsOn(project.tasks.findByName("build${flavor.capitalize()}Engine"))
        }
    }

    /**
     * 拷贝基础工程
     */
    private static void createCopyBaseProjectTask(Project project, String name){
        def task=project.tasks.create("copy${name.capitalize()}Project",Copy)
        task.from("../en_baseEngineProject")
        task.into("$BUILD_APPCAN_DIR/$name/en_baseEngineProject")
        task.dependsOn(project.tasks.findByName("proguard${name.capitalize()}Engine"))
    }

    /**
     * 拷贝引擎jar
     */
    private static void createCopyEngineJarTask(Project project, String name){
        def task=project.tasks.create("copy${name.capitalize()}EngineJar",Copy)
        task.from("build/outputs/jar/AppCanEngine-${name}-${version}.jar","src/${name}/libs/")

        task.into("$BUILD_APPCAN_DIR/$name/en_baseEngineProject/WebkitCorePalm/libs")
        task.dependsOn(project.tasks.findByName("copy${name.capitalize()}Project"))
    }

    /**
     * 压缩WebkitCorePalm工程
     */
    private void createWebkitCorePalmZipTask(Project project, String name){
        def task=project.tasks.create("build${name.capitalize()}EngineTemp",SevenZip)
        task.from("$BUILD_APPCAN_DIR/${name}/en_baseEngineProject/WebkitCorePalm")
        task.destinationDir=project.file("$BUILD_APPCAN_DIR/$name/en_baseEngineProject")
        task.archiveName=getPackageName(name)
        task.dependsOn(project.tasks.findByName("copy${name.capitalize()}EngineJar"))

    }

    /**
     * 生成引擎包
     */
    private void createBuildEngineZipTask(Project project, String name){
        def task=project.tasks.create("build${name.capitalize()}Engine",Zip)
        task.from("$BUILD_APPCAN_DIR/$name/en_baseEngineProject/${getPackageName(name)}",
                "$BUILD_APPCAN_DIR/$name/en_baseEngineProject/androidEngine.xml")
        task.into("")
        task.exclude("$BUILD_APPCAN_DIR/$name/en_baseEngineProject/WebkitCorePalm")
        task.destinationDir=project.file("build/outputs/engine")
        task.baseName=getPackageName(name)
        task.encoding="UTF-8"
        task.dependsOn(project.tasks.findByName("build${name.capitalize()}EngineTemp"))
        task.doFirst{
             setXmlContent(new File(project.getProjectDir(),
                    "$BUILD_APPCAN_DIR/${name}/en_baseEngineProject/androidEngine.xml"),name)
        }
    }

    /**
     * 获取package name
     */
    private String getPackageName(String flavor){
        def date = new Date().format("yyMMdd")
        def versionTemp=version
        return "android_Engine_${versionTemp}_${date}_01_${flavor}"
    }

    /**
     * 获取xml文件里面的version
     */
    private static String getEngineZipVersion(){
        def date = new Date().format("yyMMdd")
        def versionTemp=version
        return "sdksuit_${versionTemp}_${date}_01"
    }

    /**
     * 获取展示在网页的提示信息
     * @param flavor
     * @return
     */
    private static String  getKernelString(String flavor){
        if ("x5".equals(flavor)){
            return "腾讯X5内核"
        }else if ("system".equals(flavor)){
            return "系统内核"
        }else if ("crosswalk".equals(flavor)){
            return "Crosswalk内核"
        }
        return flavor+"内核"
    }

    private void setXmlContent(File xmlFile,String flavor){
        def content=xmlFile.getText('UTF-8')
                .replace("\$version\$",getEngineZipVersion())
                .replace("\$package\$",getPackageName(flavor))
                .replace("\$kernel\$",getKernelString(flavor))
        xmlFile.write(content)
    }

    /**
     * 获取引擎版本号
     * @return
     */
    private String getEngineVersion(Project project){
        def versionFilePath = "src/main/java/org/zywx/wbpalmstar/base/BConstant.java"
        def version=""
        Pattern p=Pattern.compile("ENGINE_VERSION=\"(.*?)\"")
        Matcher m=p.matcher(new File(project.getProjectDir(),versionFilePath).getText('UTF-8'))
        if (m.find()){
            version=m.group(1)
        }
        println("Engine version is $version")
        return version
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
        def jarBaseName="AppCanEngine-${name}-un-proguard"
        def applicationId=androidPlugin.extension.defaultConfig.applicationId;
        def jarEngineTask = project.tasks.create("jar${name.capitalize()}Engine",Jar)
        jarEngineTask.setBaseName(jarBaseName)
        jarEngineTask.description="build $name Engine jar"
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

        proguardTask.injars("build/outputs/jar/AppCanEngine-${name}-un-proguard.jar")
        proguardTask.outjars("build/outputs/jar/AppCanEngine-${name}-${version}.jar")
        proguardTask.libraryjars(androidJarDir)
        proguardTask.libraryjars("libs")
        proguardTask.libraryjars("src/${name}/libs")
        proguardTask.configuration('proguard.pro')
        flavors.add(name)
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

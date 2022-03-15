package com.ys.eag.plugin.jar.encryption;

import com.ys.eag.api.IDeviceLoader;
import com.ys.eag.tools.CovertTools;
import com.ys.eag.tools.DesTools;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.StringReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author wupeng
 * Create on 3/29/19.
 * @version 1.0
 */
@Mojo(name = "eag-plugins-jar-encryption", defaultPhase = LifecyclePhase.PACKAGE)
public class EagPluginsJarEncryptionMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter
    private String mainClass;

    private byte[] fileHead = {0x14, 0x05, 0x1c, 0x77};

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Build build = project.getBuild();
        Plugin assemblyPlugin = project.getPlugin("org.apache.maven.plugins:maven-assembly-plugin");
        if (assemblyPlugin == null) {
            getLog().error("Can't find the plugin: org.apache.maven.plugins:maven-assembly-plugin");
            throw new MojoFailureException("Lack of the assembly plugin");
        }
        String getAssembly = assemblyPlugin.getConfiguration().toString();
        DesTools desTools = DesTools.getInstance();
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(getAssembly)));
            Element root = doc.getDocumentElement();
            NodeList list = root.getElementsByTagName("descriptorRef");
            if (list.getLength() != 1) {
                getLog().error("The descriptorRef param number is." + list.getLength());
                throw new Exception("The descriptorRef param error.");
            }
            String lastFix = list.item(0).getTextContent();
            String fileName = build.getDirectory() + File.separator + project.getArtifactId() + "-" + project.getVersion() + "-" + lastFix + ".jar";
            File file = new File(fileName);
            URL url = new URL("file:" + file.getAbsolutePath());
            URLClassLoader loader = new URLClassLoader(new URL[]{url}, Thread.currentThread().getContextClassLoader());
            Class deviceLoaderClass = Class.forName(mainClass, true, loader);
            IDeviceLoader deviceLoader = (IDeviceLoader) deviceLoaderClass.newInstance();
            String pluginVersion = deviceLoader.getVersion();
            loader.close();
            String outFileName = build.getDirectory() + File.separator + project.getArtifactId() + "-" + pluginVersion + ".eag";
            int length = mainClass.length();
            if (file.exists() && length != 0) {
                byte[] mainClassBytes = mainClass.getBytes();
                byte[] encodeMainClassBytes = desTools.getEncCode(mainClassBytes);
                FileOutputStream out = new FileOutputStream(outFileName);
                out.write(encodeMainClassBytes);
                Path in = Paths.get(fileName);
                //读取文件，加密，并写出
                byte[] buffer = desTools.getEncCode(Files.readAllBytes(in));
                byte[] outBytes = new byte[8 + encodeMainClassBytes.length + buffer.length];
                System.arraycopy(fileHead, 0, outBytes, 0, 4);
                System.arraycopy(CovertTools.intToByteArray(encodeMainClassBytes.length), 0, outBytes, 4, 4);
                System.arraycopy(encodeMainClassBytes, 0, outBytes, 8, encodeMainClassBytes.length);
                System.arraycopy(buffer, 0, outBytes, 8 + encodeMainClassBytes.length, buffer.length);
                Files.write(Paths.get(outFileName), outBytes);
                //释放资源
                out.close();
            } else {
                throw new Exception("Do not exists the package jar file, path:" + fileName + " or the main class name is empty.");
            }
            getLog().info(fileName);
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage());
        }
    }

}

package cs652.repl;

import com.sun.source.util.JavacTask;

import javax.tools.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaREPL {
    public static void main(String[] args) throws IOException {
        exec(new InputStreamReader(System.in));
    }

    /**
     * User Interactive shell - Get input line from NestedReader class and process it
     * @param r
     * @throws IOException
     */
    public static void exec(Reader r) throws IOException {
        BufferedReader stdin = new BufferedReader(r);
        NestedReader reader = new NestedReader(stdin);
        int classNumber = 0;
        Path projectDir = Paths.get(System.getProperty("user.dir"));
        Path tempDir = Files.createTempDirectory(projectDir, "temp");
        URL tmpURL = new File(tempDir.toString()).toURI().toURL();
        ClassLoader loader = new URLClassLoader(new URL[]{tmpURL});

        while (true) {
            System.out.print("> ");
            String java = reader.getNestedString();
            if (!java.equals("")) {
                if (java.contains("//")) {
                    java = java.replaceAll("(\\/\\/.*)", "");
                }
                if (java.startsWith("print ")) {
                    Pattern p = Pattern.compile("print ((.*[\\t\\s\\n]*.*)*);");
                    Matcher m = p.matcher(java);
                    if (m.find()) {
                        java = String.format("System.out.println(%s);", m.group(1));
                    }

                }
                String fileName = String.format("Interp_%d.java", classNumber);
                StringBuffer classTemplate = new StringBuffer();
                classTemplate.append("import java.io.*;\nimport java.util.*;\n");
                if (classNumber == 0) {
                    classTemplate.append(String.format("public class Interp_%d {\n", classNumber));
                } else {
                    classTemplate.append(String.format("public class Interp_%d extends Interp_%d {\n", classNumber, classNumber - 1));
                }
                if (isDeclaration(java)) {
                    classTemplate.append(String.format("\tpublic static %s", java));
                    classTemplate.append("\n\tpublic static void exec() {");
                    classTemplate.append("\n\t}\n}");
                } else {
                    classTemplate.append("\tpublic static void exec() {\n");
                    classTemplate.append("\t\t" + java);
                    classTemplate.append("\n\t}\n}");
                }
                writeFile(tempDir.toString(), fileName, classTemplate.toString());
                String compiledResult = compile(tempDir.toString() + File.separator + fileName, tempDir);
                if (compiledResult.equals("")) {
                    exec(loader, String.format("Interp_%d", classNumber), "exec");
                } else {
                    String[] errorstr = compiledResult.split("\\$");
                    for (int i = 0; i < errorstr.length; i++) {
                        System.err.println(errorstr[i]);
                    }
                }
                classNumber++;
            } else
                break;
        }
    }

    /**
     * Check whether the given line is a declaration or a statement by creating a dummy class and compiling it
     * @param line
     * @return
     */
    public static boolean isDeclaration(String line) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
        try {
            StringBuffer classTemplate = new StringBuffer();
            classTemplate.append("import java.io.*;\nimport java.util.*;\n");
            classTemplate.append("public class Bogus {\n");
            classTemplate.append(String.format("public static %s\n", line));
            classTemplate.append("public static void exec() {\n");
            classTemplate.append("}\n}");
            Path bogusDir = Files.createTempDirectory(Paths.get(System.getProperty("user.dir")), "bogus");
            writeFile(bogusDir.toString(), "Bogus.java", classTemplate.toString());

            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromStrings(Arrays.asList(bogusDir.toString() + File.separator + "Bogus.java"));
            JavacTask task = (JavacTask) compiler.getTask(null, fileManager, diagnostics, null, null, compilationUnits);
            task.parse();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return diagnostics.getDiagnostics().size() == 0;
    }

    /**
     * Compile the java file using JavaCompiler and return error message on failure
     * @param fileName
     * @param tempDir
     * @return
     * @throws IOException
     */
    public static String compile(String fileName, Path tempDir) throws IOException {
        StringBuffer msg = new StringBuffer();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
        fileManager.setLocation(StandardLocation.CLASS_PATH, Arrays.asList(tempDir.toFile()));
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromStrings(Arrays.asList(fileName));
        JavacTask task = (JavacTask) compiler.getTask(null, fileManager, diagnostics, null, null, compilationUnits);
        try {
            boolean ok = task.call();
            if (!ok) {
                for (Diagnostic<? extends JavaFileObject> err : diagnostics.getDiagnostics()) {
                    msg.append("line ");
                    msg.append(err.getLineNumber() + 2);
                    msg.append(": ");
                    msg.append(err.getMessage(Locale.getDefault()));
                    msg.append("$");
                }
            }
        } finally {
            try {
                fileManager.close();
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
        return msg.toString();
    }

    /**
     * For a given class, invoke a method using java reflection
     * @param loader
     * @param className
     * @param methodName
     */
    public static void exec(ClassLoader loader, String className, String methodName) {
        try {
            Class cl = loader.loadClass(className);
            Object obj = cl.newInstance();
            Method method = cl.getDeclaredMethod(methodName, null);
            method.invoke(obj, null);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    /**
     * Given the directory location, write the content to file
     * @param dir
     * @param fileName
     * @param content
     */
    public static void writeFile(String dir, String fileName, String content) {
        try (PrintWriter writer = new PrintWriter(dir + File.separator + fileName)) {
            writer.write(content);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}

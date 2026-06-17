
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.io.PrintWriter;

public class Main {
    // Tracks the current working directory for the shell, updated by cd builtin
    private static File currentDirectory = new File(System.getProperty("user.dir"));

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            if (!sc.hasNextLine())
                break;

            String command = sc.nextLine();
            String trimmed = command.trim();

            if (trimmed.isEmpty())
                continue;

            String[] parts = parseCommand(trimmed);

            String outputFile = null;
            boolean appendOutput = false;
            String errorFile = null;
            boolean appendError = false;
            List<String> filteredParts = new ArrayList<>();

            for (int i = 0; i < parts.length; i++) {
                if (parts[i].equals(">") || parts[i].equals("1>")) {
                    if (i + 1 < parts.length) {
                        outputFile = parts[i + 1];
                        appendOutput = false;
                        i++;
                    }
                } else if (parts[i].equals(">>") || parts[i].equals("1>>")) {
                    if (i + 1 < parts.length) {
                        outputFile = parts[i + 1];
                        appendOutput = true;
                        i++;
                    }
                } else if (parts[i].equals("2>")) {
                    if (i + 1 < parts.length) {
                        errorFile = parts[i + 1];
                        appendError = false;
                        i++;
                    }
                } else if (parts[i].equals("2>>")) {
                    if (i + 1 < parts.length) {
                        errorFile = parts[i + 1];
                        appendError = true;
                        i++;
                    }
                } else {
                    filteredParts.add(parts[i]);
                }
            }

            parts = filteredParts.toArray(new String[0]);
            String cmd = parts[0];

            File outFile = null;
            if (outputFile != null) {
                outFile = new File(outputFile);
                if (!outFile.isAbsolute()) {
                    outFile = new File(currentDirectory, outputFile);
                }
                if (outFile.getParentFile() != null) {
                    outFile.getParentFile().mkdirs();
                }
            }

            File errFile = null;
            if (errorFile != null) {
                errFile = new File(errorFile);
                if (!errFile.isAbsolute()) {
                    errFile = new File(currentDirectory, errorFile);
                }
                if (errFile.getParentFile() != null) {
                    errFile.getParentFile().mkdirs();
                }
            }

            java.io.PrintStream out = System.out;
            java.io.PrintStream errOut = System.out;
            try {
                if (outFile != null) {
                    out = new java.io.PrintStream(new java.io.FileOutputStream(outFile, appendOutput));
                }
                if (errFile != null) {
                    errOut = new java.io.PrintStream(new java.io.FileOutputStream(errFile, appendError));
                }

                if (cmd.equals("exit")) {
                int status = 0;
                if (parts.length > 1) {
                    try {
                        status = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                    }
                }
                System.exit(status);
            }

            else if (cmd.equals("echo")) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < parts.length; i++) {
                    if (i > 1)
                        sb.append(" ");
                    sb.append(parts[i]);
                }
                out.println(sb);
            }

            else if (cmd.equals("pwd")) {
                out.println(currentDirectory.getAbsolutePath());
            }

            else if (cmd.equals("cd")) {
                if (parts.length > 1) {
                    String path = parts[1];

                    if (path.equals("~")) {
                        path = System.getenv("HOME");
                    }

                    File newDir;
                    if (new File(path).isAbsolute()) {
                        newDir = new File(path);
                    } else {
                        newDir = new File(currentDirectory, path);
                    }

                    try {
                        newDir = newDir.getCanonicalFile();

                        if (newDir.exists() && newDir.isDirectory()) {
                            currentDirectory = newDir;
                        } else {
                            errOut.println("cd: " + parts[1] + ": No such file or directory");
                        }
                    } catch (Exception e) {
                        errOut.println("cd: " + parts[1] + ": No such file or directory");
                    }
                }
            }

            else if (cmd.equals("type")) {
                if (parts.length < 2) {
                    errOut.println("type: missing operand");
                } else {
                    String target = parts[1];

                    if (target.equals("exit")
                            || target.equals("echo")
                            || target.equals("pwd")
                            || target.equals("cd")
                            || target.equals("type")) {
                        out.println(target + " is a shell builtin");
                    } else {
                        File file = findExecutable(target);

                        if (file != null) {
                            out.println(target + " is " + file.getAbsolutePath());
                        } else {
                            errOut.println(target + ": not found");
                        }
                    }
                }
            }
            else {
                File file = findExecutable(cmd);

                if (file != null) {
                    try {
                        List<String> commandList = new ArrayList<>();
                        // Use the basename as argv[0], matching POSIX shell behavior.
                        commandList.add(file.getName());

                        for (int i = 1; i < parts.length; i++) {
                            commandList.add(parts[i]);
                        }

                        // Close file streams before ProcessBuilder starts so it can take over the files
                        if (out != System.out) {
                            out.close();
                            out = System.out;
                        }
                        if (errOut != System.out) {
                            errOut.close();
                            errOut = System.out;
                        }

                        ProcessBuilder pb = new ProcessBuilder(commandList);
                        pb.directory(currentDirectory);
                        pb.inheritIO();
                        if (outFile != null) {
                            if (appendOutput) {
                                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(outFile));
                            } else {
                                pb.redirectOutput(outFile);
                            }
                        }
                        if (errFile != null) {
                            if (appendError) {
                                pb.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
                            } else {
                                pb.redirectError(errFile);
                            }
                        }

                        // Prepend the executable's parent directory to PATH so
                        // ProcessBuilder can locate the binary by its basename.
                        String parentDir = file.getParent() != null ? file.getParent() : ".";
                        pb.environment().merge("PATH", parentDir,
                                (existing, prepend) -> prepend + File.pathSeparator + existing);

                        Process process = pb.start();
                        process.waitFor();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    errOut.println(cmd + ": command not found");
                }
            } // end of else block for external commands
        } finally { // end of try block started before the if-else chain
            if (out != System.out) {
                out.close();
            }
            if (errOut != System.out) {
                errOut.close();
            }
        }
    } // end of while (true)
} // end of main

    private static String[] parseCommand(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inSingleQuotes) {
                if (c == '\'') {
                    inSingleQuotes = false;
                } else {
                    current.append(c);
                }
            }

            else if (inDoubleQuotes) {
                if (c == '"') {
                    inDoubleQuotes = false;
                }

                else if (c == '\\') {
                    if (i + 1 < input.length()) {
                        char next = input.charAt(i + 1);

                        // Only ", \, $, ` are special in double quotes
                        if (next == '"' || next == '\\' || next == '$' || next == '`') {
                            current.append(next);
                            i++;
                        } else {
                            current.append(c);
                        }
                    } else {
                        current.append('\\');
                    }
                }

                else {
                    current.append(c);
                }
            }

            else {
                if (c == '\'') {
                    inSingleQuotes = true;
                }

                else if (c == '"') {
                    inDoubleQuotes = true;
                }

                else if (c == '\\') {
                    // Escape next character (including whitespace) to be part of the token
                    if (i + 1 < input.length()) {
                        current.append(input.charAt(i + 1));
                        i++; // skip the escaped character
                    } else {
                        // Trailing backslash, treat as literal
                        current.append('\\');
                    }
                } else if (Character.isWhitespace(c)) {
                    if (current.length() > 0) {
                        args.add(current.toString());
                        current.setLength(0);
                    }
                }

                else {
                    current.append(c);
                }
            }
        }

        if (current.length() > 0) {
            args.add(current.toString());
        }

        return args.toArray(new String[0]);
    }

    private static File findExecutable(String target) {
        // Only treat '/' as a path separator. Backslashes are considered part of the
        // filename.
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        if (target.contains("/")) {
            File file = new File(target);
            if (file.exists() && file.isFile()) {
                if (isWindows || file.canExecute()) {
                    return file;
                }
            }
            return null;
        }
        // Check if executable exists in current working directory by exact filename
        File[] cwdFiles = currentDirectory.listFiles();
        if (cwdFiles != null) {
            for (File f : cwdFiles) {
                if (f.isFile() && f.getName().equals(target)) {
                    if (isWindows || f.canExecute()) {
                        return f;
                    }
                }
            }
        }
        return findInPath(target);
    }

    private static File findInPath(String target) {
        String pathEnv = System.getenv("PATH");

        if (pathEnv == null)
            return null;

        String[] directories = pathEnv.split(File.pathSeparator);

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        String[] extensions = { "" };

        if (isWindows) {
            extensions = new String[] { "", ".exe", ".bat", ".cmd", ".com" };
        }

        for (String dir : directories) {
            for (String ext : extensions) {
                File file = new File(dir, target + ext);

                if (file.exists() && file.isFile()) {
                    if (isWindows || file.canExecute()) {
                        return file;
                    }
                }
            }
        }

        return null;
    }
}

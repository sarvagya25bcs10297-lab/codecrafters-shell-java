
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

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
            String cmd = parts[0];

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
                System.out.println(sb);
            }

            else if (cmd.equals("pwd")) {
                System.out.println(currentDirectory.getAbsolutePath());
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
                            System.out.println("cd: " + parts[1] + ": No such file or directory");
                        }
                    } catch (Exception e) {
                        System.out.println("cd: " + parts[1] + ": No such file or directory");
                    }
                }
            }

            else if (cmd.equals("type")) {
                if (parts.length < 2) {
                    System.out.println("type: missing operand");
                } else {
                    String target = parts[1];

                    if (target.equals("exit")
                            || target.equals("echo")
                            || target.equals("pwd")
                            || target.equals("cd")
                            || target.equals("type")) {
                        System.out.println(target + " is a shell builtin");
                    } else {
                        File file = findExecutable(target);

                        if (file != null) {
                            System.out.println(target + " is " + file.getAbsolutePath());
                        } else {
                            System.out.println(target + ": not found");
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

                        ProcessBuilder pb = new ProcessBuilder(commandList);
                        pb.directory(currentDirectory);
                        pb.inheritIO();

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
                    System.out.println(cmd + ": command not found");
                }
            }
        }
    }

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
                }
                else if (Character.isWhitespace(c)) {
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
        // Only treat '/' as a path separator. Backslashes are considered part of the filename.
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

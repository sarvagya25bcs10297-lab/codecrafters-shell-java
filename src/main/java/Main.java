
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.io.PrintWriter;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Completer;
import org.jline.reader.Candidate;
import org.jline.reader.ParsedLine;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.reader.impl.DefaultParser;

public class Main {
    // Tracks the current working directory for the shell, updated by cd builtin
    private static File currentDirectory = new File(System.getProperty("user.dir"));
    private static String lastBuffer = "";
    private static int lastCursor = -1;
    private static int tabCount = 0;

    public static void main(String[] args) throws Exception {
        DefaultParser parser = new DefaultParser();
        parser.setEscapeChars(new char[0]);
        parser.setQuoteChars(new char[0]);

        // Custom completer that checks the raw buffer directly and includes PATH executables and filenames
        Completer builtinCompleter = (reader, line, candidates) -> {
            String buf = line.line().substring(0, line.cursor());
            if (buf.length() == 0) {
                return;
            }

            int lastSpace = buf.lastIndexOf(' ');
            if (lastSpace >= 0) {
                // Filename completion
                String argPrefix = buf.substring(lastSpace + 1);
                String dirPath = "";
                String filePrefix = argPrefix;
                File targetDir = currentDirectory;

                int lastSlashIndex = argPrefix.lastIndexOf('/');
                if (lastSlashIndex >= 0) {
                    dirPath = argPrefix.substring(0, lastSlashIndex + 1);
                    filePrefix = argPrefix.substring(lastSlashIndex + 1);
                    targetDir = new File(currentDirectory, dirPath);
                }

                java.util.Set<String> fileMatches = new java.util.LinkedHashSet<>();
                if (targetDir.exists() && targetDir.isDirectory()) {
                    File[] dirFiles = targetDir.listFiles();
                    if (dirFiles != null) {
                        for (File f : dirFiles) {
                            if (f.getName().startsWith(filePrefix)) {
                                fileMatches.add(dirPath + f.getName());
                            }
                        }
                    }
                }

                if (fileMatches.size() == 0) {
                    try {
                        System.out.print("\u0007");
                        System.out.flush();
                        reader.getTerminal().writer().print("\u0007");
                        reader.getTerminal().writer().flush();
                    } catch (Exception e) {
                    }
                    return;
                }

                if (fileMatches.size() == 1) {
                    String match = fileMatches.iterator().next();
                    File matchFile = new File(currentDirectory, match);
                    String suffix = matchFile.isDirectory() ? "/" : " ";
                    candidates.add(new Candidate(match + suffix, match, null, null, null, null, false));
                } else {
                    // Multiple file matches
                    String lcp = getLongestCommonPrefix(fileMatches);
                    if (lcp.length() > argPrefix.length()) {
                        for (String match : fileMatches) {
                            File matchFile = new File(currentDirectory, match);
                            String suffix = matchFile.isDirectory() ? "/" : " ";
                            candidates.add(new Candidate(match + suffix, match, null, null, null, null, false));
                        }
                    } else {
                        if (buf.equals(lastBuffer) && line.cursor() == lastCursor) {
                            tabCount++;
                        } else {
                            tabCount = 1;
                            lastBuffer = buf;
                            lastCursor = line.cursor();
                        }
                        if (tabCount == 1) {
                            try {
                                System.out.print("\u0007");
                                System.out.flush();
                                reader.getTerminal().writer().print("\u0007");
                                reader.getTerminal().writer().flush();
                            } catch (Exception e) {
                            }
                        } else if (tabCount >= 2) {
                            List<String> formattedMatches = new ArrayList<>();
                            for (String match : fileMatches) {
                                File matchFile = new File(currentDirectory, match);
                                String suffix = matchFile.isDirectory() ? "/" : "";
                                formattedMatches.add(matchFile.getName() + suffix);
                            }
                            java.util.Collections.sort(formattedMatches);
                            try {
                                reader.getTerminal().writer().println();
                                reader.getTerminal().writer().println(String.join("  ", formattedMatches));
                                reader.getTerminal().writer().flush();
                                reader.callWidget(LineReader.REDRAW_LINE);
                            } catch (Exception e) {
                            }
                        }
                    }
                }
            } else {
                // Command completion
                java.util.Set<String> matches = new java.util.LinkedHashSet<>();

                // Builtins
                String[] builtins = { "echo", "exit", "pwd", "cd", "type", "complete" };
                for (String b : builtins) {
                    if (b.startsWith(buf)) {
                        matches.add(b);
                    }
                }

                // Executables in PATH
                String pathEnv = System.getenv("PATH");
                if (pathEnv != null) {
                    String[] directories = pathEnv.split(File.pathSeparator);
                    boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
                    for (String dir : directories) {
                        File folder = new File(dir);
                        if (folder.exists() && folder.isDirectory()) {
                            File[] files = folder.listFiles();
                            if (files != null) {
                                for (File f : files) {
                                    if (f.isFile() && (isWindows || f.canExecute())) {
                                        String name = f.getName();
                                        if (name.startsWith(buf)) {
                                            matches.add(name);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (matches.size() == 0) {
                    try {
                        System.out.print("\u0007");
                        System.out.flush();
                        reader.getTerminal().writer().print("\u0007");
                        reader.getTerminal().writer().flush();
                    } catch (Exception e) {
                    }
                    return;
                }

                if (matches.size() == 1) {
                    String match = matches.iterator().next();
                    candidates.add(new Candidate(match + " ", match, null, null, null, null, false));
                } else {
                    // Multiple matches
                    String lcp = getLongestCommonPrefix(matches);
                    if (lcp.length() > buf.length()) {
                        for (String match : matches) {
                            candidates.add(new Candidate(match + " ", match, null, null, null, null, false));
                        }
                    } else {
                        if (buf.equals(lastBuffer) && line.cursor() == lastCursor) {
                            tabCount++;
                        } else {
                            tabCount = 1;
                            lastBuffer = buf;
                            lastCursor = line.cursor();
                        }

                        if (tabCount == 1) {
                            try {
                                System.out.print("\u0007");
                                System.out.flush();
                                reader.getTerminal().writer().print("\u0007");
                                reader.getTerminal().writer().flush();
                            } catch (Exception e) {
                            }
                        } else if (tabCount >= 2) {
                            List<String> sortedMatches = new ArrayList<>(matches);
                            java.util.Collections.sort(sortedMatches);
                            try {
                                reader.getTerminal().writer().println();
                                reader.getTerminal().writer().println(String.join("  ", sortedMatches));
                                reader.getTerminal().writer().flush();
                                reader.callWidget(LineReader.REDRAW_LINE);
                            } catch (Exception e) {
                            }
                        }
                    }
                }
            }
        };


        Terminal terminal = TerminalBuilder.builder().system(true).build();
        LineReader lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(builtinCompleter)
                .parser(parser)
                .option(LineReader.Option.AUTO_LIST, false)
                .option(LineReader.Option.AUTO_MENU, false)
                .build();
        lineReader.setVariable(LineReader.BELL_STYLE, "none");


        while (true) {
            String command;
            try {
                command = lineReader.readLine("$ ");
            } catch (org.jline.reader.EndOfFileException e) {
                break;
            } catch (org.jline.reader.UserInterruptException e) {
                continue;
            }
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
                            || target.equals("type")
                            || target.equals("complete")) {
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
            else if (cmd.equals("complete")) {
                // Placeholder for complete builtin behavior in later stages
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

    private static String getLongestCommonPrefix(java.util.Set<String> matches) {
        if (matches == null || matches.isEmpty()) {
            return "";
        }
        String[] arr = matches.toArray(new String[0]);
        String prefix = arr[0];
        for (int i = 1; i < arr.length; i++) {
            while (arr[i].indexOf(prefix) != 0) {
                prefix = prefix.substring(0, prefix.length() - 1);
                if (prefix.isEmpty()) {
                    return "";
                }
            }
        }
        return prefix;
    }
}



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
    static class Job {
        int id;
        long pid;
        String command;
        Process process;

        Job(int id, long pid, String command, Process process) {
            this.id = id;
            this.pid = pid;
            this.command = command;
            this.process = process;
        }
    }
    static java.util.List<Job> backgroundJobsList = new java.util.ArrayList<>();
    private static java.util.Map<String, String> commandCompletions = new java.util.HashMap<>();

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

            // Parse buf into words, ignoring duplicate spaces
            List<String> words = new ArrayList<>();
            StringBuilder currentWord = new StringBuilder();
            for (int i = 0; i < buf.length(); i++) {
                char c = buf.charAt(i);
                if (Character.isWhitespace(c)) {
                    if (currentWord.length() > 0) {
                        words.add(currentWord.toString());
                        currentWord.setLength(0);
                    }
                } else {
                    currentWord.append(c);
                }
            }
            String curWord = currentWord.toString();

            if (!words.isEmpty()) {
                String firstWord = words.get(0);
                if (commandCompletions.containsKey(firstWord)) {
                    String completerScript = commandCompletions.get(firstWord);
                    String cmdArg = firstWord;
                    String prevWord = words.get(words.size() - 1);
                    List<String> scriptMatches = new ArrayList<>();
                    try {
                        ProcessBuilder pb = new ProcessBuilder(completerScript, cmdArg, curWord, prevWord);
                        String compLine = line.line();
                        int compPoint = compLine.substring(0, line.cursor()).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                        pb.environment().put("COMP_LINE", compLine);
                        pb.environment().put("COMP_POINT", String.valueOf(compPoint));
                        pb.directory(currentDirectory);
                        Process process = pb.start();

                        try (java.io.BufferedReader readerProc = new java.io.BufferedReader(
                                new java.io.InputStreamReader(process.getInputStream()))) {
                            String lineProc;
                            while ((lineProc = readerProc.readLine()) != null) {
                                String trimmed = lineProc.trim();
                                if (!trimmed.isEmpty()) {
                                    scriptMatches.add(trimmed);
                                }
                            }
                        }
                        process.waitFor();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    List<String> filteredMatches = new ArrayList<>();
                    for (String m : scriptMatches) {
                        if (m.startsWith(curWord)) {
                            filteredMatches.add(m);
                        }
                    }

                    if (filteredMatches.size() == 0) {
                        try {
                            System.out.print("\u0007");
                            System.out.flush();
                            reader.getTerminal().writer().print("\u0007");
                            reader.getTerminal().writer().flush();
                        } catch (Exception e) {
                        }
                        return;
                    }

                    if (filteredMatches.size() == 1) {
                        String match = filteredMatches.get(0);
                        candidates.add(new Candidate(match + " ", match, null, null, null, null, false));
                    } else {
                        // Multiple matches
                        String lcp = getLongestCommonPrefix(new java.util.LinkedHashSet<>(filteredMatches));
                        if (lcp.length() > curWord.length()) {
                            for (String match : filteredMatches) {
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
                                java.util.Collections.sort(filteredMatches);
                                try {
                                    reader.getTerminal().writer().println();
                                    reader.getTerminal().writer().println(String.join("  ", filteredMatches));
                                    reader.getTerminal().writer().flush();
                                    reader.callWidget(LineReader.REDRAW_LINE);
                                } catch (Exception e) {
                                }
                            }
                        }
                    }
                    return;
                }
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
                String[] builtins = { "echo", "exit", "pwd", "cd", "type", "complete", "jobs" };
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
            reapJobs();
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

            boolean isPipeline = false;
            for (String part : parts) {
                if (part.equals("|")) {
                    isPipeline = true;
                    break;
                }
            }

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

                if (isPipeline) {
                    // Parse pipeline commands
                    List<List<String>> commandsList = new ArrayList<>();
                    List<String> currentCommand = new ArrayList<>();
                    for (String part : parts) {
                        if (part.equals("|")) {
                            if (!currentCommand.isEmpty()) {
                                commandsList.add(currentCommand);
                                currentCommand = new ArrayList<>();
                            }
                        } else {
                            currentCommand.add(part);
                        }
                    }
                    if (!currentCommand.isEmpty()) {
                        commandsList.add(currentCommand);
                    }

                    // Check background status from the last command
                    boolean isBackground = false;
                    List<String> lastCommandParts = commandsList.get(commandsList.size() - 1);
                    if (!lastCommandParts.isEmpty() && lastCommandParts.get(lastCommandParts.size() - 1).equals("&")) {
                        isBackground = true;
                        lastCommandParts.remove(lastCommandParts.size() - 1);
                    }

                    boolean hasBuiltin = false;
                    for (List<String> cmdParts : commandsList) {
                        if (!cmdParts.isEmpty() && isBuiltin(cmdParts.get(0))) {
                            hasBuiltin = true;
                            break;
                        }
                    }

                    if (hasBuiltin) {
                        // Sequential execution in a Runnable (either run immediately or in a Thread)
                        Runnable pipelineRunnable = () -> {
                            java.io.PrintStream threadOut = out;
                            java.io.PrintStream threadErr = errOut;

                            byte[] currentInput = new byte[0];
                            for (int i = 0; i < commandsList.size(); i++) {
                                List<String> cmdParts = commandsList.get(i);
                                if (cmdParts.isEmpty()) continue;

                                String cmdName = cmdParts.get(0);
                                String[] cmdArray = cmdParts.toArray(new String[0]);

                                java.io.ByteArrayOutputStream stageOut = new java.io.ByteArrayOutputStream();
                                java.io.PrintStream stagePrintStream = new java.io.PrintStream(stageOut);

                                java.io.PrintStream targetOut = (i == commandsList.size() - 1) ? threadOut : stagePrintStream;
                                java.io.PrintStream targetErr = threadErr;

                                if (isBuiltin(cmdName)) {
                                    runBuiltin(cmdArray, targetOut, targetErr);
                                    targetOut.flush();
                                    if (i < commandsList.size() - 1) {
                                        currentInput = stageOut.toByteArray();
                                    }
                                } else {
                                    File execFile = findExecutable(cmdName);
                                    if (execFile == null) {
                                        targetErr.println(cmdName + ": command not found");
                                        break;
                                    }

                                    List<String> commandList = new ArrayList<>();
                                    commandList.add(execFile.getName());
                                    for (int j = 1; j < cmdParts.size(); j++) {
                                        commandList.add(cmdParts.get(j));
                                    }

                                    ProcessBuilder pb = new ProcessBuilder(commandList);
                                    pb.directory(currentDirectory);

                                    String parentDir = execFile.getParent() != null ? execFile.getParent() : ".";
                                    pb.environment().merge("PATH", parentDir,
                                            (existing, prepend) -> prepend + File.pathSeparator + existing);

                                    pb.redirectInput(ProcessBuilder.Redirect.PIPE);

                                    if (i == commandsList.size() - 1) {
                                        if (outFile != null) {
                                            if (appendOutput) {
                                                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(outFile));
                                            } else {
                                                pb.redirectOutput(outFile);
                                            }
                                        } else {
                                            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                                        }
                                    } else {
                                        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
                                    }

                                    if (errFile != null) {
                                        if (appendError) {
                                            pb.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
                                        } else {
                                            pb.redirectError(errFile);
                                        }
                                    } else {
                                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                                    }

                                    try {
                                        Process process = pb.start();

                                        try (java.io.OutputStream os = process.getOutputStream()) {
                                            os.write(currentInput);
                                            os.flush();
                                        }

                                        byte[] nextInput = new byte[0];
                                        if (i < commandsList.size() - 1) {
                                            try (java.io.InputStream is = process.getInputStream()) {
                                                nextInput = is.readAllBytes();
                                            }
                                        }

                                        process.waitFor();

                                        if (i < commandsList.size() - 1) {
                                            currentInput = nextInput;
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        break;
                                    }
                                }
                            }

                            // Cleanup thread streams if they were files
                            if (threadOut != System.out) {
                                threadOut.close();
                            }
                            if (threadErr != System.out) {
                                threadErr.close();
                            }
                        };

                        if (isBackground) {
                            if (out != System.out) {
                                out = System.out;
                            }
                            if (errOut != System.out) {
                                errOut = System.out;
                            }

                            Thread thread = new Thread(pipelineRunnable);
                            thread.start();

                            int nextId = getSmallestAvailableJobId();
                            String cmdString = String.join(" ", parts);
                            long pid = ProcessHandle.current().pid();
                            backgroundJobsList.add(new Job(nextId, pid, cmdString, new ThreadProcess(thread, pid)));
                            System.out.println("[" + nextId + "] " + pid);
                        } else {
                            if (out != System.out) {
                                out.close();
                                out = System.out;
                            }
                            if (errOut != System.out) {
                                errOut.close();
                                errOut = System.out;
                            }

                            pipelineRunnable.run();
                        }
                    } else {
                        // Original ProcessBuilder.startPipeline execution for external-only pipelines
                        // Resolve executables for all commands in the pipeline
                        List<ProcessBuilder> builders = new ArrayList<>();
                        boolean allExecutableFound = true;
                        for (List<String> cmdParts : commandsList) {
                            if (cmdParts.isEmpty()) continue;
                            String pipelineCmd = cmdParts.get(0);
                            File execFile = findExecutable(pipelineCmd);
                            if (execFile == null) {
                                errOut.println(pipelineCmd + ": command not found");
                                allExecutableFound = false;
                                break;
                            }

                            List<String> commandList = new ArrayList<>();
                            commandList.add(execFile.getName());
                            for (int i = 1; i < cmdParts.size(); i++) {
                                commandList.add(cmdParts.get(i));
                            }

                            ProcessBuilder pb = new ProcessBuilder(commandList);
                            pb.directory(currentDirectory);
                            
                            // Prepend the executable's parent directory to PATH
                            String parentDir = execFile.getParent() != null ? execFile.getParent() : ".";
                            pb.environment().merge("PATH", parentDir,
                                    (existing, prepend) -> prepend + File.pathSeparator + existing);

                            builders.add(pb);
                        }

                        if (allExecutableFound && !builders.isEmpty()) {
                            // Close out and errOut if they are redirected files so subprocesses can write to them
                            if (out != System.out) {
                                out.close();
                                out = System.out;
                            }
                            if (errOut != System.out) {
                                errOut.close();
                                errOut = System.out;
                            }

                            // Configure redirection for all builders in the pipeline
                            for (int i = 0; i < builders.size(); i++) {
                                ProcessBuilder pb = builders.get(i);
                                
                                // Set error redirection
                                if (errFile != null) {
                                    if (appendError) {
                                        pb.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
                                    } else {
                                        pb.redirectError(errFile);
                                    }
                                } else {
                                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                                }

                                // Set input redirection for the first process
                                if (i == 0) {
                                    pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                                }

                                // Set output redirection for the last process
                                if (i == builders.size() - 1) {
                                    if (outFile != null) {
                                        if (appendOutput) {
                                            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(outFile));
                                        } else {
                                            pb.redirectOutput(outFile);
                                        }
                                    } else {
                                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                                    }
                                }
                            }

                            // Start the pipeline
                            List<Process> processes = ProcessBuilder.startPipeline(builders);

                            if (isBackground) {
                                int nextId = getSmallestAvailableJobId();
                                String cmdString = String.join(" ", parts);
                                Process lastProcess = processes.get(processes.size() - 1);
                                backgroundJobsList.add(new Job(nextId, lastProcess.pid(), cmdString, lastProcess));
                                System.out.println("[" + nextId + "] " + lastProcess.pid());
                            } else {
                                for (Process p : processes) {
                                    p.waitFor();
                                }
                            }
                        }
                    }
                } else if (runBuiltin(parts, out, errOut)) {
                    // Executed builtin
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

                        boolean isBackground = false;
                        if (commandList.size() > 0 && commandList.get(commandList.size() - 1).equals("&")) {
                            isBackground = true;
                            commandList.remove(commandList.size() - 1);
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
                        
                        if (isBackground) {
                            int nextId = getSmallestAvailableJobId();
                            String cmdString = String.join(" ", parts);
                            backgroundJobsList.add(new Job(nextId, process.pid(), cmdString, process));
                            System.out.println("[" + nextId + "] " + process.pid());
                        } else {
                            process.waitFor();
                        }

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

    static int getSmallestAvailableJobId() {
        int id = 1;
        while (true) {
            boolean found = false;
            for (Job job : backgroundJobsList) {
                if (job.id == id) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return id;
            }
            id++;
        }
    }

    static void reapJobs() {
        if (backgroundJobsList.isEmpty()) return;
        java.util.List<Job> toRemove = new java.util.ArrayList<>();
        int totalJobs = backgroundJobsList.size();
        for (int i = 0; i < totalJobs; i++) {
            Job job = backgroundJobsList.get(i);
            if (!job.process.isAlive()) {
                char marker = ' ';
                if (i == totalJobs - 1) {
                    marker = '+';
                } else if (i == totalJobs - 2) {
                    marker = '-';
                }
                String doneCmd = job.command;
                if (doneCmd.endsWith(" &")) {
                    doneCmd = doneCmd.substring(0, doneCmd.length() - 2);
                }
                System.out.printf("[%d]%c  %-24s%s\n", job.id, marker, "Done", doneCmd);
                toRemove.add(job);
            }
        }
        backgroundJobsList.removeAll(toRemove);
    }

    // Also used by the jobs builtin to print both Running and Done entries
    static void printAndReapJobs(java.io.PrintStream out) {
        java.util.List<Job> toRemove = new java.util.ArrayList<>();
        int totalJobs = backgroundJobsList.size();
        for (int i = 0; i < totalJobs; i++) {
            Job job = backgroundJobsList.get(i);
            char marker = ' ';
            if (i == totalJobs - 1) {
                marker = '+';
            } else if (i == totalJobs - 2) {
                marker = '-';
            }
            if (job.process.isAlive()) {
                out.printf("[%d]%c  %-24s%s\n", job.id, marker, "Running", job.command);
            } else {
                String doneCmd = job.command;
                if (doneCmd.endsWith(" &")) {
                    doneCmd = doneCmd.substring(0, doneCmd.length() - 2);
                }
                out.printf("[%d]%c  %-24s%s\n", job.id, marker, "Done", doneCmd);
                toRemove.add(job);
            }
        }
        backgroundJobsList.removeAll(toRemove);
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

    static boolean runBuiltin(String[] parts, java.io.PrintStream out, java.io.PrintStream errOut) {
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
            return true;
        }
        else if (cmd.equals("echo")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                if (i > 1)
                    sb.append(" ");
                sb.append(parts[i]);
            }
            out.println(sb);
            return true;
        }
        else if (cmd.equals("pwd")) {
            out.println(currentDirectory.getAbsolutePath());
            return true;
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
            return true;
        }
        else if (cmd.equals("type")) {
            if (parts.length < 2) {
                errOut.println("type: missing operand");
            } else {
                String target = parts[1];
                if (isBuiltin(target)) {
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
            return true;
        }
        else if (cmd.equals("complete")) {
            if (parts.length >= 4 && parts[1].equals("-C")) {
                String completerPath = parts[2];
                String targetCmd = parts[3];
                commandCompletions.put(targetCmd, completerPath);
            } else if (parts.length >= 3 && parts[1].equals("-p")) {
                String targetCmd = parts[2];
                if (commandCompletions.containsKey(targetCmd)) {
                    out.println("complete -C '" + commandCompletions.get(targetCmd) + "' " + targetCmd);
                } else {
                    out.println("complete: " + targetCmd + ": no completion specification");
                }
            } else if (parts.length >= 3 && parts[1].equals("-r")) {
                String targetCmd = parts[2];
                commandCompletions.remove(targetCmd);
            }
            return true;
        }
        else if (cmd.equals("jobs")) {
            printAndReapJobs(out);
            return true;
        }
        return false;
    }

    static class ThreadProcess extends Process {
        private final Thread thread;
        private final long pid;

        ThreadProcess(Thread thread, long pid) {
            this.thread = thread;
            this.pid = pid;
        }

        @Override
        public boolean isAlive() {
            return thread.isAlive();
        }

        @Override
        public long pid() {
            return pid;
        }

        @Override
        public java.io.OutputStream getOutputStream() { return null; }

        @Override
        public java.io.InputStream getInputStream() { return null; }

        @Override
        public java.io.InputStream getErrorStream() { return null; }

        @Override
        public int waitFor() throws InterruptedException {
            thread.join();
            return 0;
        }

        @Override
        public int exitValue() {
            if (thread.isAlive()) {
                throw new IllegalThreadStateException();
            }
            return 0;
        }

        @Override
        public void destroy() {
            thread.interrupt();
        }
    }

    static boolean isBuiltin(String cmd) {
        return cmd.equals("exit")
                || cmd.equals("echo")
                || cmd.equals("pwd")
                || cmd.equals("cd")
                || cmd.equals("type")
                || cmd.equals("complete")
                || cmd.equals("jobs");
    }
}



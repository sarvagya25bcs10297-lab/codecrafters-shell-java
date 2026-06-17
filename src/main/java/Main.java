import java.io.File;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        File currentDirectory = new File(System.getProperty("user.dir"));

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            if (!sc.hasNextLine()) {
                break;
            }

            String command = sc.nextLine();
            String trimmed = command.trim();

            if (trimmed.isEmpty()) {
                continue;
            }

            String[] parts = trimmed.split("\\s+");
            String cmd = parts[0];

            // exit
            if (cmd.equals("exit")) {
                int status = 0;
                if (parts.length > 1) {
                    try {
                        status = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        // Ignore invalid status
                    }
                }
                System.exit(status);
            }

            // echo
            else if (cmd.equals("echo")) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < parts.length; i++) {
                    sb.append(parts[i]);
                    if (i < parts.length - 1) {
                        sb.append(" ");
                    }
                }
                System.out.println(sb.toString());
            }

            // pwd
            else if (cmd.equals("pwd")) {
                System.out.println(currentDirectory.getAbsolutePath());
            }

            // cd
            else if (cmd.equals("cd")) {
    if (parts.length > 1) {
        File newDir;

        if (new File(parts[1]).isAbsolute()) {
            // Absolute path
            newDir = new File(parts[1]);
        } else {
            // Relative path (./, ../, dirname, etc.)
            newDir = new File(currentDirectory, parts[1]);
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

            // type
            else if (cmd.equals("type")) {
                if (parts.length < 2) {
                    System.out.println("type: missing operand");
                } else {
                    String target = parts[1];

                    if (target.equals("exit")
                            || target.equals("echo")
                            || target.equals("type")
                            || target.equals("pwd")
                            || target.equals("cd")) {

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

            // external command
            else {
                File file = findExecutable(cmd);

                if (file != null) {
                    try {
                        ProcessBuilder pb = new ProcessBuilder(parts);
                        pb.directory(currentDirectory);
                        pb.inheritIO();

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

    private static File findExecutable(String target) {
        if (target.contains("/") || target.contains("\\")) {
            File file = new File(target);

            if (file.exists() && file.isFile()) {
                boolean isWindows = System.getProperty("os.name")
                        .toLowerCase()
                        .contains("win");

                if (isWindows || file.canExecute()) {
                    return file;
                }
            }

            return null;
        }

        return findInPath(target);
    }

    private static File findInPath(String target) {
        String pathEnv = System.getenv("PATH");

        if (pathEnv == null) {
            return null;
        }

        String[] directories = pathEnv.split(File.pathSeparator);

        boolean isWindows = System.getProperty("os.name")
                .toLowerCase()
                .contains("win");

        String[] extensions = {""};

        if (isWindows) {
            extensions = new String[]{"", ".exe", ".bat", ".cmd", ".com"};
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
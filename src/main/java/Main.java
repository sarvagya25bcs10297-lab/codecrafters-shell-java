import java.io.File;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage
        Scanner sc = new Scanner(System.in);
        while (true){ 
        System.out.print("$ ");
        
        String command = sc.nextLine() ;

        if (command.equals("exit")){
            break ;
        }
        else if(command.startsWith("echo")){
            System.out.println(command.substring(5));
        }
        else if(command.startsWith("type ")){
            String target = command.substring(5).trim();
            if (target.equals("exit") || target.equals("echo") || target.equals("type")) {
                System.out.println(target + " is a shell builtin");
            } else {
                File file = findInPath(target);
                if (file != null) {
                    System.out.println(target + " is " + file.getAbsolutePath());
                } else {
                    System.out.println(target + ": not found");
                }
            }
        }
        else{
            String[] parts = command.trim().split("\\s+");
            if (parts.length > 0 && !parts[0].isEmpty()) {
                String cmd = parts[0];
                File file = findInPath(cmd);
                if (file != null) {
                    try {
                        parts[0] = file.getAbsolutePath();
                        ProcessBuilder pb = new ProcessBuilder(parts);
                        pb.inheritIO();
                        Process process = pb.start();
                        process.waitFor();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.println(command + ": command not found");
                }
            }
        }

}
}

    private static File findInPath(String target) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] directories = pathEnv.split(File.pathSeparator);
            for (String dir : directories) {
                File file = new File(dir, target);
                if (file.exists() && file.isFile() && file.canExecute()) {
                    return file;
                }
            }
        }
        return null;
    }
}

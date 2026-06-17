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
            System.out.println(command + ": command not found") ;
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

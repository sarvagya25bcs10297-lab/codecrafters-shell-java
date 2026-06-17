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
                System.out.println(target + ": not found");
            }
        }
        else{
            System.out.println(command + ": command not found") ;
        }

}
}
}

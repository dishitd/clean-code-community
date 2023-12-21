## Chapter 6 - Object and Data Structures

### Encapsulation and Data Hiding:
Demonstrated in Book and User classes through private variables and public getters and setters.

### Abstract Interfaces vs Implementation Exposure:
Illustrated in the Session class, which provides methods to start and end sessions
without exposing how these operations are implemented.

### Data/Object Anti-Symmetry: 
Book and User classes are objects that encapsulate data and expose behavior, contrasting with data structures that would expose data directly.

### The Law of Demeter: Followed in the Session class and the main method,
where interactions are with direct objects rather than through chains of method calls.

### Avoiding Hybrids: Not explicitly demonstrated in the code,
but the design avoids creating hybrid structures by having clear object roles and responsibilities.

### Data Transfer Objects (DTOs)
The UserDTO class is an example of a DTO with public variables for simple data transfer.

### Conclusion 

```
import java.util.Scanner;

public class Main {

    // 1. Encapsulation and Data Hiding
    public static class Book {
        private String title; // Private variables for encapsulation
        private String author;

        public Book(String title, String author) {
            this.title = title;
            this.author = author;
        }

        // Getters and setters for controlled access
        public String getTitle() {
            return title;
        }

        public String getAuthor() {
            return author;
        }
    }

    // 1. Encapsulation and Data Hiding
    public static class User {
        private String name; // Private variables for encapsulation
        private String email;

        public User(String name, String email) {
            this.name = name;
            this.email = email;
        }

        // Getters and setters for controlled access
        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }
    }

    // 2. Abstract Interfaces vs Implementation Exposure
    // 4. The Law of Demeter
    public static class Session {
        private User host; // Encapsulated data

        public Session(User host) {
            this.host = host;
        }

        // Public methods to start and end sessions, abstracting the implementation
        public void startSession() {
            // Session starting logic
        }

        public void endSession() {
            // Session ending logic
        }
    }

    // 6. Data Transfer Objects (DTOs)
    public static class UserDTO {
        public String name; // Public variables for data transfer
        public String email;

        public UserDTO(User user) {
            this.name = user.getName();
            this.email = user.getEmail();
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Creating objects based on user input
        System.out.println("Enter Book Title:");
        String bookTitle = scanner.nextLine();
        System.out.println("Enter Book Author:");
        String bookAuthor = scanner.nextLine();
        Book book = new Book(bookTitle, bookAuthor); // 3. Data/Object Anti-Symmetry

        System.out.println("Enter User Name:");
        String userName = scanner.nextLine();
        System.out.println("Enter User Email:");
        String userEmail = scanner.nextLine();
        User user = new User(userName, userEmail); // 3. Data/Object Anti-Symmetry

        Session session = new Session(user); // 4. The Law of Demeter

        // Displaying Entered Information
        System.out.println("Book Details:");
        System.out.println("Title: " + book.getTitle());
        System.out.println("Author: " + book.getAuthor());

        System.out.println("\nUser Details:");
        System.out.println("Name: " + user.getName());
        System.out.println("Email: " + user.getEmail());

        // Managing the session
        System.out.println("\nStarting the Session...");
        session.startSession();

        System.out.println("Ending the Session...");
        session.endSession();

        scanner.close();
    }
}

```
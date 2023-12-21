## Chapter 5 - Formatting

### Purpose of Formatting: 
Good formatting serves the practical purpose of making code readable. Since we read code much more often than we write it, it should be written for ease of reading.

### Vertical Formatting: 
Code files should be like a newspaper article with the most important concepts at the top and the details in subsequent lines. Functions and classes should be organized so related functions are vertically close.

### Horizontal Formatting: 
We should limit the length of lines of code to enhance readability. Robert C. Martin suggests a line should be 80-120 characters long. This helps in reading the code without the need to scroll horizontally.

### Vertical Openness and Density: 
Use blank lines thoughtfully to separate concepts; lines of code that are tightly related should appear close together without extra blank lines.

### Indentation: 
Indentation is crucial as it signifies the structure of the code, such as class declarations, methods, and control structures.

### Team Rules: 
The entire team should follow a consistent formatting style. This rule emphasizes that no individual’s preference should override the agreed-upon team standard.

### Uncle Bob's Formatting Rules: 
These are specific guidelines suggested by Robert C. Martin, like keeping lines short, using whitespace properly, and organizing code logically.

### Breaking Indentation Rules: 
Sometimes, breaking conventional indentation rules can make the code more readable. It's important to strike a balance between strict adherence to rules and practical readability.

### Alignment: 
Aligning similar lines of code can make it easier to identify patterns that might not be as obvious if the code is not aligned.

### Scope Lines: 
Use indentation to make scopes clear. This is naturally enforced in Python but should be carefully managed in languages that use braces.

### Conceptual Affinity: 
Code that is conceptually related should be placed near each other, enhancing the reader's ability to group related concepts mentally.

### File and Class Size: 
Classes and files should be kept small and to the point. This falls under the broader topic of file organization but relates to the ease of understanding a file's purpose.

```

import java.util.List;
import java.util.ArrayList;

// 1. Purpose of Formatting: Code is written for ease of reading.
// 2. Vertical Formatting: High-level concepts are at the top.
public class BookClub {

    private List<Member> members;
    private List<Session> sessions;

    // Constructor initializes the members and sessions lists.
    public BookClub() {
        // 4. Vertical Openness and Density: Logical separation and grouping.
        members = new ArrayList<>();
        sessions = new ArrayList<>();
    }

    // 11. Conceptual Affinity: Related methods are grouped together.

    // Add a new member to the book club
    public void addMember(Member member) {
        members.add(member);
    }

    // Schedule a new session
    public void scheduleSession(Session session) {
        sessions.add(session);
    }

    // 3. Horizontal Formatting: Lines of code are kept short for readability.
    // 10. Scope Lines: Indentation signifies the structure of the code.

    // Find the next session for the book club
    public Session getNextSession() {
        // Logic to get the next session would go here
        return sessions.isEmpty() ? null : sessions.get(0); // Simplified for example
    }

    // 12. File and Class Size: The class is kept small and to the point.
}

// 2. Vertical Formatting: Classes are kept logically organized and short.

class Member {
    private String name;
    private String email;

    // 5. Indentation: Proper indentation is used for constructor and methods.
    public Member(String name, String email) {
        this.name = name;
        this.email = email;
    }

    // Getters and setters would go here
}

class Session {
    private String bookTitle;
    private String date;

    // Constructor
    public Session(String bookTitle, String date) {
        this.bookTitle = bookTitle;
        this.date = date;
    }

    // Getters and setters would go here
}

// 6. Team Rules: The entire team should use this consistent formatting style.
// 7. Uncle Bob's Formatting Rules: Specific guidelines are followed.
// 8. Breaking Indentation Rules: Not needed as Java naturally enforces good indentation.
// 9. Alignment: Similar lines of code are aligned where it makes sense.

// The main class to run the book club application
public class Main {
    public static void main(String[] args) {
        BookClub bookClub = new BookClub();

        bookClub.addMember(new Member("John Doe", "john.doe@example.com"));
        bookClub.scheduleSession(new Session("Clean Code", "2023-12-15"));

        Session nextSession = bookClub.getNextSession();
        if (nextSession != null) {
            System.out.println("The next session is on: " + nextSession.getDate());
        }
    }
}

```
## Chapter 4 - Comments

## Key Takewaways
 - Use the code itself to express as much as possible. Rely less on comments.
 - Explain the why rather than what or how in the code
 
 ### Comments Cannot Compensate for Bad Code: 
 Relying on comments to make up for poorly written code is not a good practice. It's far better to write clear, self-explanatory code than to use comments to explain complicated logic.

### Code Should Explain Itself Where Possible**: 
Strive to write code that is self-explanatory. 
Use meaningful variable and function names, and structure your code in a way that makes its purpose and logic clear. This reduces the need for explanatory comments.

### Use Comments for Why, Not How: The code itself should tell you how something is done**. 
Use comments to explain why a particular approach was chosen or why a complex or non-obvious algorithm is necessary. This context can be invaluable, especially for future maintenance

### Keep Comments Relevant and Updated
Outdated or incorrect comments are worse than no comments at all. Ensure that comments are kept up to date with any changes made to the code.

### Avoid Commented-Out Code
In modern development environments with version control systems, commented-out code becomes unnecessary clutter. It's better to remove such code; you can always retrieve it from version history if needed.

### Use TODO Comments Sparingly
While TODO comments can be helpful for marking areas of the code that need further work, they should be used judiciously. Excessive TODOs can become noise and may be ignored over time.

### Avoid Redundant Comments:
Don't add comments that simply restate what is obvious from the code. Redundant comments do not add value and can clutter the codebase.

### Be Careful with Legal and Informative Comments
While necessary in some contexts (like headers for licensing information), ensure these comments are concise and only where they are needed

### Comments for Complex Logic:
When dealing with particularly complex algorithms or business logic, a well-placed comment can be very helpful in providing clarity and intent.

### Avoid Over-commenting
Excessive commenting can make code harder to read, obscuring the code’s own narrative. Striking the right balance is key.

```
public class BookClubSession {

    // List to store registered members
    private List<Member> members = new ArrayList<>();

    // 1. Avoiding bad code: The code is written to be clear without relying on comments to explain what it does.
    // 2. Self-explanatory code: Method names and variable names are chosen to be descriptive.
    public void addMember(Member member) {
        if (member != null && !isMemberRegistered(member)) {
            members.add(member);
        }
    }

    // 3. Explaining 'why': This method uses a comment to explain why the check is necessary, not how it's done.
    // Checks if a member is already registered to avoid duplicates.
    // Duplicate registrations could cause issues in session management.
    private boolean isMemberRegistered(Member member) {
        return members.contains(member);
    }

    // 4. Keeping comments updated: This method has no outdated comments.
    // 6. No redundant comments: Avoiding stating the obvious, like "this method removes a member".
    public void removeMember(Member member) {
        members.remove(member);
    }

    // 5. Avoid commented-out code: No old code is left commented out; it's either used or removed.
    public void clearAllMembers() {
        members.clear();  // Clearing the list of members
    }

    // 7. TODO comments: Used to indicate enhancements or changes needed.
    // TODO: Implement a notification system to alert members of session changes.
    public void notifyMembers(String message) {
        // Notification logic to be implemented
    }

    // 8. Legal or informative comments: 
    // This class is part of the BookDemy project. (C) 2023 BookDemy Inc. All rights reserved.
    // 9. Complex logic explanation: Not applicable as the class methods are straightforward.
    // 10. Avoid over-commenting: Minimal comments are used, focusing on clarity of the code itself.
}

class Member {
    private String name;

    // Constructor with descriptive variable name (2. Self-explanatory code)
    public Member(String name) {
        this.name = name;
    }

    // Getter method with a clear name (2. Self-explanatory code)
    public String getName() {
        return name;
    }
}

```
## Chapter 3 - Functions
Chapter 3 of "Clean Code," which focuses on Functions, is a critical section that provides valuable insights into writing
effective functions in programming. 

Here's a brief overview of the key takeaways from this chapter:

1. Small Size: Functions should be small. Robert C. Martin even suggests that functions should hardly ever be 20 lines long. But this is debatable.

2. Do One Thing: Functions should do only one thing and do it well.
If a function is doing more than one task, it's a sign that it could be broken down further. - Covered

3. One Level of Abstraction per Function: The statements within a function should all be at the same level of abstraction
to make them easier to read and understand. - Covered

3a. Stepdown Rule for reading code from top to bottom - a set of TO paragraphs

4. Descriptive Names: Functions should have names that clearly describe their purpose.
Longer descriptive names are better than short cryptic ones.

5. Function Arguments: The fewer arguments a function has, the better.
Zero arguments (niladic) is ideal, followed by one (monadic), two (dyadic), and so on. - Covered

6. No Side Effects: Functions should not have hidden effects. They should not modify any states outside their own scope,
which can lead to unexpected behavior. - Covered

7. Command-Query Separation: Functions should either change the state of an object (command) or
return information about an object (query), but not both. eg Query(Get) and Command(update)

8. Prefer Exceptions to Returning Error Codes: Returning error codes from command functions leads to cluttered code.
Using exceptions instead helps in separating error processing from the main logic. - Covered
Go through John Ousterhout's related lectures

9. DRY (Don’t Repeat Yourself): Avoid duplication in code. Duplication can lead to inconsistencies and
makes the code more difficult to maintain.

10. Structured Programming: Following the rules of structured programming, such as having one entry and
one exit point (return statement), can make functions more transparent and less error-prone.
package ro.mathlms.audit;

/** The groups the journal page filters by. */
public enum AuditCategory {
    /** Classes, books, chapters, exercises. */
    CONTENT,
    /** Quizzes and their questions. */
    QUIZ,
    /** Points, comments and finished grading on a student's paper. */
    GRADING,
    /** Homework: assigned, deadline moved, removed. */
    HOMEWORK,
    /** Accounts, class rosters, parent links and invite links. */
    PEOPLE
}

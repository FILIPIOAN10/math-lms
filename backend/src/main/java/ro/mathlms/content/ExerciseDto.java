package ro.mathlms.content;

/**
 * An exercise as the API returns it. {@code version} is echoed so the client can send it
 * back on update for the optimistic-lock check. {@code chapterId} is read off the LAZY proxy.
 */
public record ExerciseDto(
        Long id,
        Long chapterId,
        String statement,
        String solution,
        Difficulty difficulty,
        long version
) {
    public static ExerciseDto from(Exercise exercise) {
        return new ExerciseDto(
                exercise.getId(),
                exercise.getChapter().getId(),
                exercise.getStatement(),
                exercise.getSolution(),
                exercise.getDifficulty(),
                exercise.getVersion());
    }
}
